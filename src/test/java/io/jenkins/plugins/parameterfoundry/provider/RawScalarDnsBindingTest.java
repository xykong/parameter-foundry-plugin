package io.jenkins.plugins.parameterfoundry.provider;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class RawScalarDnsBindingTest {
    private ProviderRequest request(JenkinsRule j) throws Exception {
        return new ProviderRequest(
                j.createFreeStyleProject(),
                "raw",
                "digest",
                ProviderScope.GLOBAL,
                "jenkins",
                Map.of(),
                ProviderOperation.RESOLVE,
                Instant.now().plusSeconds(20),
                "builder");
    }

    private ConnectionProfile profile(String host, int port, boolean tls) {
        var profile = new ConnectionProfile("profile", (tls ? "https://" : "http://") + host + ":" + port + "/");
        profile.setUseJenkinsProxy(false);
        profile.setAllowPrivateAddresses(true);
        profile.setConnectTimeoutSeconds(2);
        profile.setReadTimeoutSeconds(1);
        profile.setTotalTimeoutSeconds(5);
        return profile;
    }

    @Test
    void actualConnectionUsesCheckedAddressAndPreservesHost(JenkinsRule j) throws Exception {
        var owned = HttpServer.create(new InetSocketAddress(OwnedHttpPeer.address(), 0), 0);
        int port = owned.getAddress().getPort();
        var forbidden = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        var goodRequests = new AtomicInteger();
        var badRequests = new AtomicInteger();
        var host = new AtomicReference<String>();
        owned.createContext("/", exchange -> {
            goodRequests.incrementAndGet();
            host.set(exchange.getRequestHeaders().getFirst("Host"));
            byte[] bytes = "owned-peer".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        forbidden.createContext("/", exchange -> {
            badRequests.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        owned.start();
        forbidden.start();
        try {
            var calls = new AtomicInteger();
            var policy = new NetworkPolicy(name -> List.of(
                    calls.incrementAndGet() == 1 ? OwnedHttpPeer.address() : InetAddress.getByName("127.0.0.1")));
            var p = profile("localhost", port, false);
            var result = assertInstanceOf(
                    RawScalarRestTransport.Body.class,
                    new RawScalarRestTransport(policy).get(p, request(j), p.buildUri("value", Map.of())));
            assertEquals("owned-peer", new String(result.bytes(), StandardCharsets.UTF_8));
            assertEquals(1, calls.get());
            assertEquals(1, goodRequests.get());
            assertEquals(0, badRequests.get());
            assertEquals("localhost:" + port, host.get());
        } finally {
            owned.stop(0);
            forbidden.stop(0);
        }
    }

    @Test
    void forbiddenFallbackAddressesCannotBecomeRequestsOrErrorChoices(JenkinsRule j) throws Exception {
        var forbidden = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var requests = new AtomicInteger();
        forbidden.createContext("/", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        forbidden.start();
        try {
            var p = profile("localhost", forbidden.getAddress().getPort(), false);
            for (String blocked : List.of("127.0.0.1", "169.254.169.254", "100.100.100.200", "168.63.129.16")) {
                var addresses = List.of(OwnedHttpPeer.address(), InetAddress.getByName(blocked));
                var transport = new RawScalarRestTransport(new NetworkPolicy(host -> addresses));
                var provider = new RestProviderDefinition("raw", "profile", "value", null, transport);
                provider.setResponseMode("active-choices-raw-scalar");
                provider.setRawFailureChoices(List.of(new RestRawFailureChoice("CONNECTION_REFUSED", "not-allowed")));
                var denied = assertThrows(
                        ProviderException.class,
                        () -> provider.load(new ProviderLoadContext(request(j), Optional.of(p))));
                assertTrue(denied.getMessage().contains("prohibited address"));
            }
            assertEquals(0, requests.get());
        } finally {
            forbidden.stop(0);
        }
    }

    @Test
    void publicSnapshotIsImmutableAndCannotResolveAnotherHost() throws Exception {
        var calls = new AtomicInteger();
        var publicIp = InetAddress.getByName("203.0.113.10");
        var mutable = new ArrayList<>(List.of(publicIp));
        var policy = new NetworkPolicy(host -> {
            if (calls.incrementAndGet() > 1) return List.of(InetAddress.getByName("127.0.0.1"));
            return mutable;
        });
        var uri = URI.create("https://public.example.invalid/");
        var checked = policy.resolveAllowed(uri, false);
        mutable.set(0, InetAddress.getByName("169.254.169.254"));
        var resolver = new ValidatedDnsResolver(uri, checked);
        for (int i = 0; i < 3; i++) {
            var values = resolver.resolve("public.example.invalid");
            assertArrayEquals(new InetAddress[] {publicIp}, values);
            values[0] = InetAddress.getByName("127.0.0.1");
        }
        assertEquals(1, calls.get());
        assertEquals("public.example.invalid", resolver.resolveCanonicalHostname("public.example.invalid"));
        assertThrows(UnknownHostException.class, () -> resolver.resolve("alternate.invalid"));
        assertThrows(UnknownHostException.class, () -> resolver.resolveCanonicalHostname("alternate.invalid"));
        var ipv6 = InetAddress.getByName("fd00::1");
        var ipv6Resolver = new ValidatedDnsResolver(URI.create("https://[fd00::1]/"), List.of(ipv6));
        assertArrayEquals(new InetAddress[] {ipv6}, ipv6Resolver.resolve("fd00::1"));
    }

    @Test
    void tlsClientHelloRetainsOriginalNameAfterAddressBinding(JenkinsRule j) throws Exception {
        try (var listener = new ServerSocket(0, 1, OwnedHttpPeer.address())) {
            listener.setSoTimeout(5000);
            var worker = Executors.newSingleThreadExecutor();
            try {
                var hello = worker.submit(() -> {
                    try (var socket = listener.accept()) {
                        socket.setSoTimeout(5000);
                        byte[] header = socket.getInputStream().readNBytes(5);
                        assertEquals(5, header.length);
                        assertEquals(22, Byte.toUnsignedInt(header[0]));
                        int length = Byte.toUnsignedInt(header[3]) * 256 + Byte.toUnsignedInt(header[4]);
                        byte[] bytes = socket.getInputStream().readNBytes(length);
                        assertEquals(length, bytes.length);
                        Thread.sleep(2500);
                        return new String(bytes, StandardCharsets.ISO_8859_1);
                    }
                });
                var p = profile("raw-peer.invalid", listener.getLocalPort(), true);
                var policy = new NetworkPolicy(host -> List.of(OwnedHttpPeer.address()));
                var failure = assertInstanceOf(
                        RawScalarRestTransport.Failure.class,
                        new RawScalarRestTransport(policy).get(p, request(j), p.buildUri("stall", Map.of())));
                assertEquals(RawScalarRestTransport.FailureKind.READ_TIMEOUT, failure.kind());
                assertTrue(hello.get(5, TimeUnit.SECONDS).contains("raw-peer.invalid"));
            } finally {
                worker.shutdownNow();
            }
        }
    }

    @Test
    void checkedOwnedAddressFallbackRetainsOriginalHost(JenkinsRule j) throws Exception {
        var addresses = OwnedHttpPeer.addresses();
        Assumptions.assumeTrue(
                addresses.size() >= 2, "This platform-specific fixture requires two owned private interfaces.");
        var server = HttpServer.create(new InetSocketAddress(addresses.get(1), 0), 0);
        int port = server.getAddress().getPort();
        // Verify the first owned address has no listener on this port before the fallback attempt.
        try (var closed = new ServerSocket(port, 1, addresses.get(0))) {}
        var host = new AtomicReference<String>();
        server.createContext("/", exchange -> {
            host.set(exchange.getRequestHeaders().getFirst("Host"));
            byte[] bytes = "second-owned-peer".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var calls = new AtomicInteger();
            var policy = new NetworkPolicy(name -> {
                calls.incrementAndGet();
                return addresses.subList(0, 2);
            });
            var p = profile("fallback.invalid", port, false);
            var response = assertInstanceOf(
                    RawScalarRestTransport.Body.class,
                    new RawScalarRestTransport(policy).get(p, request(j), p.buildUri("value", Map.of())));
            assertEquals("second-owned-peer", new String(response.bytes(), StandardCharsets.UTF_8));
            assertEquals("fallback.invalid:" + port, host.get());
            assertEquals(1, calls.get());
        } finally {
            server.stop(0);
        }
    }
}
