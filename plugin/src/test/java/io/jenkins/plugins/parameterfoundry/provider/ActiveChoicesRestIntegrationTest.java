package io.jenkins.plugins.parameterfoundry.provider;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import hudson.EnvVars;
import hudson.cli.CLICommand;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition;
import io.jenkins.plugins.parameterfoundry.model.Option;
import io.jenkins.plugins.parameterfoundry.snapshot.ProviderSnapshotStore;
import io.jenkins.plugins.parameterfoundry.snapshot.ProviderSubmission;
import io.jenkins.plugins.parameterfoundry.snapshot.SnapshotAudience;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.sf.json.JSONObject;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.util.NameValuePair;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ActiveChoicesRestIntegrationTest {
    private FoundryChoiceParameterDefinition definition(String name, String provider) {
        var definition = new FoundryChoiceParameterDefinition(name);
        definition.setProviderId(provider);
        definition.setRenderer("select");
        definition.setDefaultMode("first");
        definition.setStandardApiInputMode("active-choices");
        return definition;
    }

    private JenkinsRule.JSONWebResponse postJson(JenkinsRule j, String path, JSONObject body) throws Exception {
        var request = new WebRequest(new URL(j.getURL(), path), HttpMethod.POST);
        request.setAdditionalHeader("Content-Type", "application/json");
        request.setRequestBody(body.toString());
        var issuer = j.jenkins.getCrumbIssuer();
        request.setAdditionalHeader(issuer.getCrumbRequestField(), issuer.getCrumb());
        return new JenkinsRule.JSONWebResponse(j.createWebClient().loadWebResponse(request));
    }

    @Test
    void actualRestRequestsPreserveUiApiDependencyAndFallbackContracts(JenkinsRule j) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var client = new RestProviderClient(new NetworkPolicy(host -> List.of(InetAddress.getByName("203.0.113.10"))));
        var responseBody = new AtomicReference<>("{\"data\":[\"gamma:selected\",\"alpha:selected\"]}");
        var requests = new AtomicInteger();
        var dependencyQuery = new AtomicReference<String>();
        server.createContext("/api/", exchange -> {
            requests.incrementAndGet();
            dependencyQuery.set(exchange.getRequestURI().getRawQuery());
            byte[] bytes = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var profile = new ConnectionProfile(
                    "source-profile",
                    "http://127.0.0.1" + ":" + server.getAddress().getPort() + "/api/");
            profile.setAllowedPathPrefixes(List.of("/api/"));
            profile.setAllowedQueryParameters(List.of("changeSet"));
            profile.setAllowPrivateAddresses(true);
            var ordinary = new RestProviderDefinition("source-name", "source-profile", "name", client);
            ordinary.setResponseMode("active-choices-choice");
            ordinary.setCacheTtlSeconds(0);
            ordinary.setFallbackOptions(List.of(new Option("fallback")));
            var cascade = new RestProviderDefinition("source-env", "source-profile", "env", client);
            cascade.setResponseMode("active-choices-cascade");
            cascade.setCacheTtlSeconds(0);
            cascade.setFallbackOptions(List.of(new Option("fallback")));
            var query = new RestQueryParameter("changeSet", "");
            query.setDependencyParameter("NAME");
            cascade.setQueryParameters(List.of(query));
            ParameterFoundryGlobalConfiguration.get().setConnectionProfiles(List.of(profile));
            ParameterFoundryGlobalConfiguration.get().setProviders(List.of(ordinary, cascade));
            var name = definition("NAME", "source-name");
            name.setActiveChoicesFilterLength(3);
            var env = definition("ENV", "source-env");
            env.setDependsOn(List.of("NAME"));
            env.setActiveChoicesApiDefaultMode("literal");
            env.setActiveChoicesApiDefaultLiteral("independent-fallback");
            var job = j.createFreeStyleProject("isolated-source-rest");
            job.addProperty(new ParametersDefinitionProperty(name, env));
            var body = JSONObject.fromObject("{\"parameter\":\"NAME\",\"version\":7}");
            var resolved = postJson(j, job.getUrl() + "parameter-foundry/api/v1/resolve", body)
                    .getJSONObject();
            assertTrue(resolved.optBoolean("ok"), resolved.toString());
            assertTrue(resolved.containsKey("default"), resolved.toString());
            assertFalse(resolved.getBoolean("fallback"), resolved.toString() + " requests=" + requests.get());
            assertEquals("alpha", resolved.getString("default"));
            assertEquals(1, resolved.getInt("sourceChoiceDefaultIndex"));
            assertEquals("active-choices-regex", resolved.getString("filterMode"));
            assertEquals(3, resolved.getInt("sourceChoiceFilterLength"));
            assertEquals(2, resolved.getJSONArray("options").size());
            var value = assertInstanceOf(StringParameterValue.class, name.getDefaultParameterValue());
            assertEquals("gamma,alpha", value.getValue());
            var exports = new EnvVars();
            value.buildEnvironment(null, exports);
            assertEquals("gamma,alpha", exports.get("NAME"));
            int beforeLiteral = requests.get();
            assertEquals(
                    " literal ",
                    name.createValue((CLICommand) null, " literal ").getValue());
            assertEquals("independent-fallback", env.getDefaultParameterValue().getValue());
            assertEquals(beforeLiteral, requests.get());
            var missing = postJson(
                            j,
                            job.getUrl() + "parameter-foundry/api/v1/resolve",
                            JSONObject.fromObject("{\"parameter\":\"ENV\",\"version\":8}"))
                    .getJSONObject();
            assertTrue(missing.optBoolean("ok"), missing.toString());
            assertTrue(missing.getBoolean("fallback"), missing.toString());
            assertEquals("fallback", missing.getString("default"));
            assertEquals(beforeLiteral, requests.get());
            body = JSONObject.fromObject("{\"parameter\":\"ENV\",\"version\":8,\"dependencies\":{\"NAME\":\"gamma\"}}");
            var explicitEmpty = postJson(
                            j,
                            job.getUrl() + "parameter-foundry/api/v1/resolve",
                            JSONObject.fromObject(
                                    "{\"parameter\":\"ENV\",\"version\":8,\"dependencies\":{\"NAME\":null}}"))
                    .getJSONObject();
            assertTrue(explicitEmpty.getBoolean("ok"));
            assertEquals("changeSet=", dependencyQuery.get());
            var undeclared = postJson(
                            j,
                            job.getUrl() + "parameter-foundry/api/v1/resolve",
                            JSONObject.fromObject("{\"parameter\":\"ENV\",\"dependencies\":{\"OTHER\":\"gamma\"}}"))
                    .getJSONObject();
            assertFalse(undeclared.getBoolean("ok"));
            postJson(j, job.getUrl() + "parameter-foundry/api/v1/resolve", body);
            assertEquals("changeSet=gamma", dependencyQuery.get());
            responseBody.set("invalid");
            resolved = postJson(j, job.getUrl() + "parameter-foundry/api/v1/resolve", body)
                    .getJSONObject();
            assertTrue(resolved.getBoolean("fallback"));
            assertEquals("fallback", resolved.getString("default"));
            responseBody.set("{\"data\":true}");
            resolved = postJson(j, job.getUrl() + "parameter-foundry/api/v1/resolve", body)
                    .getJSONObject();
            assertFalse(resolved.getBoolean("fallback"), resolved.toString());
            assertEquals(0, resolved.getJSONArray("options").size());
            assertEquals(-1, resolved.getInt("sourceChoiceDefaultIndex"));
            responseBody.set("{\"data\":{\"alpha\":\"First:selected\",\"alpha:selected\":\"Alias\"}}");
            body = JSONObject.fromObject("{\"parameter\":\"NAME\",\"version\":9}");
            resolved = postJson(j, job.getUrl() + "parameter-foundry/api/v1/resolve", body)
                    .getJSONObject();
            assertEquals(0, resolved.getInt("sourceChoiceDefaultIndex"));
            assertEquals(
                    "alpha", resolved.getJSONArray("options").getJSONObject(0).getString("value"));
            assertEquals(
                    "alpha", resolved.getJSONArray("options").getJSONObject(1).getString("value"));
            assertEquals(1, job.getNextBuildNumber());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void realHttpSnapshotSubmissionNormalizesOnlySourceCascadeMissingDependencies(JenkinsRule j) throws Exception {
        var profile = new ConnectionProfile("source-profile", "https://example.test/api/");
        var cascade = new RestProviderDefinition("source-env", "source-profile", "env");
        cascade.setResponseMode("active-choices-cascade");
        cascade.setCacheTtlSeconds(0);
        ParameterFoundryGlobalConfiguration.get().setConnectionProfiles(List.of(profile));
        ParameterFoundryGlobalConfiguration.get().setProviders(List.of(cascade));
        var env = definition("ENV", "source-env");
        env.setDependsOn(List.of("NAME"));
        env.setActiveChoicesApiDefaultMode("literal");
        env.setActiveChoicesApiDefaultLiteral("fallback");
        var job = j.createFreeStyleProject("isolated-snapshot-http");
        job.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("NAME"), env));
        j.jenkins.setCrumbIssuer(null);
        var now = Instant.now();
        var resolution = new ProviderResolution(
                List.of(new Option("fallback")),
                null,
                ProviderResolutionService.currentProviderDigest(job, env),
                "options",
                now,
                now.plusSeconds(300),
                false,
                false,
                null,
                "synthetic");
        var absent = ProviderSnapshotStore.get().create(job, env, Map.of(), resolution, SnapshotAudience.BUILD_VALUE);
        var empty = ProviderSnapshotStore.get()
                .create(job, env, Map.of("NAME", ""), resolution, SnapshotAudience.BUILD_VALUE);
        var request =
                new WebRequest(new URL(j.getURL(), job.getUrl() + "buildWithParameters?delay=0sec"), HttpMethod.POST);
        var client = j.createWebClient().withThrowExceptionOnFailingStatusCode(false);
        request.setRequestParameters(List.of(
                new NameValuePair("ENV", "literal"),
                new NameValuePair(ProviderSubmission.snapshotRequestField(env), absent.snapshotId())));
        assertTrue(client.getPage(request).getWebResponse().getStatusCode() < 400);
        j.waitUntilNoActivity();
        assertEquals(1, job.getLastBuild().getNumber());
        assertEquals(
                "literal",
                job.getLastBuild()
                        .getAction(ParametersAction.class)
                        .getParameter("ENV")
                        .getValue());
        request.setRequestParameters(List.of(
                new NameValuePair("ENV", "literal"),
                new NameValuePair(ProviderSubmission.snapshotRequestField(env), empty.snapshotId()),
                new NameValuePair(ProviderSubmission.dependenciesRequestField(env), "{\"NAME\":null}")));
        assertTrue(client.getPage(request).getWebResponse().getStatusCode() < 400);
        j.waitUntilNoActivity();
        assertEquals(2, job.getLastBuild().getNumber());
        request.setRequestParameters(List.of(
                new NameValuePair("ENV", "literal"),
                new NameValuePair(ProviderSubmission.snapshotRequestField(env), empty.snapshotId()),
                new NameValuePair("NAME", "")));
        assertTrue(client.getPage(request).getWebResponse().getStatusCode() < 400);
        j.waitUntilNoActivity();
        assertEquals(3, job.getLastBuild().getNumber());
        for (String dependencies : List.of("{\"NAME\":\"tampered\"}", "{\"OTHER\":\"x\"}", "{}")) {
            request.setRequestParameters(List.of(
                    new NameValuePair("ENV", "literal"),
                    new NameValuePair(ProviderSubmission.snapshotRequestField(env), empty.snapshotId()),
                    new NameValuePair(ProviderSubmission.dependenciesRequestField(env), dependencies)));
            assertTrue(client.getPage(request).getWebResponse().getStatusCode() >= 400, dependencies);
        }
        cascade.setResponseMode("mapped");
        resolution = new ProviderResolution(
                List.of(new Option("fallback")),
                null,
                ProviderResolutionService.currentProviderDigest(job, env),
                "options",
                now,
                now.plusSeconds(300),
                false,
                false,
                null,
                "synthetic");
        var strict = ProviderSnapshotStore.get().create(job, env, Map.of(), resolution, SnapshotAudience.BUILD_VALUE);
        request.setRequestParameters(List.of(
                new NameValuePair("ENV", "literal"),
                new NameValuePair(ProviderSubmission.snapshotRequestField(env), strict.snapshotId())));
        assertTrue(client.getPage(request).getWebResponse().getStatusCode() >= 400);
        j.waitUntilNoActivity();
        assertEquals(3, job.getLastBuild().getNumber());
    }

    @Test
    void newModeIsFingerprintBoundAndConfigurationRoundTrips(JenkinsRule j) throws Exception {
        var provider = new RestProviderDefinition("source", "profile", "options");
        String mapped = provider.configurationDigest();
        provider.setResponseMode("active-choices-choice");
        assertNotEquals(mapped, provider.configurationDigest());
        String ordinary = provider.configurationDigest();
        provider.setResponseMode("active-choices-cascade");
        assertNotEquals(ordinary, provider.configurationDigest());
        assertThrows(IllegalArgumentException.class, () -> provider.setResponseMode("invalid"));
        ParameterFoundryGlobalConfiguration.get().setProviders(List.of(provider));
        j.configRoundtrip();
        var restored = (RestProviderDefinition)
                ParameterFoundryGlobalConfiguration.get().getProviders().get(0);
        assertEquals("active-choices-cascade", restored.getResponseMode());
        assertEquals(provider.configurationDigest(), restored.configurationDigest());
    }

    @Test
    void filterSnapshotBindingRejectsFreeTextDomainCollisionAndPreservesDefaultOne(JenkinsRule j) throws Exception {
        ParameterFoundryGlobalConfiguration.get()
                .setConnectionProfiles(List.of(new ConnectionProfile("profile", "https://example.test/api/")));
        var provider = new RestProviderDefinition("source", "profile", "options");
        provider.setResponseMode("active-choices-cascade");
        ParameterFoundryGlobalConfiguration.get().setProviders(List.of(provider));
        var choice = definition("ENV", "source");
        choice.setDependsOn(List.of("NAME"));
        choice.setActiveChoicesApiDefaultMode("literal");
        choice.setActiveChoicesApiDefaultLiteral("fallback");
        var job = j.createFreeStyleProject();
        job.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("NAME"), choice));
        var now = Instant.now();
        var resolution = new ProviderResolution(
                List.of(new Option("fallback")),
                null,
                ProviderResolutionService.currentProviderDigest(job, choice),
                "options",
                now,
                now.plusSeconds(300),
                false,
                false,
                null,
                "synthetic");
        var store = ProviderSnapshotStore.get();
        var absent = store.create(job, choice, Map.of(), resolution, SnapshotAudience.BUILD_VALUE);
        var submitted = JSONObject.fromObject(
                Map.of("value", "literal", "dependenciesJson", "{}", "snapshotId", absent.snapshotId()));
        choice.setActiveChoicesFilterLength(1);
        assertEquals(
                "literal",
                choice.createValue((org.kohsuke.stapler.StaplerRequest2) null, submitted)
                        .getValue());
        choice.setActiveChoicesFilterLength(3);
        assertThrows(
                RuntimeException.class,
                () -> choice.createValue((org.kohsuke.stapler.StaplerRequest2) null, submitted));
        var three = store.create(job, choice, Map.of(), resolution, SnapshotAudience.BUILD_VALUE);
        submitted.put("snapshotId", three.snapshotId());
        assertEquals(
                "literal",
                choice.createValue((org.kohsuke.stapler.StaplerRequest2) null, submitted)
                        .getValue());
        choice.setActiveChoicesFilterLength(1);
        choice.setActiveChoicesApiDefaultLiteral("fallback" + "\0active-choices-filter-length\0" + "3");
        assertThrows(
                RuntimeException.class,
                () -> choice.createValue((org.kohsuke.stapler.StaplerRequest2) null, submitted));
        choice.setActiveChoicesApiDefaultLiteral("fallback");
        assertThrows(
                RuntimeException.class,
                () -> choice.createValue((org.kohsuke.stapler.StaplerRequest2) null, submitted));
        submitted.put("snapshotId", absent.snapshotId());
        assertEquals(
                "literal",
                choice.createValue((org.kohsuke.stapler.StaplerRequest2) null, submitted)
                        .getValue());
        choice.setActiveChoicesFilterLength(0);
        assertThrows(
                RuntimeException.class,
                () -> choice.createValue((org.kohsuke.stapler.StaplerRequest2) null, submitted));
    }

    @Test
    void filterLengthSurvivesConfigurationSnippetsAndSnapshotBindings(JenkinsRule j) throws Exception {
        ParameterFoundryGlobalConfiguration.get()
                .setConnectionProfiles(List.of(new ConnectionProfile("profile", "https://example.test/api/")));
        var provider = new RestProviderDefinition("source", "profile", "options");
        provider.setResponseMode("active-choices-cascade");
        ParameterFoundryGlobalConfiguration.get().setProviders(List.of(provider));
        var choice = definition("ENV", "source");
        assertEquals(1, choice.getActiveChoicesFilterLength());
        choice.setDependsOn(List.of("NAME"));
        choice.setActiveChoicesApiDefaultMode("literal");
        choice.setActiveChoicesApiDefaultLiteral("fallback");
        choice.setActiveChoicesFilterLength(3);
        var job = j.createFreeStyleProject();
        job.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("NAME"), choice));
        j.configRoundtrip(job);
        choice = (FoundryChoiceParameterDefinition)
                job.getProperty(ParametersDefinitionProperty.class).getParameterDefinition("ENV");
        assertEquals(3, choice.getActiveChoicesFilterLength());
        assertEquals(
                3,
                ((FoundryChoiceParameterDefinition)
                                choice.copyWithDefaultValue(new StringParameterValue("ENV", "historical")))
                        .getActiveChoicesFilterLength());
        String snippet = io.jenkins.plugins.parameterfoundry.migration.MigrationSnippets.forTarget(choice);
        var shell = new groovy.lang.GroovyShell();
        shell.setVariable("foundryChoice", new groovy.lang.Closure<Object>(null) {
            public Object doCall(Map<String, Object> fields) {
                return org.jenkinsci.plugins.structs.describable.DescribableModel.of(
                                FoundryChoiceParameterDefinition.class)
                        .instantiate(fields);
            }
        });
        var bound = (FoundryChoiceParameterDefinition) shell.evaluate(snippet);
        assertEquals(3, bound.getActiveChoicesFilterLength());
        assertEquals(List.of("NAME"), bound.getDependsOn());
        assertEquals("literal", bound.getActiveChoicesApiDefaultMode());
        assertEquals("fallback", bound.getActiveChoicesApiDefaultLiteral());
        var now = Instant.now();
        var resolution = new ProviderResolution(
                List.of(new Option("fallback")),
                null,
                ProviderResolutionService.currentProviderDigest(job, choice),
                "options",
                now,
                now.plusSeconds(300),
                false,
                false,
                null,
                "synthetic");
        var snapshot =
                ProviderSnapshotStore.get().create(job, choice, Map.of(), resolution, SnapshotAudience.BUILD_VALUE);
        var submitted = JSONObject.fromObject(
                Map.of("value", "literal", "dependenciesJson", "{}", "snapshotId", snapshot.snapshotId()));
        assertEquals(
                "literal",
                choice.createValue((org.kohsuke.stapler.StaplerRequest2) null, submitted)
                        .getValue());
        choice.setActiveChoicesFilterLength(1);
        final var changed = choice;
        assertThrows(
                RuntimeException.class,
                () -> changed.createValue((org.kohsuke.stapler.StaplerRequest2) null, submitted));
        choice.setActiveChoicesFilterLength(-1);
        assertFalse(io.jenkins.plugins.parameterfoundry.validation.DefinitionValidationService.validateStoredDefinition(
                        choice)
                .isValid());
    }
}
