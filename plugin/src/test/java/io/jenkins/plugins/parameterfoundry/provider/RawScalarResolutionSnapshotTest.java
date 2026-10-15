package io.jenkins.plugins.parameterfoundry.provider;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class RawScalarResolutionSnapshotTest {
    private RestRawFailureChoice tls(String fingerprint) {
        var row = new RestRawFailureChoice("TLS_REJECTED", "reviewed-owned-literal");
        row.setFingerprint(fingerprint);
        return row;
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

    @Test
    void invalidPersistedTlsDeclarationsRejectBeforeDnsOrFallback(JenkinsRule j) throws Exception {
        var calls = new AtomicInteger();
        var provider = new RestProviderDefinition(
                "raw", "profile", "value", null, new RawScalarRestTransport(new NetworkPolicy(host -> {
                    calls.incrementAndGet();
                    throw new UnknownHostException();
                })));
        provider.setResponseMode("active-choices-raw-scalar");
        var profile = new ConnectionProfile("profile", "https://owned.invalid/");
        profile.setUseJenkinsProxy(false);
        var context = context(j, profile);
        var good = tls("a".repeat(64));
        String goodXml = j.jenkins.XSTREAM2.toXML(good);
        for (String xml : List.of(
                goodXml.replace("a".repeat(64), "A".repeat(64)),
                goodXml.replace("a".repeat(64), "*"),
                goodXml.replace("<status>0</status>", "<status>503</status>"),
                goodXml.replace("TLS_REJECTED", "READ_TIMEOUT"),
                goodXml.replace("reviewed-owned-literal", "{uri}"),
                goodXml.replace("<fingerprint>" + "a".repeat(64) + "</fingerprint>", ""))) {
            var malformed = (RestRawFailureChoice) j.jenkins.XSTREAM2.fromXML(xml);
            provider.setRawFailureChoices(List.of(malformed));
            assertThrows(ProviderException.class, () -> provider.load(context));
        }
        provider.setRawFailureChoices(List.of(good, good));
        assertThrows(ProviderException.class, () -> provider.load(context));
        assertEquals(0, calls.get());
        assertThrows(IllegalArgumentException.class, () -> good.setStatus(404));
        assertThrows(IllegalArgumentException.class, () -> good.setFingerprint("*"));
        assertThrows(IllegalArgumentException.class, () -> new RestRawFailureChoice("READ_TIMEOUT", "fixed")
                .setFingerprint("a".repeat(64)));
    }

    @Test
    void snapshotDigestAndFinalDeclarationsIgnoreLaterConfigurationMutations() {
        var row = tls("a".repeat(64));
        var query = new RestQueryParameter("method", "fixed");
        query.setDependencyParameter("before");
        var provider = new RestProviderDefinition("raw", "profile", "value");
        provider.setResponseMode("active-choices-raw-scalar");
        provider.setRawFailureChoices(List.of(row));
        provider.setQueryParameters(List.of(query));
        var copy = provider.snapshotForResolution();
        String digest = copy.configurationDigest();
        row.setFingerprint("b".repeat(64));
        query.setDependencyParameter("after");
        assertEquals(digest, copy.configurationDigest());
        assertNotEquals(digest, provider.snapshotForResolution().configurationDigest());
        assertSame(copy, copy.snapshotForResolution());
    }

    @Test
    void runtimeUsesSameRedirectPolicyAndQuerySnapshotAfterDigestBeforeAsync(JenkinsRule j) throws Exception {
        var calls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress(OwnedHttpPeer.address(), 0), 0);
        server.createContext("/value", exchange -> {
            calls.incrementAndGet();
            assertEquals("method=before", exchange.getRequestURI().getRawQuery());
            exchange.getResponseHeaders().add("Location", "/final?method=before");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        var finalCalls = new AtomicInteger();
        server.createContext("/final", exchange -> {
            finalCalls.incrementAndGet();
            assertEquals("method=before", exchange.getRequestURI().getRawQuery());
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.start();
        var worker = Executors.newSingleThreadExecutor();
        var enqueued = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var executor = new ThreadPoolExecutor(1, 1, 1, TimeUnit.SECONDS, new LinkedBlockingQueue<>()) {
            @Override
            public void execute(Runnable task) {
                enqueued.countDown();
                super.execute(() -> {
                    try {
                        assertTrue(release.await(10, TimeUnit.SECONDS));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(e);
                    }
                    task.run();
                });
            }
        };
        try {
            var profile = new ConnectionProfile(
                    "profile", "http://owned.invalid:" + server.getAddress().getPort() + "/");
            profile.setUseJenkinsProxy(false);
            profile.setAllowPrivateAddresses(true);
            profile.setAllowedQueryParameters(List.of("method"));
            profile.setFollowRedirects(true);
            String destination = profile.getBaseUrl() + "final?method=before";
            var provider = new RestProviderDefinition(
                    "raw",
                    "profile",
                    "value",
                    null,
                    new RawScalarRestTransport(new NetworkPolicy(host -> List.of(OwnedHttpPeer.address()))));
            provider.setResponseMode("active-choices-raw-scalar");
            provider.setRawRedirectTargets(List.of(new RestRawLiteral(destination)));
            provider.setCacheTtlSeconds(0);
            provider.setTimeoutSeconds(20);
            var query = new RestQueryParameter("method", "unused");
            query.setDependencyParameter("old");
            provider.setQueryParameters(List.of(query));
            var row = new RestRawFailureChoice("HTTP_STATUS", "reviewed-before");
            row.setStatus(503);
            row.setMatchUri(destination);
            var tls = tls("a".repeat(64));
            provider.setRawFailureChoices(List.of(row, tls));
            var configuration = ParameterFoundryGlobalConfiguration.get();
            configuration.setProviders(List.of(provider));
            configuration.setConnectionProfiles(List.of(profile));
            var runtime = new ProviderRuntime(executor, new ProviderRateLimiter());
            var job = j.createFreeStyleProject();
            var located = ProviderRegistry.findProvider(job, "raw").orElseThrow();
            var first = worker.submit(() -> runtime.resolve(
                    job, located, ProviderOperation.RESOLVE, Map.of("old", "before", "new", "after"), "builder"));
            assertTrue(enqueued.await(5, TimeUnit.SECONDS));
            row.setStatus(404);
            row.setMatchUri(profile.getBaseUrl() + "changed?method=after");
            provider.setRawRedirectTargets(List.of(new RestRawLiteral(profile.getBaseUrl() + "changed?method=after")));
            profile.setFollowRedirects(false);
            tls.setFingerprint("b".repeat(64));
            query.setDependencyParameter("new");
            profile.setAllowPrivateAddresses(false);
            profile.setAllowedQueryParameters(List.of());
            profile.setCredentialId("changed");
            profile.setUseJenkinsProxy(true);
            profile.setReadTimeoutSeconds(1);
            release.countDown();
            var resolution = first.get(10, TimeUnit.SECONDS);
            assertEquals("reviewed-before", resolution.options().get(0).getValue());
            assertEquals(1, calls.get());
            assertEquals(1, finalCalls.get());
            assertThrows(
                    ProviderException.class,
                    () -> runtime.resolve(
                            job,
                            located,
                            ProviderOperation.RESOLVE,
                            Map.of("old", "before", "new", "after"),
                            "builder"));
            assertEquals(1, calls.get());
            assertEquals(1, finalCalls.get());
            assertFalse(runtime.health("jenkins", "raw").orElseThrow().message().contains("bbbb"));
        } finally {
            release.countDown();
            executor.shutdownNow();
            worker.shutdownNow();
            server.stop(0);
        }
    }

    @Test
    void profileSnapshotDigestIncludesOperationalPolicy() {
        var profile = new ConnectionProfile("profile", "https://owned.invalid/");
        var frozen = profile.snapshotForResolution();
        String original = frozen.configurationDigest();
        profile.setUseJenkinsProxy(false);
        assertNotEquals(original, profile.configurationDigest());
        String previous = profile.configurationDigest();
        profile.setReadTimeoutSeconds(2);
        assertNotEquals(previous, profile.configurationDigest());
        previous = profile.configurationDigest();
        profile.setConnectTimeoutSeconds(2);
        assertNotEquals(previous, profile.configurationDigest());
        previous = profile.configurationDigest();
        profile.setTotalTimeoutSeconds(2);
        assertNotEquals(previous, profile.configurationDigest());
        previous = profile.configurationDigest();
        profile.setMaximumResponseBytes(1024);
        assertNotEquals(previous, profile.configurationDigest());
        assertEquals(original, frozen.configurationDigest());
    }

    @Test
    void tlsConfigurationRoundtripAndSnippetRetainFiniteDeclaration(JenkinsRule j) throws Exception {
        var provider = new RestProviderDefinition("raw", "profile", "value");
        provider.setResponseMode("active-choices-raw-scalar");
        provider.setRawFailureChoices(List.of(tls("a".repeat(64))));
        String digest = provider.configurationDigest();
        var configuration = ParameterFoundryGlobalConfiguration.get();
        configuration.setProviders(List.of(provider));
        j.configRoundtrip();
        var restored = (RestProviderDefinition) configuration.getProviders().get(0);
        assertEquals(digest, restored.configurationDigest());
        assertEquals("a".repeat(64), restored.getRawFailureChoices().get(0).getFingerprint());
        var model = new org.jenkinsci.plugins.structs.describable.DescribableModel<>(RestRawFailureChoice.class);
        var arguments =
                model.uninstantiate2(restored.getRawFailureChoices().get(0)).toMap();
        assertEquals("TLS_REJECTED", arguments.get("kind"));
        assertEquals("a".repeat(64), arguments.get("fingerprint"));
        assertEquals("reviewed-owned-literal", model.instantiate(arguments).getValue());
    }
}
