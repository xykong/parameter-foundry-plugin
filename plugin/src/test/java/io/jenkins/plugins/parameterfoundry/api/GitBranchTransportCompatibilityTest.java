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
    void unselectedListFormKeepsLiteralDefaultWithoutLoadingGit(JenkinsRule j) throws Exception {
        j.jenkins.setNumExecutors(0);
        j.jenkins.setCrumbIssuer(null);
        var job = j.createFreeStyleProject();
        var parameter = definition();
        parameter.setRenderer("list");
        parameter.setVisibleItemCount(20);
        parameter.setGitSelectionMode("none");
        job.addProperty(new ParametersDefinitionProperty(parameter));
        var client = j.createWebClient().withJavaScriptEnabled(false);
        client.getOptions().setThrowExceptionOnFailingStatusCode(false);
        var page = client.goTo(job.getUrl() + "build?delay=0sec");
        var field = (org.htmlunit.html.HtmlDivision) page.getFirstByXPath("//div[@data-parameter-name='REF']");
        assertEquals("20", field.getAttribute("data-visible-item-count"));
        assertEquals("none", field.getAttribute("data-git-selection-mode"));
        var request = new WebRequest(new URL(j.getURL(), job.getUrl() + "build?delay=0sec"), HttpMethod.POST);
        request.setRequestParameters(
                List.of(new NameValuePair("json", "{\"parameter\":[{\"name\":\"REF\",\"value\":[]}]}")));
        j.createWebClient().getPage(request);
        var items = j.jenkins.getQueue().getItems();
        assertEquals(1, items.length);
        try {
            var value = items[0].getAction(ParametersAction.class).getParameter("REF");
            assertEquals("release/default", scalar(value));
            var environment = new hudson.EnvVars();
            value.buildEnvironment(null, environment);
            assertEquals("release/default", environment.get("REF"));
            assertTrue(job.getBuilds().isEmpty());
        } finally {
            j.jenkins.getQueue().cancel(items[0]);
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

    @Test
    void sourceStringHistoryCopyPreservesLiteralWithoutChangingCurrentDefaults(JenkinsRule j) {
        var configured = definition();
        for (String literal : List.of("", " old-unlisted-ref ", "a,b,a", "quote\"&<ref>", "中文")) {
            var historical = new StringParameterValue("REF", literal, "Old description");
            var copy = (FoundryChoiceParameterDefinition) configured.copyWithDefaultValue(historical);
            assertNotSame(configured, copy);
            assertTrue(copy.isGitRebuildInput());
            assertEquals(literal, copy.getLegacyRebuildValue());
            assertEquals("release/default", configured.getDefaultValue());
            assertFalse(configured.isGitRebuildInput());
        }
        var normal = new FoundryChoiceParameterDefinition("REF");
        assertSame(normal, normal.copyWithDefaultValue(new StringParameterValue("REF", "old")));
    }

    @Test
    void actualRebuildOfPreMigrationStringHistoryKeepsEditableInputAndSubmission(JenkinsRule j) throws Exception {
        j.jenkins.setCrumbIssuer(null);
        for (String literal : List.of("old-unlisted-ref", " padded,ref ", "")) {
            var job = j.createFreeStyleProject();
            job.addProperty(new ParametersDefinitionProperty(new hudson.model.StringParameterDefinition("REF", "")));
            var original = j.assertBuildStatusSuccess(job.scheduleBuild2(
                    0,
                    new ParametersAction(
                            new com.syhuang.hudson.plugins.listgitbranchesparameter.ListGitBranchesParameterValue(
                                    "REF", literal))));
            job.removeProperty(ParametersDefinitionProperty.class);
            job.addProperty(new ParametersDefinitionProperty(definition()));
            assertInstanceOf(
                    FoundryChoiceParameterDefinition.class,
                    job.getProperty(ParametersDefinitionProperty.class).getParameterDefinition("REF"));
            var client = j.createWebClient();
            var page = (org.htmlunit.html.HtmlPage) client.getPage(original, "rebuild/parameterized");
            var inputs = page.getByXPath("//input[@name='value']");
            assertEquals(1, inputs.size());
            assertEquals(literal, ((org.htmlunit.html.HtmlInput) inputs.get(0)).getValue());
            assertTrue(page.getByXPath("//*[@data-provider-backed='true']").isEmpty());
            j.submit(page.getFormByName("config"));
            j.waitUntilNoActivity();
            var rebuilt = j.assertBuildStatusSuccess(job.getLastBuild());
            assertEquals(original.getNumber() + 1, rebuilt.getNumber());
            // Empty source form input falls back to the current configured default.
            assertEquals(
                    literal.isEmpty() ? "release/default" : literal,
                    scalar(rebuilt.getAction(ParametersAction.class).getParameter("REF")));
            assertEquals(
                    "release/default",
                    ((FoundryChoiceParameterDefinition) job.getProperty(ParametersDefinitionProperty.class)
                                    .getParameterDefinition("REF"))
                            .getDefaultValue());
        }
    }

    @Test
    void sensitiveStringHistoryNeverCopiesPlaintextIntoChoiceInput(JenkinsRule j) {
        var sensitive = new StringParameterValue("REF", "private-history") {
            @Override
            public boolean isSensitive() {
                return true;
            }

            @Override
            public String getValue() {
                throw new AssertionError("Sensitive history must not be read for a choice default");
            }
        };
        for (String mode : List.of("list-git-branches", "active-choices", "strict")) {
            var configured = definition();
            configured.setStandardApiInputMode(mode);
            assertSame(configured, configured.copyWithDefaultValue(sensitive));
            assertFalse(configured.isLegacyRebuildInput());
            assertEquals("release/default", configured.getDefaultValue());
        }
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
