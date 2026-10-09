package io.jenkins.plugins.parameterfoundry.api;

import static org.junit.jupiter.api.Assertions.*;

import hudson.cli.CLICommand;
import hudson.cli.CLICommandInvoker;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterValue;
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition;
import io.jenkins.plugins.parameterfoundry.evaluation.EvaluationException;
import io.jenkins.plugins.parameterfoundry.model.Option;
import io.jenkins.plugins.parameterfoundry.provider.*;
import io.jenkins.plugins.parameterfoundry.snapshot.*;
import java.net.URL;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.sf.json.JSONObject;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.util.NameValuePair;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ActiveChoicesSingleRuntimeTest {
    private FoundryChoiceParameterDefinition definition() {
        var d = new FoundryChoiceParameterDefinition("node");
        d.setStandardApiInputMode("active-choices");
        d.setDefaultMode("none");
        d.setRenderer("radio");
        return d;
    }

    @Test
    void actualHttpAndCliKeepLiteralStringsAndRejectRepeatedFields(JenkinsRule j) throws Exception {
        var d = definition();
        d.setChoices(List.of(new Option("built-in")));
        var job = j.createFreeStyleProject();
        job.addProperty(new ParametersDefinitionProperty(d));
        j.jenkins.setCrumbIssuer(null);
        var cli = new CLICommandInvoker(j, "build")
                .invokeWithArgs(job.getFullName(), "-f", "-p", "node= agent-a || agent-z ");
        assertEquals(0, cli.returnCode(), cli.stderr());
        assertEquals(
                " agent-a || agent-z ",
                job.getLastBuild()
                        .getAction(ParametersAction.class)
                        .getParameter("node")
                        .getValue());
        var request =
                new WebRequest(new URL(j.getURL(), job.getUrl() + "buildWithParameters?delay=0sec"), HttpMethod.POST);
        request.setRequestParameters(List.of(new NameValuePair("node", "")));
        j.createWebClient().getPage(request);
        j.waitUntilNoActivity();
        assertEquals(
                "",
                job.getLastBuild()
                        .getAction(ParametersAction.class)
                        .getParameter("node")
                        .getValue());
        var count = job.getLastBuild().getNumber();
        request.setRequestParameters(List.of(new NameValuePair("node", "a"), new NameValuePair("node", "b")));
        var response =
                j.createWebClient().withThrowExceptionOnFailingStatusCode(false).getPage(request);
        assertTrue(response.getWebResponse().getStatusCode() >= 400);
        j.waitUntilNoActivity();
        assertEquals(count, job.getLastBuild().getNumber());
        var env = new hudson.EnvVars();
        d.createValue((CLICommand) null, " literal ").buildEnvironment(null, env);
        assertEquals(" literal ", env.get("node"));
        assertEquals(" literal ", env.get("NODE"));
    }

    @Test
    void explicitInputsAndLiteralDefaultsDoNotFetchButSnapshotsStillBind(JenkinsRule j) throws Exception {
        var calls = new AtomicInteger();
        var provider = new CountingProvider(calls);
        ParameterFoundryGlobalConfiguration.get().setProviders(List.of(provider));
        var d = definition();
        d.setProviderId("active-nodes");
        d.setActiveChoicesApiDefaultMode("literal");
        d.setActiveChoicesApiDefaultLiteral("unlisted-default");
        var job = j.createFreeStyleProject();
        job.addProperty(new ParametersDefinitionProperty(d));
        assertEquals("unlisted-default", d.getDefaultParameterValue().getValue());
        assertEquals(
                "unknown || other",
                d.createValue((CLICommand) null, "unknown || other").getValue());
        assertTrue(d.isValid(new StringParameterValue("node", "unlisted")));
        assertEquals(0, calls.get());
        var now = Instant.now();
        var resolution = new ProviderResolution(
                List.of(new Option("built-in")),
                null,
                ProviderResolutionService.currentProviderDigest(job, d),
                "options",
                now,
                now.plusSeconds(60),
                false,
                false,
                null,
                "test");
        var store = ProviderSnapshotStore.get();
        var snapshot = store.create(job, d, Map.of(), resolution, SnapshotAudience.BUILD_VALUE);
        var submitted =
                JSONObject.fromObject(Map.of("name", "node", "value", "unknown", "snapshotId", snapshot.snapshotId()));
        assertEquals(
                "unknown",
                d.createValue((org.kohsuke.stapler.StaplerRequest2) null, submitted)
                        .getValue());
        assertThrows(
                EvaluationException.class,
                () -> TypedParameterAdapterRegistry.decode(
                        job, d, true, "unknown", Map.of(), snapshot.snapshotId(), ProviderOperation.BUILD));
        var other = j.createFreeStyleProject();
        var otherDefinition = definition();
        otherDefinition.setProviderId("active-nodes");
        other.addProperty(new ParametersDefinitionProperty(otherDefinition));
        assertThrows(
                RuntimeException.class,
                () -> otherDefinition.createValue((org.kohsuke.stapler.StaplerRequest2) null, submitted));
        var expired = new ProviderResolution(
                resolution.options(),
                null,
                resolution.providerDigest(),
                "options",
                now.minusSeconds(10),
                now.minusSeconds(1),
                false,
                false,
                null,
                "test");
        var old = store.create(job, d, Map.of(), expired, SnapshotAudience.BUILD_VALUE);
        submitted.put("snapshotId", old.snapshotId());
        assertThrows(
                RuntimeException.class, () -> d.createValue((org.kohsuke.stapler.StaplerRequest2) null, submitted));
        var diagnostic = store.create(job, d, Map.of(), resolution, SnapshotAudience.DIAGNOSTIC);
        submitted.put("snapshotId", diagnostic.snapshotId());
        assertThrows(
                RuntimeException.class, () -> d.createValue((org.kohsuke.stapler.StaplerRequest2) null, submitted));
        submitted.put("snapshotId", snapshot.snapshotId());
        d.setActiveChoicesApiDefaultLiteral("changed-default");
        assertThrows(
                RuntimeException.class, () -> d.createValue((org.kohsuke.stapler.StaplerRequest2) null, submitted));
        submitted.put("snapshotId", "missing");
        assertThrows(
                RuntimeException.class, () -> d.createValue((org.kohsuke.stapler.StaplerRequest2) null, submitted));
        assertEquals(0, calls.get());
    }

    @Test
    void emptyOrderedSourceHasAnExistingEmptyDefault(JenkinsRule j) throws Exception {
        var d = definition();
        assertInstanceOf(StringParameterValue.class, d.getDefaultParameterValue());
        assertEquals("", d.getDefaultParameterValue().getValue());
    }

    @Test
    void configuredEmptyFailurePolicyKeepsTheSourceEmptyStringDefault(JenkinsRule j) throws Exception {
        var provider = new FailingProvider();
        provider.setCacheTtlSeconds(0);
        provider.setFailurePolicy("empty");
        ParameterFoundryGlobalConfiguration.get().setProviders(List.of(provider));
        var job = j.createFreeStyleProject();
        var d = definition();
        d.setProviderId("failed-nodes");
        job.addProperty(new ParametersDefinitionProperty(d));
        assertInstanceOf(StringParameterValue.class, d.getDefaultParameterValue());
        assertEquals("", d.getDefaultParameterValue().getValue());
    }

    private static final class FailingProvider extends SharedProviderDefinition {
        FailingProvider() {
            super("failed-nodes");
        }

        @Override
        public List<Option> load(ProviderLoadContext context) {
            throw new ProviderException(
                    io.jenkins.plugins.parameterfoundry.evaluation.ErrorCode.PROVIDER_UNAVAILABLE,
                    getId(),
                    "Synthetic unavailable provider");
        }

        @Override
        protected String providerFingerprint() {
            return "source-empty-failure";
        }
    }

    private static final class CountingProvider extends SharedProviderDefinition {
        private transient AtomicInteger calls;

        CountingProvider(AtomicInteger calls) {
            super("active-nodes");
            this.calls = calls;
        }

        @Override
        public List<Option> load(ProviderLoadContext context) {
            calls.incrementAndGet();
            return List.of(new Option("built-in"));
        }

        @Override
        protected String providerFingerprint() {
            return "active-single-runtime";
        }
    }
}
