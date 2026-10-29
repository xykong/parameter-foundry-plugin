package io.jenkins.plugins.parameterfoundry.provider;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class RawScalarRedirectTest {
    private static final String LIMIT_MESSAGE = Runtime.version().feature() == 17
            ? "Server redirected too many  times (20)"
            : "Server redirected too many times (20)";

    private static final class Peer implements AutoCloseable {
        final HttpServer server;
        final String origin;
        final AtomicReference<List<String>> locations = new AtomicReference<>(List.of());
        final AtomicInteger requests = new AtomicInteger();
        final AtomicInteger targets = new AtomicInteger();
        final AtomicInteger hops = new AtomicInteger(-1);
        final AtomicInteger terminalStatus = new AtomicInteger(200);
        final AtomicInteger redirectStatus = new AtomicInteger(302);
        volatile long delayMillis;
        volatile String twentiethLocation;

        Peer() throws IOException {
            server = HttpServer.create(new InetSocketAddress(OwnedHttpPeer.address(), 0), 0);
            origin = "http://" + OwnedHttpPeer.address().getHostAddress() + ":"
                    + server.getAddress().getPort();
            server.createContext("/", exchange -> {
                try {
                    int count = requests.incrementAndGet();
                    if (delayMillis > 0) Thread.sleep(delayMillis);
                    boolean redirect = hops.get() >= 0
                            ? count <= hops.get()
                            : exchange.getRequestURI().getPath().equals("/start");
                    if (redirect) {
                        for (String location : hops.get() >= 0
                                ? List.of(
                                        count == 20 && twentiethLocation != null
                                                ? twentiethLocation
                                                : "/hop-" + count + "?method=read")
                                : locations.get()) {
                            exchange.getResponseHeaders().add("Location", location);
                        }
                    } else {
                        targets.incrementAndGet();
                    }
                    byte[] body = (redirect
                                    ? "redirect-body"
                                    : "value:" + exchange.getRequestURI().toASCIIString())
                            .getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(redirect ? redirectStatus.get() : terminalStatus.get(), body.length);
                    exchange.getResponseBody().write(body);
                } catch (IOException cancelled) {
                    // Redirect bodies and expired terminal responses may be cancelled by the client.
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } finally {
                    exchange.close();
                }
            });
            server.start();
        }

        String uri(String path) {
            return origin + path;
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private ConnectionProfile profile(Peer peer) {
        var profile = new ConnectionProfile("profile", peer.origin + "/");
        profile.setUseJenkinsProxy(false);
        profile.setAllowPrivateAddresses(true);
        profile.setFollowRedirects(true);
        profile.setAllowedQueryParameters(List.of("method", "target"));
        return profile;
    }

    private RestProviderDefinition provider(List<String> targets) {
        var provider = new RestProviderDefinition("raw", "profile", "start");
        provider.setResponseMode("active-choices-raw-scalar");
        provider.setQueryParameters(List.of(new RestQueryParameter("method", "read")));
        provider.setRawRedirectTargets(targets.stream().map(RestRawLiteral::new).toList());
        provider.setRawTrailingChoices(List.of(new RestRawLiteral("topic/stable")));
        provider.setCacheTtlSeconds(0);
        return provider;
    }

    private ProviderLoadContext context(JenkinsRule j, ConnectionProfile profile) throws Exception {
        return new ProviderLoadContext(
                new ProviderRequest(
                        j.createFreeStyleProject(),
                        "raw",
                        "digest",
                        ProviderScope.GLOBAL,
                        "jenkins",
                        Map.of(),
                        ProviderOperation.RESOLVE,
                        Instant.now().plusSeconds(20),
                        "builder"),
                Optional.of(profile));
    }

    private String nativeValue(String target) throws Exception {
        var connection = (HttpURLConnection) URI.create(target).toURL().openConnection(java.net.Proxy.NO_PROXY);
        connection.setConnectTimeout(2000);
        connection.setReadTimeout(2000);
        try (var input = connection.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            return failure.getMessage();
        } finally {
            connection.disconnect();
        }
    }

    @Test
    void relativeAbsoluteQueryOnlyAndLastLocationMatchNative(JenkinsRule j) throws Exception {
        try (var peer = new Peer()) {
            for (int status : List.of(300, 301, 302, 303, 307)) {
                peer.redirectStatus.set(status);
                for (var locations : List.of(
                        List.of("/final?method=read"),
                        List.of(peer.uri("/final?method=read")),
                        List.of("?method=read&target=yes"),
                        List.of("/first?method=read", "/last?method=read"))) {
                    peer.locations.set(locations);
                    String expected = nativeValue(peer.uri("/start?method=read"));
                    String destination = locations.get(0).startsWith("?")
                            ? peer.uri("/?method=read&target=yes")
                            : locations.size() == 2 ? peer.uri("/last?method=read") : peer.uri("/final?method=read");
                    peer.requests.set(0);
                    peer.targets.set(0);
                    var options = provider(List.of(destination)).load(context(j, profile(peer)));
                    assertEquals(
                            expected, options.get(0).getSourceChoiceMetadata().apiValue());
                    assertEquals(2, peer.requests.get());
                    assertEquals(1, peer.targets.get());
                }
            }
        }
    }

    @Test
    void undeclaredQueryChangesAndInvalidDeclarationsNeverReachDestination(JenkinsRule j) throws Exception {
        try (var peer = new Peer()) {
            var profile = profile(peer);
            var provider = provider(List.of(peer.uri("/final?method=read")));
            for (String location : List.of(
                    "/final?method=write",
                    "/final",
                    "/other?method=read",
                    "http://other.invalid/final?method=read",
                    "/%2e%2e/final?method=read")) {
                peer.locations.set(List.of(location));
                peer.requests.set(0);
                peer.targets.set(0);
                var context = context(j, profile);
                assertThrows(ProviderException.class, () -> provider.load(context));
                assertEquals(1, peer.requests.get());
                assertEquals(0, peer.targets.get());
            }
            for (List<String> invalid : List.of(
                    List.of(peer.uri("/final?method=read"), peer.uri("/final?method=read")),
                    List.of("http://other.invalid/final"),
                    List.of(peer.uri("/final#fragment")),
                    List.of(peer.origin.replace("http://", "http://owned:fixture@") + "/final"))) {
                peer.requests.set(0);
                var malformed = provider(invalid);
                var context = context(j, profile);
                assertThrows(ProviderException.class, () -> malformed.load(context));
                assertEquals(0, peer.requests.get());
            }
        }
    }

    @Test
    void finalUriSelectorAndStatusPrecedenceDoNotReuseOriginalFailure(JenkinsRule j) throws Exception {
        try (var peer = new Peer()) {
            peer.locations.set(List.of("/final?method=read"));
            peer.terminalStatus.set(503);
            var provider = provider(List.of(peer.uri("/final?method=read")));
            var original = row("source-only", 0, peer.uri("/start?method=read"));
            var target = row("target-any", 0, peer.uri("/final?method=read"));
            var exact = row("shared-exact", 503, null);
            var exactTarget = row("target-exact", 503, peer.uri("/final?method=read"));
            var context = context(j, profile(peer));
            provider.setRawFailureChoices(List.of(original, target, exact, exactTarget));
            assertEquals("target-exact", provider.load(context).get(0).getValue());
            provider.setRawFailureChoices(List.of(original, target, exact));
            assertEquals("shared-exact", provider.load(context).get(0).getValue());
            provider.setRawFailureChoices(List.of(original, target));
            assertEquals("target-any", provider.load(context).get(0).getValue());
            provider.setRawFailureChoices(List.of(original));
            assertThrows(ProviderException.class, () -> provider.load(context));
            provider.setRawFailureChoices(List.of(target, target));
            int before = peer.requests.get();
            assertThrows(ProviderException.class, () -> provider.load(context));
            assertEquals(before, peer.requests.get());
        }
    }

    private RestRawFailureChoice row(String value, int status, String uri) {
        var row = new RestRawFailureChoice("HTTP_STATUS", value);
        row.setStatus(status);
        row.setMatchUri(uri);
        return row;
    }

    @Test
    void nineteenHopsSucceedAndTwentiethResponseStopsWithoutExtraRequest(JenkinsRule j) throws Exception {
        try (var peer = new Peer()) {
            var allowed = new ArrayList<String>();
            for (int i = 1; i <= 20; i++) allowed.add(peer.uri("/hop-" + i + "?method=read"));
            var provider = provider(allowed);
            provider.setRawFailureChoices(List.of(new RestRawFailureChoice("REDIRECT_LIMIT", LIMIT_MESSAGE)));
            for (int hops : List.of(19, 20)) {
                peer.hops.set(hops);
                peer.requests.set(0);
                String expected = nativeValue(peer.uri("/start?method=read"));
                assertEquals(20, peer.requests.get());
                peer.requests.set(0);
                var options = provider.load(context(j, profile(peer)));
                assertEquals(expected, options.get(0).getSourceChoiceMetadata().apiValue());
                assertEquals(20, peer.requests.get());
            }
            peer.hops.set(-1);
            peer.locations.set(List.of(""));
            peer.requests.set(0);
            provider.setRawRedirectTargets(List.of(new RestRawLiteral(peer.uri("/start?method=read"))));
            assertEquals(
                    LIMIT_MESSAGE,
                    provider.load(context(j, profile(peer))).get(0).getValue());
            assertEquals(20, peer.requests.get());
        }
    }

    @Test
    void hopsShareOneBudgetAndLateTimerCancelsCurrentRequest(JenkinsRule j) throws Exception {
        try (var peer = new Peer()) {
            peer.locations.set(List.of("/final?method=read"));
            peer.delayMillis = 650;
            var profile = profile(peer);
            profile.setTotalTimeoutSeconds(1);
            profile.setReadTimeoutSeconds(3);
            var provider = provider(List.of(peer.uri("/final?method=read")));
            var context = context(j, profile);
            var error = assertThrows(ProviderException.class, () -> provider.load(context));
            assertTrue(error.getMessage().contains("time budget"));
            assertEquals(2, peer.requests.get());
            peer.delayMillis = 0;
            var budget = new RawScalarRestTransport.Budget(System.nanoTime() + 5_000_000_000L);
            var old = new org.apache.hc.client5.http.classic.methods.HttpGet(peer.uri("/old"));
            var current = new org.apache.hc.client5.http.classic.methods.HttpGet(peer.uri("/current"));
            budget.activate(old, context.request());
            budget.deactivate(old);
            budget.activate(current, context.request());
            budget.expire(old);
            assertTrue(current.isCancelled());
            assertFalse(old.isCancelled());
            assertThrows(ProviderException.class, () -> budget.check(context.request()));
        }
    }

    @Test
    void legacyDirectEncodedUriStillWorksAndTargetQueryCannotLeakThroughUriToken(JenkinsRule j) throws Exception {
        try (var peer = new Peer()) {
            var profile = profile(peer);
            profile.setFollowRedirects(false);
            var context = context(j, profile);
            var result = new RawScalarRestTransport(new NetworkPolicy())
                    .get(profile, context.request(), URI.create(peer.uri("/safe%2Fname")));
            assertEquals(
                    "value:/safe%2Fname",
                    new String(
                            assertInstanceOf(RawScalarRestTransport.Body.class, result)
                                    .bytes(),
                            StandardCharsets.UTF_8));
            profile.setFollowRedirects(true);
            peer.requests.set(0);
            var provider = new RestProviderDefinition("raw", "profile", "start");
            provider.setResponseMode("active-choices-raw-scalar");
            provider.setRawRedirectTargets(List.of(new RestRawLiteral(peer.uri("/final?method=read"))));
            provider.setRawFailureChoices(List.of(new RestRawFailureChoice("HTTP_STATUS", "{uri}")));
            var redirected = context(j, profile);
            assertThrows(ProviderException.class, () -> provider.load(redirected));
            assertEquals(0, peer.requests.get());
        }
    }

    @Test
    void everyHopRechecksDnsAndNeverConnectsToReboundLoopback(JenkinsRule j) throws Exception {
        try (var peer = new Peer()) {
            peer.locations.set(List.of("/final?method=read"));
            String origin = "http://owned.invalid:" + peer.server.getAddress().getPort();
            var profile = new ConnectionProfile("profile", origin + "/");
            profile.setUseJenkinsProxy(false);
            profile.setAllowPrivateAddresses(true);
            profile.setFollowRedirects(true);
            profile.setAllowedQueryParameters(List.of("method"));
            var dns = new AtomicInteger();
            var transport = new RawScalarRestTransport(new NetworkPolicy(host -> List.of(
                    dns.incrementAndGet() == 1
                            ? OwnedHttpPeer.address()
                            : java.net.InetAddress.getByName("127.0.0.1"))));
            var provider = new RestProviderDefinition("raw", "profile", "start", null, transport);
            provider.setResponseMode("active-choices-raw-scalar");
            provider.setQueryParameters(List.of(new RestQueryParameter("method", "read")));
            provider.setRawRedirectTargets(List.of(new RestRawLiteral(origin + "/final?method=read")));
            var trapped = new AtomicInteger();
            var trap = HttpServer.create(
                    new InetSocketAddress("127.0.0.1", peer.server.getAddress().getPort()), 0);
            trap.createContext("/", exchange -> {
                trapped.incrementAndGet();
                exchange.sendResponseHeaders(500, -1);
                exchange.close();
            });
            trap.start();
            try {
                var context = context(j, profile);
                assertThrows(ProviderException.class, () -> provider.load(context));
                assertEquals(2, dns.get());
                assertEquals(1, peer.requests.get());
                assertEquals(0, peer.targets.get());
                assertEquals(0, trapped.get());
            } finally {
                trap.stop(0);
            }
        }
    }

    @Test
    void unresolvedFinalDnsUsesFinalSelectorAndFrozenPermissions(JenkinsRule j) throws Exception {
        try (var peer = new Peer()) {
            peer.locations.set(List.of("/final?method=read"));
            String origin = "http://owned.invalid:" + peer.server.getAddress().getPort();
            var profile = new ConnectionProfile("profile", origin + "/");
            profile.setUseJenkinsProxy(false);
            profile.setAllowPrivateAddresses(true);
            profile.setFollowRedirects(true);
            profile.setAllowedQueryParameters(List.of("method"));
            var dns = new AtomicInteger();
            var provider = new RestProviderDefinition(
                    "raw", "profile", "start", null, new RawScalarRestTransport(new NetworkPolicy(host -> {
                        if (dns.incrementAndGet() == 1) return List.of(OwnedHttpPeer.address());
                        throw new java.net.UnknownHostException("private-resolver-diagnostic");
                    })));
            provider.setResponseMode("active-choices-raw-scalar");
            provider.setQueryParameters(List.of(new RestQueryParameter("method", "read")));
            String target = origin + "/final?method=read";
            provider.setRawRedirectTargets(List.of(new RestRawLiteral(target)));
            var initial = new RestRawFailureChoice("DNS_UNRESOLVED", "initial-only");
            initial.setMatchUri(origin + "/start?method=read");
            var finalRow = new RestRawFailureChoice("DNS_UNRESOLVED", "final-owned-literal");
            finalRow.setMatchUri(target);
            provider.setRawFailureChoices(List.of(initial, finalRow));
            var frozen = provider.snapshotForResolution();
            String digest = frozen.configurationDigest();
            provider.setRawRedirectTargets(List.of(new RestRawLiteral(origin + "/changed?method=write")));
            finalRow.setMatchUri(origin + "/changed?method=write");
            assertEquals(digest, frozen.configurationDigest());
            assertNotEquals(digest, provider.snapshotForResolution().configurationDigest());
            var result = frozen.load(context(j, profile));
            assertEquals("final-owned-literal", result.get(0).getValue());
            assertEquals(2, dns.get());
            assertEquals(1, peer.requests.get());
            assertEquals(0, peer.targets.get());
        }
    }

    @Test
    void proxyAndUnsupportedProtocolNeverBecomeOutgoingRequests(JenkinsRule j) throws Exception {
        try (var peer = new Peer()) {
            var provider = provider(List.of(peer.uri("/final?method=read")));
            var profile = profile(peer);
            for (int code : List.of(302, 305)) {
                peer.redirectStatus.set(code);
                for (String location : List.of(
                        "file:///owned-never-read",
                        "ftp://owned.invalid/value",
                        "custom-owned:never-invoke-handler",
                        "/final?method=read")) {
                    if (code == 302 && location.startsWith("/")) continue;
                    peer.locations.set(List.of(location));
                    peer.requests.set(0);
                    peer.targets.set(0);
                    var context = context(j, profile);
                    assertThrows(ProviderException.class, () -> provider.load(context));
                    assertEquals(1, peer.requests.get());
                    assertEquals(0, peer.targets.get());
                }
            }
        }
    }

    @Test
    void persistedPermissionsAndSelectorsAreRevalidatedBeforeAnyRequest(JenkinsRule j) throws Exception {
        try (var peer = new Peer()) {
            peer.locations.set(List.of("/final?method=read"));
            String destination = peer.uri("/final?method=read");
            var original = provider(List.of(destination));
            var selector = row("owned-final", 503, destination);
            original.setRawFailureChoices(List.of(selector));
            String xml = j.jenkins.XSTREAM2.toXML(original);
            var restored = (RestProviderDefinition) j.jenkins.XSTREAM2.fromXML(xml);
            assertEquals(original.configurationDigest(), restored.configurationDigest());
            assertEquals(destination, restored.getRawRedirectTargets().get(0).getValue());
            assertEquals(destination, restored.getRawFailureChoices().get(0).getMatchUri());
            assertEquals(
                    "value:/final?method=read",
                    restored.load(context(j, profile(peer)))
                            .get(0)
                            .getSourceChoiceMetadata()
                            .apiValue());
            for (String corrupt : List.of(
                    xml.replace("<value>" + destination + "</value>", "<value>file:///never-read</value>"),
                    xml.replace(
                            "<matchUri>" + destination + "</matchUri>", "<matchUri>file:///never-read</matchUri>"))) {
                assertNotEquals(xml, corrupt);
                peer.requests.set(0);
                var invalid = (RestProviderDefinition) j.jenkins.XSTREAM2.fromXML(corrupt);
                var context = context(j, profile(peer));
                assertThrows(ProviderException.class, () -> invalid.load(context));
                assertEquals(0, peer.requests.get());
            }
            for (String value : List.of("", "{uri}", "{status}", "{unexpected}")) {
                original.setRawFailureChoices(List.of(new RestRawFailureChoice("REDIRECT_LIMIT", value)));
                peer.requests.set(0);
                var context = context(j, profile(peer));
                assertThrows(ProviderException.class, () -> original.load(context));
                assertEquals(0, peer.requests.get());
            }
        }
    }

    @Test
    void twentiethRedirectCannotTurnUndeclaredDestinationIntoLimitChoice(JenkinsRule j) throws Exception {
        try (var peer = new Peer()) {
            peer.hops.set(20);
            var targets = new ArrayList<String>();
            for (int i = 1; i <= 20; i++) targets.add(peer.uri("/hop-" + i + "?method=read"));
            var provider = provider(targets);
            provider.setRawFailureChoices(List.of(new RestRawFailureChoice("REDIRECT_LIMIT", "limit-choice")));
            for (String denied : List.of(
                    "/offlist?method=read", "/hop-20?method=write", "http://other.invalid/target?method=read")) {
                peer.twentiethLocation = denied;
                peer.requests.set(0);
                peer.targets.set(0);
                var context = context(j, profile(peer));
                assertThrows(ProviderException.class, () -> provider.load(context));
                assertEquals(20, peer.requests.get());
                assertEquals(0, peer.targets.get());
            }
        }
    }

    @Test
    void globalFormAndSnippetRoundtripRetainDestinationsAndFinalSelector(JenkinsRule j) throws Exception {
        try (var peer = new Peer()) {
            String destination = peer.uri("/final?method=read");
            var provider = provider(List.of(destination));
            provider.setRawFailureChoices(List.of(row("final-unavailable", 503, destination)));
            var configuration = ParameterFoundryGlobalConfiguration.get();
            configuration.setProviders(List.of(provider));
            configuration.setConnectionProfiles(List.of(profile(peer)));
            String digest = provider.configurationDigest();
            j.configRoundtrip();
            var restored = (RestProviderDefinition) configuration.getProviders().get(0);
            assertEquals(digest, restored.configurationDigest());
            assertEquals(destination, restored.getRawRedirectTargets().get(0).getValue());
            assertEquals(destination, restored.getRawFailureChoices().get(0).getMatchUri());
            var providerModel =
                    new org.jenkinsci.plugins.structs.describable.DescribableModel<>(RestProviderDefinition.class);
            var arguments = providerModel.uninstantiate2(restored).toMap();
            var snippet = providerModel.instantiate(arguments);
            assertEquals(digest, snippet.configurationDigest());
            assertEquals(destination, snippet.getRawRedirectTargets().get(0).getValue());
            assertEquals(destination, snippet.getRawFailureChoices().get(0).getMatchUri());
            peer.locations.set(List.of("/final?method=read"));
            peer.terminalStatus.set(503);
            assertEquals(
                    "final-unavailable",
                    snippet.load(context(
                                    j, configuration.getConnectionProfiles().get(0)))
                            .get(0)
                            .getValue());
            assertEquals(2, peer.requests.get());
        }
    }
}
