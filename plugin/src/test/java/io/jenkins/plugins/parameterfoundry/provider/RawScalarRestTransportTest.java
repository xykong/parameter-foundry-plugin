package io.jenkins.plugins.parameterfoundry.provider;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.CookieHandler;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class RawScalarRestTransportTest {
    @Test
    void stalledTlsHandshakeRetainsNativeReadTimeoutClassification(JenkinsRule j) throws Exception {
        try (var listener = new java.net.ServerSocket(0, 1, OwnedHttpPeer.address())) {
            listener.setSoTimeout(5000);
            var worker = Executors.newSingleThreadExecutor();
            var accepted = worker.submit(() -> {
                try (var socket = listener.accept()) {
                    socket.setSoTimeout(5000);
                    assertTrue(socket.getInputStream().read() >= 0);
                    Thread.sleep(2500);
                    return true;
                }
            });
            try {
                var profile = new ConnectionProfile(
                        "raw",
                        "https://" + OwnedHttpPeer.address().getHostAddress() + ":" + listener.getLocalPort() + "/");
                profile.setUseJenkinsProxy(false);
                profile.setAllowPrivateAddresses(true);
                profile.setReadTimeoutSeconds(1);
                profile.setConnectTimeoutSeconds(3);
                profile.setTotalTimeoutSeconds(5);
                var transport = new RawScalarRestTransport(new NetworkPolicy());
                var failure = assertInstanceOf(
                        RawScalarRestTransport.Failure.class,
                        transport.get(profile, request(j), profile.buildUri("stall", Map.of())));
                assertEquals(RawScalarRestTransport.FailureKind.READ_TIMEOUT, failure.kind());
                assertTrue(accepted.get(5, java.util.concurrent.TimeUnit.SECONDS));
            } finally {
                worker.shutdownNow();
            }
        }
    }

    @Test
    void completedBudgetCannotBeCancelledByLateTimer(JenkinsRule j) throws Exception {
        var request = request(j);
        var get = new org.apache.hc.client5.http.classic.methods.HttpGet("http://example.invalid/");
        var budget =
                new RawScalarRestTransport.Budget(System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5));
        budget.complete(new RawScalarRestTransport.Body(new byte[0]), request);
        budget.expire(get);
        assertFalse(get.isCancelled());
        var expired =
                new RawScalarRestTransport.Budget(System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5));
        expired.expire(get);
        assertTrue(get.isCancelled());
        assertThrows(
                ProviderException.class, () -> expired.complete(new RawScalarRestTransport.Body(new byte[0]), request));
    }

    @Test
    void onlyVerifiedFixedLengthEofCanPreservePartialBody() {
        var exact = new org.apache.hc.core5.http.ConnectionClosedException(
                "Premature end of Content-Length delimited message body (expected: 15; received: 5)");
        assertTrue(RawScalarRestTransport.isNativeFixedShortBody(exact, true, 15, 5));
        assertFalse(RawScalarRestTransport.isNativeFixedShortBody(exact, false, 15, 5));
        assertFalse(RawScalarRestTransport.isNativeFixedShortBody(exact, true, 16, 5));
        assertFalse(RawScalarRestTransport.isNativeFixedShortBody(exact, true, 15, 6));
        assertFalse(RawScalarRestTransport.isNativeFixedShortBody(
                new java.net.SocketTimeoutException(exact.getMessage()), true, 15, 5));
        assertFalse(RawScalarRestTransport.isNativeFixedShortBody(
                new org.apache.hc.core5.http.ConnectionClosedException("Other connection failure"), true, 15, 5));
    }

    @Test
    void authenticationCookiesAndTruncatedBodyMatchTheRestrictedContract(JenkinsRule j) throws Exception {
        var server = HttpServer.create(new InetSocketAddress(OwnedHttpPeer.address(), 0), 0);
        var leaked = new java.util.concurrent.atomic.AtomicBoolean();
        var callbacks = new AtomicInteger();
        server.createContext("/", exchange -> {
            if (exchange.getRequestHeaders().getFirst("Authorization") != null
                    || exchange.getRequestHeaders().getFirst("Cookie") != null) leaked.set(true);
            try {
                if (exchange.getRequestURI().getPath().equals("/auth")) {
                    exchange.getResponseHeaders().set("WWW-Authenticate", "Negotiate, Basic realm=fixture");
                    exchange.sendResponseHeaders(401, -1);
                } else {
                    exchange.sendResponseHeaders(200, 15);
                    exchange.getResponseBody().write("short".getBytes(StandardCharsets.UTF_8));
                    exchange.getResponseBody().flush();
                }
            } finally {
                exchange.close();
            }
        });
        server.start();
        var priorAuth = java.net.Authenticator.getDefault();
        var priorCookies = CookieHandler.getDefault();
        try {
            java.net.Authenticator.setDefault(new java.net.Authenticator() {
                @Override
                protected java.net.PasswordAuthentication getPasswordAuthentication() {
                    callbacks.incrementAndGet();
                    return null;
                }
            });
            CookieHandler.setDefault(new CookieHandler() {
                @Override
                public Map<String, List<String>> get(URI uri, Map<String, List<String>> headers) {
                    callbacks.incrementAndGet();
                    return Map.of("Cookie", List.of("synthetic-fixture"));
                }

                @Override
                public void put(URI uri, Map<String, List<String>> headers) {
                    callbacks.incrementAndGet();
                }
            });
            var profile = profile(server);
            var request = request(j);
            var transport = new RawScalarRestTransport(new NetworkPolicy());
            var auth = assertInstanceOf(
                    RawScalarRestTransport.Failure.class,
                    transport.get(profile, request, profile.buildUri("auth", Map.of())));
            assertEquals(401, auth.status());
            assertEquals(0, callbacks.get());
            assertFalse(leaked.get());
            var shortBody = assertInstanceOf(
                    RawScalarRestTransport.Body.class,
                    transport.get(profile, request, profile.buildUri("short", Map.of())));
            assertEquals("short", new String(shortBody.bytes(), StandardCharsets.UTF_8));
        } finally {
            java.net.Authenticator.setDefault(priorAuth);
            CookieHandler.setDefault(priorCookies);
            server.stop(0);
        }
    }

    @Test
    void truncatedChunkedWireResponseCannotBecomeSuccessfulPartialBody(JenkinsRule j) throws Exception {
        try (var listener = new java.net.ServerSocket(0, 1, OwnedHttpPeer.address())) {
            var worker = Executors.newSingleThreadExecutor();
            try {
                var served = worker.submit(() -> {
                    try (var socket = listener.accept()) {
                        socket.setSoTimeout(2000);
                        var reader = new java.io.BufferedReader(
                                new java.io.InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                        String line;
                        int lines = 0;
                        while ((line = reader.readLine()) != null && !line.isEmpty()) {
                            if (++lines > 32 || line.length() > 4096) throw new IOException("Fixture header bound");
                        }
                        socket.getOutputStream()
                                .write(
                                        "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n5\r\nshort\r\n"
                                                .getBytes(StandardCharsets.US_ASCII));
                        socket.getOutputStream().flush();
                    }
                    return true;
                });
                var profile = new ConnectionProfile(
                        "raw",
                        "http://" + OwnedHttpPeer.address().getHostAddress() + ":" + listener.getLocalPort() + "/");
                profile.setUseJenkinsProxy(false);
                profile.setAllowPrivateAddresses(true);
                var transport = new RawScalarRestTransport(new NetworkPolicy());
                var result = assertInstanceOf(
                        RawScalarRestTransport.Failure.class,
                        transport.get(profile, request(j), profile.buildUri("chunked", Map.of())));
                assertEquals(RawScalarRestTransport.FailureKind.PREMATURE_EOF, result.kind());
                assertTrue(served.get(3, java.util.concurrent.TimeUnit.SECONDS));
            } finally {
                worker.shutdownNow();
            }
        }
    }

    @Test
    void cancellationFailureCannotSkipRemainingCleanupOrExposeItsMessage(JenkinsRule j) throws Exception {
        var calls = new AtomicInteger();
        var primary = new ProviderException(
                io.jenkins.plugins.parameterfoundry.evaluation.ErrorCode.PROVIDER_UNAVAILABLE,
                "raw",
                "Primary rejection");
        RawScalarRestTransport.cleanup(
                request(j),
                primary,
                () -> {
                    throw new IllegalStateException("synthetic-sensitive-detail");
                },
                calls::incrementAndGet,
                () -> {
                    throw new AssertionError("synthetic-sensitive-detail");
                },
                calls::incrementAndGet);
        assertEquals(2, calls.get());
        assertEquals(2, primary.getSuppressed().length);
        for (Throwable suppressed : primary.getSuppressed())
            assertEquals("Raw scalar cleanup failed.", suppressed.getMessage());
        var noPrimary = assertThrows(
                ProviderException.class,
                () -> RawScalarRestTransport.cleanup(
                        request(j),
                        null,
                        () -> {
                            throw new IllegalStateException("synthetic-sensitive-detail");
                        },
                        calls::incrementAndGet));
        assertEquals("Raw scalar cleanup failed.", noPrimary.getMessage());
        assertEquals(3, calls.get());
    }

    private ProviderRequest request(JenkinsRule j) throws IOException {
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

    private ConnectionProfile profile(HttpServer server) {
        var profile = new ConnectionProfile(
                "raw",
                "http://" + OwnedHttpPeer.address().getHostAddress() + ":"
                        + server.getAddress().getPort() + "/");
        profile.setUseJenkinsProxy(false);
        profile.setAllowPrivateAddresses(true);
        profile.setReadTimeoutSeconds(1);
        profile.setTotalTimeoutSeconds(3);
        profile.setMaximumResponseBytes(1024);
        return profile;
    }

    @Test
    void preservesBodyAndSameUriRecoveryWithoutErrorBodyOrCredentialLeak(JenkinsRule j) throws Exception {
        var server = HttpServer.create(new InetSocketAddress(OwnedHttpPeer.address(), 0), 0);
        var requests = new AtomicInteger();
        var authorization = new java.util.concurrent.atomic.AtomicReference<String>();
        server.createContext("/", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            int ordinal = requests.incrementAndGet();
            byte[] bytes =
                    (ordinal == 1 ? "private-error-body" : "{\"data\":\"literal\"}\n").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=ISO-8859-1");
            exchange.sendResponseHeaders(ordinal == 1 ? 503 : 200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var profile = profile(server);
            var request = request(j);
            var transport = new RawScalarRestTransport(new NetworkPolicy());
            URI uri = profile.buildUri("same", Map.of());
            var failure = assertInstanceOf(RawScalarRestTransport.Failure.class, transport.get(profile, request, uri));
            assertEquals(RawScalarRestTransport.FailureKind.HTTP_STATUS, failure.kind());
            assertEquals(503, failure.status());
            var body = assertInstanceOf(RawScalarRestTransport.Body.class, transport.get(profile, request, uri));
            assertEquals("{\"data\":\"literal\"}\n", new String(body.bytes(), StandardCharsets.UTF_8));
            byte[] external = body.bytes();
            external[0] = 0;
            assertEquals('{', body.bytes()[0]);
            assertNull(authorization.get());
            assertEquals(2, requests.get());
            assertEquals(1, request.job().getNextBuildNumber());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsProfileCookieAndNetworkPoliciesBeforeRequest(JenkinsRule j) throws Exception {
        var server = HttpServer.create(new InetSocketAddress(OwnedHttpPeer.address(), 0), 0);
        var requests = new AtomicInteger();
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        CookieHandler prior = CookieHandler.getDefault();
        try {
            var profile = profile(server);
            var request = request(j);
            var transport = new RawScalarRestTransport(new NetworkPolicy());
            URI uri = profile.buildUri("same", Map.of());
            assertThrows(
                    ProviderException.class,
                    () -> transport.get(
                            profile,
                            request,
                            URI.create("http://synthetic@127.0.0.1:"
                                    + server.getAddress().getPort() + "/same")));
            assertThrows(
                    ProviderException.class,
                    () -> transport.get(profile, request, URI.create(uri.toString() + "#fragment")));
            profile.setCredentialId("fixture");
            assertThrows(ProviderException.class, () -> transport.get(profile, request, uri));
            profile.setCredentialId(null);
            profile.setUseJenkinsProxy(true);
            assertThrows(ProviderException.class, () -> transport.get(profile, request, uri));
            profile.setUseJenkinsProxy(false);
            profile.setHeaders(List.of(new ProfileHeader("X-Fixture", "value")));
            assertThrows(ProviderException.class, () -> transport.get(profile, request, uri));
            profile.setHeaders(List.of());
            profile.setAllowPrivateAddresses(false);
            var realNetworkPolicy = new RawScalarRestTransport(new NetworkPolicy());
            assertThrows(ProviderException.class, () -> realNetworkPolicy.get(profile, request, uri));
            assertEquals(0, requests.get());
        } finally {
            CookieHandler.setDefault(prior);
            server.stop(0);
        }
    }

    @Test
    void readTimeoutAndBudgetLimitsRemainDistinctFromSuccessfulResponses(JenkinsRule j) throws Exception {
        var server = HttpServer.create(new InetSocketAddress(OwnedHttpPeer.address(), 0), 0);
        var workers = Executors.newCachedThreadPool();
        server.setExecutor(workers);
        server.createContext("/", exchange -> {
            try {
                if (exchange.getRequestURI().getPath().equals("/size")) {
                    exchange.sendResponseHeaders(200, 2048);
                    exchange.getResponseBody().write(new byte[2048]);
                } else {
                    exchange.sendResponseHeaders(200, 30);
                    exchange.getResponseBody().write('x');
                    exchange.getResponseBody().flush();
                    if (exchange.getRequestURI().getPath().equals("/slow")) {
                        Thread.sleep(1400);
                    } else {
                        for (int index = 1; index < 30; index++) {
                            Thread.sleep(80);
                            exchange.getResponseBody().write('x');
                            exchange.getResponseBody().flush();
                        }
                    }
                }
            } catch (IOException ignored) {
                /* Client budget/timeout closes the owned fixture. */
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            var profile = profile(server);
            var request = request(j);
            var transport = new RawScalarRestTransport(new NetworkPolicy());
            var timeout = assertInstanceOf(
                    RawScalarRestTransport.Failure.class,
                    transport.get(profile, request, profile.buildUri("slow", Map.of())));
            assertEquals(RawScalarRestTransport.FailureKind.READ_TIMEOUT, timeout.kind());
            var size = assertThrows(
                    ProviderException.class, () -> transport.get(profile, request, profile.buildUri("size", Map.of())));
            assertTrue(size.getMessage().contains("size limit"));
            profile.setReadTimeoutSeconds(3);
            profile.setTotalTimeoutSeconds(1);
            var budget = assertThrows(
                    ProviderException.class,
                    () -> transport.get(profile, request, profile.buildUri("trickle", Map.of())));
            assertTrue(budget.getMessage().contains("total time budget"));
        } finally {
            server.stop(0);
            workers.shutdownNow();
        }
    }
}
