package io.jenkins.plugins.parameterfoundry.api;

import static org.junit.jupiter.api.Assertions.*;

import hudson.EnvVars;
import hudson.cli.CLICommand;
import hudson.cli.CLICommandInvoker;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition;
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterValue;
import io.jenkins.plugins.parameterfoundry.evaluation.EvaluationException;
import io.jenkins.plugins.parameterfoundry.model.Option;
import io.jenkins.plugins.parameterfoundry.provider.ProviderOperation;
import io.jenkins.plugins.parameterfoundry.validation.DefinitionValidationService;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.util.List;
import java.util.Map;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.StaplerRequest2;

@WithJenkins
class ExtendedChoiceDefaultCompatibilityTest {
    private FoundryChoiceParameterDefinition definition(String literal) {
        var definition = new FoundryChoiceParameterDefinition("SAMPLE");
        definition.setSelectionMode("multiple");
        definition.setChoices(List.of(new Option("a"), new Option("b")));
        definition.setStandardApiInputMode("extended-choice");
        definition.setDuplicatePolicy("deduplicate");
        definition.setEnvironmentExportOrder("submitted");
        definition.setLegacyDefaultLiteral(literal);
        return definition;
    }

    private String exported(hudson.model.ParameterValue value) {
        if (value == null) {
            return null;
        }
        var environment = new EnvVars();
        value.buildEnvironment(null, environment);
        return environment.get("SAMPLE");
    }

    private StaplerRequest2 request(String[] fields) {
        return (StaplerRequest2) Proxy.newProxyInstance(
                StaplerRequest2.class.getClassLoader(),
                new Class<?>[] {StaplerRequest2.class},
                (proxy, method, arguments) ->
                        method.getName().equals("getParameterValues") && "SAMPLE".equals(arguments[0]) ? fields : null);
    }

    @Test
    void allOmissionPathsPreserveLiteralAndExplicitEmptyStaysEmpty(JenkinsRule j) throws Exception {
        for (String literal : List.of("b,a", " unknown ,a,a ", "placeholder")) {
            var definition = definition(literal);
            assertEquals(literal, exported(definition.getDefaultParameterValue()));
            assertEquals(literal, exported(definition.createValue((CLICommand) null, null)));
            assertEquals(literal, exported(definition.createValue((CLICommand) null, ",,")));
            assertEquals(literal, exported(definition.createValue(request(null))));
            assertEquals(literal, exported(definition.createValue(request(new String[0]))));
            assertEquals("", exported(definition.createValue(request(new String[] {""}))));
            assertEquals("", exported(definition.createValue((CLICommand) null, "")));
            assertEquals(
                    "",
                    exported(definition.createValue(
                            (StaplerRequest2) null, JSONObject.fromObject("{name:'SAMPLE',value:''}"))));
        }
    }

    @Test
    void blankDefaultOmitsParameterAndUiDefaultsRetainDeclaredOrder(JenkinsRule j) throws Exception {
        for (String literal : new String[] {null, "", "  "}) {
            var definition = definition(literal);
            assertNull(definition.getDefaultParameterValue());
            assertNull(definition.createValue((CLICommand) null, null));
            assertNotNull(definition.createValue((CLICommand) null, ""));
        }
        var definition = definition(null);
        definition.setDefaultMode("explicit");
        definition.setDefaultValues(List.of("b", "a"));
        var value = (FoundryChoiceParameterValue) definition.getDefaultParameterValue();
        assertEquals(List.of("a", "b"), value.getValues());
        assertEquals("b,a", exported(value));
    }

    @Test
    void typedOmissionSharesDefaultAndExplicitArraysRemainStrict(JenkinsRule j) throws Exception {
        var job = j.createFreeStyleProject();
        var definition = definition("unknown,b,a");
        job.addProperty(new ParametersDefinitionProperty(definition));
        var omitted = TypedParameterAdapterRegistry.decode(
                job, definition, false, null, Map.of(), null, ProviderOperation.VALIDATE);
        assertEquals("unknown,b,a", exported(omitted));
        var explicit = TypedParameterAdapterRegistry.decode(
                job, definition, true, JSONArray.fromObject(List.of("a")), Map.of(), null, ProviderOperation.VALIDATE);
        assertEquals("a", exported(explicit));
        assertThrows(
                EvaluationException.class,
                () -> TypedParameterAdapterRegistry.decode(
                        job,
                        definition,
                        true,
                        JSONArray.fromObject(List.of("unknown")),
                        Map.of(),
                        null,
                        ProviderOperation.VALIDATE));
        definition.setLegacyDefaultLiteral(null);
        assertNull(TypedParameterAdapterRegistry.decode(
                job, definition, false, null, Map.of(), null, ProviderOperation.VALIDATE));
    }

    @Test
    void literalSurvivesConfigurationBuildReloadAndDefaultCopy(JenkinsRule j) throws Exception {
        var job = j.createFreeStyleProject("literal-default");
        job.addProperty(new ParametersDefinitionProperty(definition(" b,a,unknown,a ")));
        j.configRoundtrip(job);
        var configured = (FoundryChoiceParameterDefinition)
                job.getProperty(ParametersDefinitionProperty.class).getParameterDefinition("SAMPLE");
        assertEquals(" b,a,unknown,a ", configured.getLegacyDefaultLiteral());
        var value = (FoundryChoiceParameterValue) configured.getDefaultParameterValue();
        assertEquals(List.of("a", "b"), value.getValues());
        assertTrue(configured.isValid(value));
        j.assertBuildStatusSuccess(
                job.scheduleBuild2(0, new ParametersAction(value)).get());
        j.jenkins.reload();
        var reloaded = j.jenkins.getItemByFullName("literal-default", hudson.model.FreeStyleProject.class);
        var historical = (FoundryChoiceParameterValue)
                reloaded.getLastBuild().getAction(ParametersAction.class).getParameter("SAMPLE");
        assertEquals(" b,a,unknown,a ", exported(historical));
        var restored = (FoundryChoiceParameterDefinition)
                reloaded.getProperty(ParametersDefinitionProperty.class).getParameterDefinition("SAMPLE");
        assertEquals(
                " b,a,unknown,a ",
                exported(restored.copyWithDefaultValue(historical).getDefaultParameterValue()));
    }

    @Test
    void actualCliBuildPreservesOmittedDefaultInTheBuildEnvironment(JenkinsRule j) throws Exception {
        var job = j.createFreeStyleProject("omitted-cli");
        job.addProperty(new ParametersDefinitionProperty(definition("b,a,unknown,a")));
        job.getBuildersList().add(new hudson.tasks.Shell("printf '%s' \"$SAMPLE\" > literal.txt"));
        var result = new CLICommandInvoker(j, "build").invokeWithArgs(job.getFullName(), "-f");
        assertEquals(0, result.returnCode(), result.stderr());
        j.waitUntilNoActivity();
        j.assertBuildStatusSuccess(job.getLastBuild());
        assertEquals(
                "b,a,unknown,a", job.getSomeWorkspace().child("literal.txt").readToString());
    }

    @Test
    void actualStandardBuildDistinguishesOmittedBlankDefaultFromEmptyField(JenkinsRule j) throws Exception {
        var job = j.createFreeStyleProject("omitted-http");
        job.addProperty(new ParametersDefinitionProperty(definition(null)));
        j.jenkins.setCrumbIssuer(null);
        var request =
                new WebRequest(new URL(j.getURL(), job.getUrl() + "buildWithParameters?delay=0sec"), HttpMethod.POST);
        j.createWebClient().getPage(request);
        j.waitUntilNoActivity();
        j.assertBuildStatusSuccess(job.getLastBuild());
        var action = job.getLastBuild().getAction(ParametersAction.class);
        assertTrue(action == null || action.getParameter("SAMPLE") == null);
        request.setRequestParameters(List.of(new org.htmlunit.util.NameValuePair("SAMPLE", "")));
        j.createWebClient().getPage(request);
        j.waitUntilNoActivity();
        j.assertBuildStatusSuccess(job.getLastBuild());
        assertEquals(
                "",
                exported(job.getLastBuild().getAction(ParametersAction.class).getParameter("SAMPLE")));
    }

    @Test
    void explicitConstraintsApplyToTheSupportedCanonicalDefault(JenkinsRule j) throws Exception {
        var definition = definition("a");
        definition.setMaxSelections(0);
        assertNull(definition.getDefaultParameterValue());
        assertThrows(hudson.AbortException.class, () -> definition.createValue((CLICommand) null, null));
        definition.setMaxSelections(-1);
        definition.setRequired(true);
        definition.setLegacyDefaultLiteral("unknown");
        assertNull(definition.getDefaultParameterValue());
        assertThrows(hudson.AbortException.class, () -> definition.createValue((CLICommand) null, null));
        definition.setRequired(false);
        var disabled = new Option("a");
        disabled.setDisabled(true);
        definition.setChoices(List.of(disabled, new Option("b")));
        definition.setLegacyDefaultLiteral("a");
        assertNull(definition.getDefaultParameterValue());
        assertThrows(hudson.AbortException.class, () -> definition.createValue((CLICommand) null, null));
    }

    @Test
    void queueEqualityIncludesLiteralAndNormalModeCannotUseIt(JenkinsRule j) {
        var first = definition("one").getDefaultParameterValue();
        var second = definition("two").getDefaultParameterValue();
        assertNotEquals(first, second);
        assertEquals(first, definition("one").getDefaultParameterValue());
        var ordinary = definition("one");
        ordinary.setStandardApiInputMode("repeated");
        assertFalse(
                DefinitionValidationService.validateStoredDefinition(ordinary).isValid());
        ordinary.setLegacyDefaultLiteral(null);
        assertFalse(ordinary.isValid(first));
    }
}
