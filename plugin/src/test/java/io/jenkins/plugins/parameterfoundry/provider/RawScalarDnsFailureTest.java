package io.jenkins.plugins.parameterfoundry.provider;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterValue;
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class RawScalarDnsFailureTest {
    private ConnectionProfile profile(int port) {
        var profile = new ConnectionProfile("dns-profile", "http://owned-dns.invalid:" + port + "/api/");
        profile.setAllowedPathPrefixes(List.of("/api/"));
        profile.setUseJenkinsProxy(false);
        profile.setAllowPrivateAddresses(true);
        return profile;
    }

    private RestProviderDefinition provider(NetworkPolicy.AddressResolver resolver) {
        var provider = new RestProviderDefinition(
                "dns-source", "dns-profile", "value", null, new RawScalarRestTransport(new NetworkPolicy(resolver)));
        provider.setResponseMode("active-choices-raw-scalar");
        provider.setCacheTtlSeconds(0);
        provider.setRawTrailingChoices(List.of(new RestRawLiteral("release/prod")));
        provider.setRawFailureChoices(List.of(new RestRawFailureChoice("DNS_UNRESOLVED", "reviewed-unresolved")));
        return provider;
    }

    private ProviderLoadContext context(JenkinsRule j, ConnectionProfile profile) throws Exception {
        return new ProviderLoadContext(
                new ProviderRequest(
                        j.createFreeStyleProject(),
                        "dns-source",
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
    void declaredDnsChoiceAndNativeDefaultRecoverOnTheNextUncachedEvaluation(JenkinsRule j) throws Exception {
        var calls = new AtomicInteger();
        var requests = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress(OwnedHttpPeer.address(), 0), 0);
        server.createContext("/api/value", exchange -> {
            requests.incrementAndGet();
            byte[] body = "release/recovered\n".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            var provider = provider(host -> {
                if (calls.incrementAndGet() == 1) throw new UnknownHostException("private-resolver-diagnostic");
                return List.of(OwnedHttpPeer.address());
            });
            var configuration = ParameterFoundryGlobalConfiguration.get();
            configuration.setConnectionProfiles(
                    List.of(profile(server.getAddress().getPort())));
            configuration.setProviders(List.of(provider));
            var definition = new FoundryChoiceParameterDefinition("BRANCH");
            definition.setProviderId("dns-source");
            definition.setRenderer("select");
            definition.setDefaultMode("first");
            definition.setStandardApiInputMode("active-choices");
            var job = j.createFreeStyleProject();
            job.addProperty(new ParametersDefinitionProperty(definition));
            assertEquals(
                    "reviewed-unresolved",
                    assertInstanceOf(StringParameterValue.class, definition.getDefaultParameterValue())
                            .getValue());
            assertEquals(0, requests.get());
            assertEquals(
                    "release/recovered\n",
                    assertInstanceOf(StringParameterValue.class, definition.getDefaultParameterValue())
                            .getValue());
            assertEquals(2, calls.get());
            assertEquals(1, requests.get());
            assertEquals(1, job.getNextBuildNumber());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void unconfiguredDnsFailuresAndExistingPolicyCallersKeepSanitizedRejection(JenkinsRule j) throws Exception {
        NetworkPolicy.AddressResolver resolver = host -> {
            throw new UnknownHostException("private-resolver-diagnostic");
        };
        var p = profile(18080);
        var legacy = assertThrows(ProviderException.class, () -> new NetworkPolicy(resolver)
                .resolveAllowed(p.buildUri("value", Map.of()), true));
        assertEquals("Provider target could not be resolved.", legacy.getMessage());
        assertNull(legacy.getCause());
        var provider = provider(resolver);
        provider.setRawFailureChoices(List.of());
        var context = context(j, p);
        var rejected = assertThrows(ProviderException.class, () -> provider.load(context));
        assertFalse(rejected.getMessage().contains("private-resolver-diagnostic"));
    }

    @Test
    void emptyOrProhibitedAddressSetsCannotBecomeDnsChoices(JenkinsRule j) throws Exception {
        var p = profile(18080);
        var context = context(j, p);
        var empty = provider(host -> List.of());
        assertTrue(assertThrows(ProviderException.class, () -> empty.load(context))
                .getMessage()
                .contains("did not resolve"));
        var mixed = provider(host -> List.of(OwnedHttpPeer.address(), InetAddress.getByName("127.0.0.1")));
        assertTrue(assertThrows(ProviderException.class, () -> mixed.load(context))
                .getMessage()
                .contains("prohibited address"));
    }

    @Test
    void invalidProfileAndInterruptRejectBeforeAnyDnsChoice(JenkinsRule j) throws Exception {
        var calls = new AtomicInteger();
        var provider = provider(host -> {
            calls.incrementAndGet();
            throw new UnknownHostException("private-resolver-diagnostic");
        });
        var p = profile(18080);
        p.setCredentialId("not-permitted-for-raw");
        var context = context(j, p);
        assertThrows(ProviderException.class, () -> provider.load(context));
        assertEquals(0, calls.get());
        p.setCredentialId(null);
        try {
            Thread.currentThread().interrupt();
            assertTrue(assertThrows(ProviderException.class, () -> provider.load(context))
                    .getMessage()
                    .contains("interrupted"));
        } finally {
            Thread.interrupted();
        }
        assertEquals(0, calls.get());
    }

    @Test
    void lateDnsResultsCannotTurnAnExpiredBudgetIntoSelectableData(JenkinsRule j) throws Exception {
        var p = profile(18080);
        p.setTotalTimeoutSeconds(1);
        var context = context(j, p);
        for (boolean unresolved : List.of(true, false)) {
            var provider = provider(host -> {
                try {
                    Thread.sleep(1100);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                if (unresolved) throw new UnknownHostException("private-resolver-diagnostic");
                return List.of(OwnedHttpPeer.address());
            });
            assertTrue(assertThrows(ProviderException.class, () -> provider.load(context))
                    .getMessage()
                    .contains("total time budget"));
        }
    }

    @Test
    void dnsOutcomeRoundtripsThroughTheRealConfigurationForm(JenkinsRule j) throws Exception {
        var provider = provider(host -> {
            throw new UnknownHostException();
        });
        var configuration = ParameterFoundryGlobalConfiguration.get();
        configuration.setConnectionProfiles(List.of(profile(18080)));
        configuration.setProviders(List.of(provider));
        j.configRoundtrip();
        var restored = (RestProviderDefinition) configuration.getProviders().get(0);
        assertEquals("DNS_UNRESOLVED", restored.getRawFailureChoices().get(0).getKind());
        assertEquals(
                "reviewed-unresolved", restored.getRawFailureChoices().get(0).getValue());
        assertThrows(
                IllegalArgumentException.class,
                () -> restored.getRawFailureChoices().get(0).setStatus(404));
    }
}
