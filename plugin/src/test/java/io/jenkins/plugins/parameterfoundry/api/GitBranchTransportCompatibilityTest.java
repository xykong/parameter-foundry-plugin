package io.jenkins.plugins.parameterfoundry.api;

import static org.junit.jupiter.api.Assertions.*;

import hudson.cli.CLICommandInvoker;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterValue;
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition;
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterValue;
import io.jenkins.plugins.parameterfoundry.evaluation.ErrorCode;
import io.jenkins.plugins.parameterfoundry.evaluation.EvaluationException;
import io.jenkins.plugins.parameterfoundry.model.Option;
import io.jenkins.plugins.parameterfoundry.provider.*;
import io.jenkins.plugins.parameterfoundry.snapshot.*;
import io.jenkins.plugins.parameterfoundry.validation.DefinitionValidationService;
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
class GitBranchTransportCompatibilityTest {

    private FoundryChoiceParameterDefinition definition() {
        var definition = new FoundryChoiceParameterDefinition("REF");
        definition.setProviderId("git-refs");
        definition.setStandardApiInputMode("list-git-branches");
        definition.setDefaultMode("explicit");
        definition.setDefaultValue("release/default");
        return definition;
    }

    @Test
    void cliAndDefaultsAcceptUnlistedStringsWithoutProvider(JenkinsRule j) throws Exception {
        var job = j.createFreeStyleProject();
        var definition = definition();
        job.addProperty(new ParametersDefinitionProperty(definition));
        assertEquals("release/default", scalar(definition.getDefaultParameterValue()));
        assertEquals(" unknown ", scalar(definition.createValue((hudson.cli.CLICommand) null, " unknown ")));
        assertEquals("release/default", scalar(definition.createValue((hudson.cli.CLICommand) null, "")));
        assertTrue(definition.isValid(new StringParameterValue("REF", "unknown")));
        var result = new CLICommandInvoker(j, "build").invokeWithArgs(job.getFullName(), "-f", "-p", "REF=unknown");
        assertEquals(0, result.returnCode(), result.stderr());
        assertEquals(
                "unknown",
                scalar(job.getLastBuild().getAction(ParametersAction.class).getParameter("REF")));
        definition.setDefaultMode("none");
        definition.setDefaultValue("");
        assertNull(definition.getDefaultParameterValue());
        assertNull(definition.createValue((hudson.cli.CLICommand) null, ""));
    }

    @Test
    void standardRequestReadsFirstFieldAndFallsBackWithoutGit(JenkinsRule j) throws Exception {
        var job = j.createFreeStyleProject();
        job.addProperty(new ParametersDefinitionProperty(definition()));
        j.jenkins.setCrumbIssuer(null);
        for (String first : List.of(" unknown ", " ")) {
            var request = new WebRequest(
                    new URL(j.getURL(), job.getUrl() + "buildWithParameters?delay=0sec"), HttpMethod.POST);
            request.setRequestParameters(List.of(new NameValuePair("REF", first), new NameValuePair("REF", "ignored")));
            j.createWebClient().getPage(request);
            j.waitUntilNoActivity();
            assertEquals(
                    first.isBlank() ? "release/default" : first,
                    scalar(job.getLastBuild().getAction(ParametersAction.class).getParameter("REF")));
        }
    }

    @Test
    void formFallbackKeepsStoredLiteralInsteadOfFetchingTop(JenkinsRule j) {
        var definition = definition();
        definition.setDefaultMode("first");
        definition.setDefaultValue("");
        for (String json : List.of("{value:''}", "{value:[]}", "{value:null}", "{value:true}", "{value:['']}")) {
            assertEquals(
                    "",
                    scalar(definition.createValue(
                            (org.kohsuke.stapler.StaplerRequest2) null, JSONObject.fromObject(json))));
        }
        assertEquals(
                "a,b,a",
                scalar(definition.createValue(
                        (org.kohsuke.stapler.StaplerRequest2) null, JSONObject.fromObject("{value:['a','b','a']}"))));
    }

    @Test
    void typedStringNullAndOmissionDoNotLoadProviderButTopDoes(JenkinsRule j) throws Exception {
        var calls = new AtomicInteger();
        ParameterFoundryGlobalConfiguration.get().setProviders(List.of(new CountingProvider(calls)));
        var job = j.createFreeStyleProject();
        var definition = definition();
        job.addProperty(new ParametersDefinitionProperty(definition));
        assertEquals(
                "unknown",
                scalar(TypedParameterAdapterRegistry.decode(
                        job, definition, true, "unknown", Map.of(), null, ProviderOperation.BUILD)));
        assertNull(scalar(TypedParameterAdapterRegistry.decode(
                job, definition, true, null, Map.of(), null, ProviderOperation.BUILD)));
        assertEquals(
                "release/default",
                scalar(TypedParameterAdapterRegistry.decode(
                        job, definition, false, null, Map.of(), null, ProviderOperation.BUILD)));
        assertThrows(
                EvaluationException.class,
                () -> TypedParameterAdapterRegistry.decode(
                        job, definition, true, 1, Map.of(), null, ProviderOperation.BUILD));
        assertEquals(0, calls.get());
        assertThrows(
                RuntimeException.class,
                () -> definition.createValue(
                        (org.kohsuke.stapler.StaplerRequest2) null,
                        JSONObject.fromObject("{value:'unknown',snapshotId:'missing'}")));
        assertEquals(0, calls.get());
        definition.setDefaultMode("first");
        definition.setDefaultValue("");
        assertEquals(
                "first",
                scalar(TypedParameterAdapterRegistry.decode(
                        job, definition, false, null, Map.of(), null, ProviderOperation.BUILD)));
        assertEquals(1, calls.get());
        assertEquals("first", scalar(definition.getDefaultParameterValue()));
    }

    @Test
    void literalSubmissionsCheckSnapshotBindingAndExpiryWithoutLoading(JenkinsRule j) throws Exception {
        var calls = new AtomicInteger();
        ParameterFoundryGlobalConfiguration.get().setProviders(List.of(new CountingProvider(calls)));
        var job = j.createFreeStyleProject();
        var definition = definition();
        job.addProperty(new ParametersDefinitionProperty(definition));
        var store = ProviderSnapshotStore.get();
        var now = Instant.now();
        var resolution = new ProviderResolution(
                List.of(new Option("first")),
                null,
                ProviderResolutionService.currentProviderDigest(job, definition),
                "options",
                now,
                now.plusSeconds(60),
                false,
                false,
                null,
                "test");
        var snapshot = store.create(job, definition, Map.of(), resolution, SnapshotAudience.BUILD_VALUE);
        var value = (FoundryChoiceParameterValue) TypedParameterAdapterRegistry.decode(
                job, definition, true, "unknown", Map.of(), snapshot.snapshotId(), ProviderOperation.BUILD);
        assertEquals("unknown", value.getScalarValue());
        var other = j.createFreeStyleProject();
        other.addProperty(new ParametersDefinitionProperty(definition()));
        assertSnapshotInvalid(() -> TypedParameterAdapterRegistry.decode(
                other, definition, true, "unknown", Map.of(), snapshot.snapshotId(), ProviderOperation.BUILD));
        assertSnapshotInvalid(() -> TypedParameterAdapterRegistry.decode(
                job, definition, true, "unknown", Map.of(), "missing", ProviderOperation.BUILD));
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
        var old = store.create(job, definition, Map.of(), expired, SnapshotAudience.BUILD_VALUE);
        assertSnapshotInvalid(() -> TypedParameterAdapterRegistry.decode(
                job, definition, true, "unknown", Map.of(), old.snapshotId(), ProviderOperation.BUILD));
        var diagnostic = store.create(job, definition, Map.of(), resolution, SnapshotAudience.DIAGNOSTIC);
        assertSnapshotInvalid(() -> TypedParameterAdapterRegistry.decode(
                job, definition, true, "unknown", Map.of(), diagnostic.snapshotId(), ProviderOperation.BUILD));
        definition.setDefaultValue("changed");
        assertSnapshotInvalid(() -> TypedParameterAdapterRegistry.decode(
                job, definition, true, "unknown", Map.of(), snapshot.snapshotId(), ProviderOperation.BUILD));
        assertEquals(0, calls.get());
    }

    @Test
    void invalidCompatibilityConfigurationsAreRejected(JenkinsRule j) {
        var definition = definition();
        assertTrue(
                DefinitionValidationService.validateStoredDefinition(definition).isValid());
        definition.setSelectionMode("multiple");
        assertFalse(
                DefinitionValidationService.validateStoredDefinition(definition).isValid());
        definition.setSelectionMode("single");
        definition.setProviderId(null);
        assertFalse(
                DefinitionValidationService.validateStoredDefinition(definition).isValid());
    }

    @Test
    void configurationRoundTripPreservesApiDefaultAndIndependentDropdownMode(JenkinsRule j) throws Exception {
        var definition = definition();
        definition.setGitSelectionMode("top");
        var job = j.createFreeStyleProject();
        job.addProperty(new ParametersDefinitionProperty(definition));
        j.configRoundtrip(job);
        var saved = (FoundryChoiceParameterDefinition)
                job.getProperty(ParametersDefinitionProperty.class).getParameterDefinition("REF");
        assertEquals("list-git-branches", saved.getStandardApiInputMode());
        assertEquals("release/default", saved.getDefaultValue());
        assertEquals("top", saved.getGitSelectionMode());
        assertEquals("release/default", scalar(saved.getDefaultParameterValue()));
        var copy = (FoundryChoiceParameterDefinition) saved.copyWithDefaultValue(
                FoundryChoiceParameterValue.single("REF", "historical", null, "string", null));
        assertEquals("top", copy.getGitSelectionMode());
        assertEquals("historical", scalar(copy.getDefaultParameterValue()));
    }

    @Test
    void rebuildPreservesHistoricalEditableLiteralWithoutProvider(JenkinsRule j) throws Exception {
        var job = j.createFreeStyleProject();
        var definition = definition();
        job.addProperty(new ParametersDefinitionProperty(definition));
        var result =
                new CLICommandInvoker(j, "build").invokeWithArgs(job.getFullName(), "-f", "-p", "REF=old-unlisted-ref");
        assertEquals(0, result.returnCode(), result.stderr());
        var page =
                j.createWebClient().withJavaScriptEnabled(false).getPage(job.getLastBuild(), "rebuild/parameterized");
        assertFalse(((org.htmlunit.html.HtmlPage) page)
                .getByXPath("//input[@name='value' and @value='old-unlisted-ref']")
                .isEmpty());
        assertTrue(((org.htmlunit.html.HtmlPage) page)
                .getByXPath("//*[@data-provider-backed='true']")
                .isEmpty());
    }

    private static void assertSnapshotInvalid(org.junit.jupiter.api.function.Executable action) {
        assertEquals(
                ErrorCode.SNAPSHOT_INVALID,
                assertThrows(EvaluationException.class, action).getCode());
    }

    private static String scalar(hudson.model.ParameterValue value) {
        return ((FoundryChoiceParameterValue) value).getScalarValue();
    }

    private static final class CountingProvider extends SharedProviderDefinition {
        private transient AtomicInteger calls;

        CountingProvider(AtomicInteger calls) {
            super("git-refs");
            this.calls = calls;
        }

        @Override
        public List<Option> load(ProviderLoadContext context) {
            calls.incrementAndGet();
            return List.of(new Option("first"));
        }

        @Override
        protected String providerFingerprint() {
            return "git-contract";
        }
    }
}
