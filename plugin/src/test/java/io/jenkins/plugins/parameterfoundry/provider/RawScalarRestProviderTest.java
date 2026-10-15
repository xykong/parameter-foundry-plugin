package io.jenkins.plugins.parameterfoundry.provider;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterValue;
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition;
import io.jenkins.plugins.parameterfoundry.model.Option;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.sf.json.JSONObject;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class RawScalarRestProviderTest {
    private RestProviderDefinition provider() throws Exception {
        var transport = new RawScalarRestTransport(new NetworkPolicy());
        var provider = new RestProviderDefinition("raw", "profile", "value", null, transport);
        provider.setResponseMode("active-choices-raw-scalar");
        provider.setCacheTtlSeconds(0);
        provider.setTimeoutSeconds(20);
        provider.setRawIgnoredResponses(List.of(new RestRawLiteral("null")));
        provider.setRawTrailingChoices(List.of(new RestRawLiteral("topic/stable")));
        return provider;
    }

    private ConnectionProfile profile(HttpServer server) {
        var profile = new ConnectionProfile(
                "profile",
                "http://" + OwnedHttpPeer.address().getHostAddress() + ":"
                        + server.getAddress().getPort() + "/api/");
        profile.setAllowedPathPrefixes(List.of("/api/"));
        profile.setAllowPrivateAddresses(true);
        profile.setUseJenkinsProxy(false);
        return profile;
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
    void wholeBodySentinelsAndSourceMetadataFlowThroughTheProvider(JenkinsRule j) throws Exception {
        var body = new AtomicReference<>("topic/test");
        var server = HttpServer.create(new InetSocketAddress(OwnedHttpPeer.address(), 0), 0);
        server.createContext("/api/value", exchange -> {
            byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=ISO-8859-1");
            exchange.sendResponseHeaders(200, bytes.length == 0 ? -1 : bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var provider = provider();
            for (String value :
                    List.of("topic/test\n", "  topic/test  ", "line1\nline2", "{\"data\":1}", "", "null\n")) {
                body.set(value);
                var options = provider.load(context(j, profile(server)));
                assertEquals(2, options.size());
                assertEquals(value, options.get(0).getSourceChoiceMetadata().apiValue());
                assertEquals("topic/stable", options.get(1).getValue());
            }
            body.set("null");
            assertEquals(
                    List.of("topic/stable"),
                    provider.load(context(j, profile(server))).stream()
                            .map(Option::getValue)
                            .toList());
            body.set("topic/stable");
            assertEquals(1, provider.load(context(j, profile(server))).size());
            body.set("topic/test:disabled");
            assertTrue(provider.load(context(j, profile(server))).get(0).isDisabled());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void classifiedErrorChoiceIsExplicitAndExactStatusOverridesGeneric(JenkinsRule j) throws Exception {
        var status = new AtomicInteger(503);
        var requests = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress(OwnedHttpPeer.address(), 0), 0);
        server.createContext("/api/value", exchange -> {
            requests.incrementAndGet();
            byte[] secretError = "not-a-choice".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), secretError.length);
            exchange.getResponseBody().write(secretError);
            exchange.close();
        });
        server.start();
        try {
            var provider = provider();
            assertThrows(ProviderException.class, () -> provider.load(context(j, profile(server))));
            var generic = new RestRawFailureChoice("HTTP_STATUS", "HTTP {status}: {uri}");
            var exact = new RestRawFailureChoice("HTTP_STATUS", "reviewed-503");
            exact.setStatus(503);
            provider.setRawFailureChoices(List.of(generic, exact));
            assertEquals(
                    "reviewed-503",
                    provider.load(context(j, profile(server))).get(0).getValue());
            status.set(404);
            var options = provider.load(context(j, profile(server)));
            assertEquals(
                    "HTTP 404: " + profile(server).getBaseUrl() + "value",
                    options.get(0).getValue());
            assertEquals("topic/stable", options.get(1).getValue());
            assertFalse(options.stream().anyMatch(option -> option.getValue().contains("not-a-choice")));
            assertEquals(3, requests.get());
            var forbidden = profile(server);
            forbidden.setHeaders(List.of(new ProfileHeader("X-Private", "not-for-display")));
            var denied = assertThrows(ProviderException.class, () -> provider.load(context(j, forbidden)));
            assertTrue(denied.getMessage().contains("credential-free"));
            assertEquals(3, requests.get(), "A profile refusal must not make an HTTP request or become a choice.");
            var invalid = (RestRawLiteral) jenkins.model.Jenkins.XSTREAM2.fromXML(
                    "<io.jenkins.plugins.parameterfoundry.provider.RestRawLiteral/>");
            provider.setRawTrailingChoices(List.of(invalid));
            assertTrue(assertThrows(ProviderException.class, () -> provider.load(context(j, profile(server))))
                    .getMessage()
                    .contains("literal rows"));
            assertThrows(ProviderException.class, provider::configurationDigest);
            assertEquals(3, requests.get(), "A malformed persisted literal must be refused before networking.");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void uncachedRuntimeFallbackRecoversAndNativeApiDefaultUsesWholeResponse(JenkinsRule j) throws Exception {
        var status = new AtomicInteger(503);
        var requests = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress(OwnedHttpPeer.address(), 0), 0);
        server.createContext("/api/value", exchange -> {
            requests.incrementAndGet();
            byte[] bytes = "topic/recovered\n".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var provider = provider();
            provider.setFallbackOptions(List.of(new Option("reviewed/fallback")));
            ParameterFoundryGlobalConfiguration.get().setConnectionProfiles(List.of(profile(server)));
            ParameterFoundryGlobalConfiguration.get().setProviders(List.of(provider));
            var definition = new FoundryChoiceParameterDefinition("REF");
            definition.setProviderId("raw");
            definition.setRenderer("select");
            definition.setDefaultMode("first");
            definition.setStandardApiInputMode("active-choices");
            var job = j.createFreeStyleProject();
            job.addProperty(new ParametersDefinitionProperty(definition));
            assertEquals(
                    "reviewed/fallback",
                    assertInstanceOf(StringParameterValue.class, definition.getDefaultParameterValue())
                            .getValue());
            status.set(200);
            assertEquals(
                    "topic/recovered\n",
                    assertInstanceOf(StringParameterValue.class, definition.getDefaultParameterValue())
                            .getValue());
            assertEquals(2, requests.get(), "An uncached source must not reuse the previous failure for five seconds.");
            var apiRequest = new WebRequest(
                    new URL(j.getURL(), job.getUrl() + "parameter-foundry/api/v1/resolve"), HttpMethod.POST);
            apiRequest.setAdditionalHeader("Content-Type", "application/json");
            apiRequest.setRequestBody("{\"parameter\":\"REF\",\"version\":1}");
            var issuer = j.jenkins.getCrumbIssuer();
            apiRequest.setAdditionalHeader(issuer.getCrumbRequestField(), issuer.getCrumb());
            JSONObject resolved =
                    new JenkinsRule.JSONWebResponse(j.createWebClient().loadWebResponse(apiRequest)).getJSONObject();
            assertTrue(resolved.getBoolean("ok"), resolved.toString());
            assertEquals("topic/recovered\n", resolved.getString("default"));
            assertEquals(
                    "topic/recovered\n",
                    resolved.getJSONArray("options").getJSONObject(0).getString("value"));
            assertNull(job.getLastBuild());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void policyAndInvalidMappingsCannotBecomeExceptionChoices(JenkinsRule j) throws Exception {
        var provider = provider();
        var profile = new ConnectionProfile("profile", "https://example.invalid/api/");
        profile.setUseJenkinsProxy(false);
        var duplicate = new RestRawFailureChoice("READ_TIMEOUT", "fixed");
        provider.setRawFailureChoices(List.of(duplicate, duplicate));
        assertTrue(assertThrows(ProviderException.class, () -> provider.load(context(j, profile)))
                .getMessage()
                .contains("invalid or duplicated"));
        provider.setRawFailureChoices(List.of(new RestRawFailureChoice("HTTP_STATUS", "{uri}")));
        profile.setAllowedQueryParameters(List.of("token"));
        provider.setQueryParameters(List.of(new RestQueryParameter("token", "not-for-display")));
        assertTrue(assertThrows(ProviderException.class, () -> provider.load(context(j, profile)))
                .getMessage()
                .contains("query-free"));
        provider.setQueryParameters(List.of());
        provider.setRawFailureChoices(List.of());
        provider.setFormat("json");
        assertTrue(assertThrows(ProviderException.class, () -> provider.load(context(j, profile)))
                .getMessage()
                .contains("automatic format"));
        assertThrows(IllegalArgumentException.class, () -> new RestRawFailureChoice("anything", "value"));
        assertThrows(IllegalArgumentException.class, () -> duplicate.setStatus(503));
        // The production constructor has no injectable network policy and must still reject loopback.
        var actual = new RestProviderDefinition("actual", "profile", "value");
        actual.setResponseMode("active-choices-raw-scalar");
        actual.setRawFailureChoices(List.of(new RestRawFailureChoice("CONNECTION_REFUSED", "fixed-refusal")));
        var loopback = new ConnectionProfile("profile", "http://127.0.0.1:1/api/");
        loopback.setAllowPrivateAddresses(true);
        loopback.setUseJenkinsProxy(false);
        assertTrue(assertThrows(ProviderException.class, () -> actual.load(context(j, loopback)))
                .getMessage()
                .contains("prohibited address"));
    }

    @Test
    void configurationRoundTripPreservesLiteralsMappingsAndDigest(JenkinsRule j) throws Exception {
        var provider = new RestProviderDefinition("raw", "profile", "value");
        String structured = provider.configurationDigest();
        provider.setRawIgnoredResponses(
                List.of(new RestRawLiteral("  null\n"), new RestRawLiteral("\nleading"), new RestRawLiteral("")));
        provider.setRawTrailingChoices(List.of(new RestRawLiteral("topic/stable")));
        assertEquals(
                structured,
                provider.configurationDigest(),
                "Inactive raw fields must not invalidate existing mapped Providers.");
        provider.setResponseMode("active-choices-raw-scalar");
        var mapped = new RestRawFailureChoice("HTTP_STATUS", "HTTP {status}: {uri}");
        mapped.setStatus(503);
        provider.setRawFailureChoices(List.of(mapped));
        String digest = provider.configurationDigest();
        assertNotEquals(structured, digest);
        ParameterFoundryGlobalConfiguration.get().setProviders(List.of(provider));
        j.configRoundtrip();
        var restored = (RestProviderDefinition)
                ParameterFoundryGlobalConfiguration.get().getProviders().get(0);
        assertEquals("active-choices-raw-scalar", restored.getResponseMode());
        assertEquals("  null\n", restored.getRawIgnoredResponses().get(0).getValue());
        assertEquals("\nleading", restored.getRawIgnoredResponses().get(1).getValue());
        assertEquals("", restored.getRawIgnoredResponses().get(2).getValue());
        assertEquals(503, restored.getRawFailureChoices().get(0).getStatus());
        assertEquals(digest, restored.configurationDigest());
        restored.setRawTrailingChoices(List.of(new RestRawLiteral("changed")));
        assertNotEquals(digest, restored.configurationDigest());
    }
}
