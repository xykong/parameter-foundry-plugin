package io.jenkins.plugins.parameterfoundry.provider;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterValue;
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class RawScalarStatusPolicyTest {
    private static final String NATIVE_BODY = "native-nonredirect-body";

    private ConnectionProfile profile(HttpServer server) {
        var profile = new ConnectionProfile(
                "profile",
                "http://" + OwnedHttpPeer.address().getHostAddress() + ":"
                        + server.getAddress().getPort() + "/");
        profile.setUseJenkinsProxy(false);
        profile.setAllowPrivateAddresses(true);
        return profile;
    }

    private RestProviderDefinition provider() {
        var provider = new RestProviderDefinition("raw", "profile", "value");
        provider.setResponseMode("active-choices-raw-scalar");
        provider.setCacheTtlSeconds(0);
        provider.setRawTrailingChoices(List.of(new RestRawLiteral("topic/stable")));
        provider.setRawFailureChoices(List.of(new RestRawFailureChoice("HTTP_STATUS", "reviewed-http-error")));
        return provider;
    }

    @Test
    void optInMatchesNativeBodiesAndCoreDefaultsWhileDefaultKeepsFailureMappings(JenkinsRule j) throws Exception {
        var status = new AtomicInteger(302);
        var body = new AtomicReference<>("topic/from-body\n");
        var requests = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress(OwnedHttpPeer.address(), 0), 0);
        server.createContext("/value", exchange -> {
            requests.incrementAndGet();
            byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
            // Native URLConnection ignores Location for these statuses. 302 has none.
            if (status.get() != 302) exchange.getResponseHeaders().set("Location", "/never-follow");
            exchange.sendResponseHeaders(status.get(), status.get() == 304 ? -1 : bytes.length);
            if (status.get() != 304) exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        var followed = new AtomicInteger();
        server.createContext("/never-follow", exchange -> {
            followed.incrementAndGet();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();
        try {
            var profile = profile(server);
            var provider = provider();
            var config = ParameterFoundryGlobalConfiguration.get();
            config.setProviders(List.of(provider));
            config.setConnectionProfiles(List.of(profile));
            var job = j.createFreeStyleProject();
            var choice = new FoundryChoiceParameterDefinition("REF");
            choice.setProviderId("raw");
            choice.setRenderer("select");
            choice.setDefaultMode("first");
            choice.setStandardApiInputMode("active-choices");
            choice.setActiveChoicesFilterMode("none");
            job.addProperty(new ParametersDefinitionProperty(choice));
            for (int code : List.of(302, 304, 306, 308)) {
                status.set(code);
                provider.setRawStatusPolicy("http-status");
                assertEquals(
                        "reviewed-http-error", choice.getDefaultParameterValue().getValue());
                var nativeConnection = (HttpURLConnection)
                        profile.buildUri("value", Map.of()).toURL().openConnection(java.net.Proxy.NO_PROXY);
                nativeConnection.setConnectTimeout(2000);
                nativeConnection.setReadTimeout(2000);
                String nativeValue;
                try (var input = nativeConnection.getInputStream()) {
                    nativeValue = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                } finally {
                    nativeConnection.disconnect();
                }
                assertEquals(code == 304 ? "" : body.get(), nativeValue);
                provider.setRawStatusPolicy(NATIVE_BODY);
                var resolution = ProviderRuntime.get()
                        .resolve(
                                job,
                                ProviderRegistry.findProvider(job, "raw").orElseThrow(),
                                ProviderOperation.RESOLVE,
                                Map.of(),
                                "builder");
                assertEquals(
                        List.of(nativeValue, "topic/stable"),
                        resolution.options().stream()
                                .map(option -> option.getSourceChoiceMetadata() == null
                                        ? option.getValue()
                                        : option.getSourceChoiceMetadata().apiValue())
                                .toList());
                var defaultValue = assertInstanceOf(StringParameterValue.class, choice.getDefaultParameterValue());
                assertEquals(nativeValue, defaultValue.getValue());
            }
            assertEquals(16, requests.get());
            assertEquals(0, followed.get());
            assertEquals(1, job.getNextBuildNumber());
            assertTrue(j.jenkins.getQueue().isEmpty());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void optInDoesNotFollowRedirectsOrChangeOtherHttpFailureDeclarations(JenkinsRule j) throws Exception {
        var status = new AtomicInteger(302);
        var requests = new AtomicInteger();
        var followed = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress(OwnedHttpPeer.address(), 0), 0);
        server.createContext("/value", exchange -> {
            requests.incrementAndGet();
            exchange.getResponseHeaders().set("Location", "/never-follow?method=write");
            byte[] bytes = "unreviewed-redirect-body".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.createContext("/never-follow", exchange -> {
            followed.incrementAndGet();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();
        try {
            var provider = provider();
            provider.setRawStatusPolicy(NATIVE_BODY);
            var profile = profile(server);
            var context = context(j, profile);
            for (int code : List.of(300, 301, 302, 303, 305, 307, 400, 404, 503)) {
                status.set(code);
                var exact = new RestRawFailureChoice("HTTP_STATUS", "reviewed-" + code);
                exact.setStatus(code);
                provider.setRawFailureChoices(List.of(new RestRawFailureChoice("HTTP_STATUS", "generic"), exact));
                assertEquals("reviewed-" + code, provider.load(context).get(0).getValue());
            }
            assertEquals(9, requests.get());
            assertEquals(0, followed.get());
            profile.setFollowRedirects(true);
            assertThrows(ProviderException.class, () -> provider.load(context));
            assertEquals(9, requests.get());
        } finally {
            server.stop(0);
        }
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
                        java.time.Instant.now().plusSeconds(20),
                        "builder"),
                java.util.Optional.of(profile));
    }

    @Test
    void policyIsFrozenAndIncludedOnlyInRawDigests() {
        var provider = provider();
        var copy = provider.snapshotForResolution();
        String digest = copy.configurationDigest();
        provider.setRawStatusPolicy(NATIVE_BODY);
        assertEquals("http-status", copy.getRawStatusPolicy());
        assertEquals(digest, copy.configurationDigest());
        assertNotEquals(digest, provider.snapshotForResolution().configurationDigest());
        provider.setResponseMode("mapped");
        String mapped = provider.configurationDigest();
        provider.setRawStatusPolicy("http-status");
        assertEquals(mapped, provider.configurationDigest());
        assertThrows(IllegalArgumentException.class, () -> provider.setRawStatusPolicy("all-redirects"));
    }

    @Test
    void formSnippetAndLegacyPersistencePreserveExplicitPolicy(JenkinsRule j) throws Exception {
        var provider = provider();
        provider.setRawStatusPolicy(NATIVE_BODY);
        var config = ParameterFoundryGlobalConfiguration.get();
        config.setProviders(List.of(provider));
        j.configRoundtrip();
        var restored = (RestProviderDefinition) config.getProviders().get(0);
        assertEquals(NATIVE_BODY, restored.getRawStatusPolicy());
        assertEquals(provider.configurationDigest(), restored.configurationDigest());
        var model = DescribableModel.of(RestProviderDefinition.class);
        var args = model.uninstantiate2(restored).getArguments();
        assertEquals(NATIVE_BODY, args.get("rawStatusPolicy"));
        assertEquals(NATIVE_BODY, model.instantiate(args).getRawStatusPolicy());
        String xml = j.jenkins.XSTREAM2.toXML(restored);
        var legacy = (RestProviderDefinition)
                j.jenkins.XSTREAM2.fromXML(xml.replace("<rawStatusPolicy>" + NATIVE_BODY + "</rawStatusPolicy>", ""));
        assertEquals("http-status", legacy.getRawStatusPolicy());
        var malformed = (RestProviderDefinition) j.jenkins.XSTREAM2.fromXML(xml.replace(NATIVE_BODY, "unknown-policy"));
        assertThrows(ProviderException.class, malformed::snapshotForResolution);
        var dns = new AtomicInteger();
        var rawClient = RestProviderDefinition.class.getDeclaredField("rawClient");
        rawClient.setAccessible(true);
        rawClient.set(malformed, new RawScalarRestTransport(new NetworkPolicy(host -> {
            dns.incrementAndGet();
            throw new java.net.UnknownHostException();
        })));
        var profile = new ConnectionProfile("profile", "https://owned.invalid/");
        profile.setUseJenkinsProxy(false);
        var context = context(j, profile);
        assertThrows(ProviderException.class, () -> malformed.load(context));
        assertEquals(0, dns.get());
    }

    @Test
    void optedInBodyStillHonorsTheResponseByteLimit(JenkinsRule j) throws Exception {
        var requests = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress(OwnedHttpPeer.address(), 0), 0);
        server.createContext("/value", exchange -> {
            requests.incrementAndGet();
            byte[] bytes = "x".repeat(1025).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(308, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var provider = provider();
            provider.setRawStatusPolicy(NATIVE_BODY);
            var profile = profile(server);
            profile.setMaximumResponseBytes(1024);
            var context = context(j, profile);
            assertThrows(ProviderException.class, () -> provider.load(context));
            assertEquals(1, requests.get());
        } finally {
            server.stop(0);
        }
    }
}
