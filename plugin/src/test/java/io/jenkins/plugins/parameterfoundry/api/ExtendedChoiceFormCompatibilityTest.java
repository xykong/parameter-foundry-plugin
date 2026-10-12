package io.jenkins.plugins.parameterfoundry.api;

import static org.junit.jupiter.api.Assertions.*;

import hudson.EnvVars;
import hudson.cli.CLICommand;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition;
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterValue;
import io.jenkins.plugins.parameterfoundry.evaluation.EvaluationException;
import io.jenkins.plugins.parameterfoundry.model.Option;
import io.jenkins.plugins.parameterfoundry.provider.ProviderOperation;
import io.jenkins.plugins.parameterfoundry.validation.DefinitionValidationService;
import java.net.URL;
import java.util.List;
import java.util.Map;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.util.NameValuePair;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.StaplerRequest2;

@WithJenkins
class ExtendedChoiceFormCompatibilityTest {
    private FoundryChoiceParameterDefinition definition() {
        var d = new FoundryChoiceParameterDefinition("SAMPLE");
        d.setSelectionMode("multiple");
        d.setChoices(List.of(new Option("a"), new Option("b")));
        d.setStandardApiInputMode("extended-choice");
        d.setEnvironmentExportOrder("submitted");
        d.setDuplicatePolicy("deduplicate");
        return d;
    }

    private FoundryChoiceParameterValue form(FoundryChoiceParameterDefinition d, Object raw) {
        var submitted = new JSONObject();
        submitted.put("name", "SAMPLE");
        submitted.put("value", raw);
        return (FoundryChoiceParameterValue) d.createValue((StaplerRequest2) null, submitted);
    }

    private String export(hudson.model.ParameterValue value) {
        var environment = new EnvVars();
        value.buildEnvironment(null, environment);
        return environment.get("SAMPLE");
    }

    @Test
    void preMigrationCoreStringHistoryKeepsEditableLiteralWithoutChangingDefaults(JenkinsRule j) {
        for (String mode : List.of("single", "multiple")) {
            var d = definition();
            d.setSelectionMode(mode);
            d.setDefaultMode("explicit");
            d.setDefaultValue("a");
            d.setDefaultValues(List.of("a"));
            d.setLegacyDefaultLiteral("b");
            for (String literal : List.of("", " ", "a", " b,unknown,a,b ", "quote\"&<value>", "中文")) {
                var historical = new hudson.model.StringParameterValue("SAMPLE", literal);
                var copy = (FoundryChoiceParameterDefinition) d.copyWithDefaultValue(historical);
                assertNotSame(d, copy);
                assertTrue(copy.isLegacyRebuildInput());
                assertEquals(literal, copy.getLegacyRebuildValue());
                assertEquals(literal, export(form(copy, literal)));
                if (literal.isBlank()) assertNull(copy.getDefaultParameterValue());
                else assertEquals(literal, export(copy.getDefaultParameterValue()));
                assertEquals("b", d.getLegacyDefaultLiteral());
                assertEquals("a", d.getDefaultValue());
                assertEquals(List.of("a"), d.getDefaultValues());
                var persisted = (FoundryChoiceParameterDefinition)
                        jenkins.model.Jenkins.XSTREAM2.fromXML(jenkins.model.Jenkins.XSTREAM2.toXML(copy));
                assertFalse(persisted.isLegacyRebuildInput());
                assertNull(persisted.getLegacyRebuildValue());
            }
        }
    }

    @Test
    void actualRebuildFormPrefillsPreMigrationCoreStringHistory(JenkinsRule j) throws Exception {
        String literal = " b,unknown,a,b ";
        var job = j.createFreeStyleProject("source-string-history");
        job.addProperty(new ParametersDefinitionProperty(new hudson.model.StringParameterDefinition("SAMPLE", "")));
        var run = job.scheduleBuild2(0, new ParametersAction(new hudson.model.StringParameterValue("SAMPLE", literal)))
                .get();
        j.assertBuildStatusSuccess(run);
        job.removeProperty(ParametersDefinitionProperty.class);
        job.addProperty(new ParametersDefinitionProperty(definition()));
        var page = (HtmlPage) j.createWebClient().withJavaScriptEnabled(false).getPage(run, "rebuild/parameterized");
        assertFalse(page.getByXPath("//input[@name='value' and @value=' b,unknown,a,b ']")
                .isEmpty());
    }

    @Test
    void formStringsRemainLiteralAcrossOutputDelimiters(JenkinsRule j) {
        for (String delimiter : List.of(",", ";")) {
            var d = definition();
            d.setDelimiter(delimiter);
            for (String literal : List.of("unknown", " b , a ", "b,a,a", "a;;unknown", "", " ")) {
                var value = form(d, literal);
                assertEquals(literal, export(value));
                assertEquals(literal, value.getLegacyExportLiteral());
                assertTrue(d.isValid(value));
                assertEquals(2, d.getChoices().size());
            }
        }
    }

    @Test
    void arraysJoinWithoutDiscardingUnknownEmptyOrNonStringElements(JenkinsRule j) {
        var d = definition();
        var value = form(d, JSONArray.fromObject("['b','','unknown','a','a']"));
        assertEquals("b,,unknown,a,a", export(value));
        assertEquals(List.of("a", "b"), value.getValues());
        d.setDelimiter(";");
        value = form(d, JSONArray.fromObject("[42,true,null,{key:'v'}]"));
        assertEquals("42;true;null;{\"key\":\"v\"}", export(value));
        assertEquals(List.of(), value.getValues());
        assertEquals("", export(form(d, new JSONArray())));
    }

    @Test
    void missingAndScalarValuesAreExplicitlyEmptyInsteadOfDefaults(JenkinsRule j) {
        var d = definition();
        d.setLegacyDefaultLiteral("b,a");
        for (String json : List.of("{}", "{value:null}", "{value:true}", "{value:42}", "{value:{key:'v'}}")) {
            var value = d.createValue((StaplerRequest2) null, JSONObject.fromObject(json));
            assertNotNull(value);
            assertEquals("", export(value));
        }
    }

    @Test
    void rawEqualityMatchesAcrossDefaultCliFormAndTypedSubmissions(JenkinsRule j) throws Exception {
        var d = definition();
        d.setLegacyDefaultLiteral("b,a");
        var job = j.createFreeStyleProject();
        job.addProperty(new ParametersDefinitionProperty(d));
        var defaultValue = d.getDefaultParameterValue();
        var cli = d.createValue((CLICommand) null, "b,a");
        var submitted = form(d, "b,a");
        assertEquals(defaultValue, cli);
        assertEquals(cli, submitted);
        assertEquals(defaultValue.hashCode(), submitted.hashCode());
        assertNotEquals(submitted, form(d, "a,b"));
        assertNotEquals(form(d, "unknown"), form(d, "different"));
        var typed = TypedParameterAdapterRegistry.decode(
                job, d, true, JSONArray.fromObject("['b','a']"), Map.of(), null, ProviderOperation.VALIDATE);
        assertEquals(submitted, typed);
        assertEquals("b,a", export(typed));
        d.setDelimiter(";");
        // The same source literal can have different canonical views across entry points.
        assertEquals(d.getDefaultParameterValue(), form(d, "b,a"));
        assertEquals(d.getDefaultParameterValue().hashCode(), form(d, "b,a").hashCode());
    }

    @Test
    void ordinaryAndTypedInputsStayStrictAndContradictoryExportPoliciesFail(JenkinsRule j) throws Exception {
        var d = definition();
        var legacy = form(d, "unknown,a");
        var job = j.createFreeStyleProject();
        job.addProperty(new ParametersDefinitionProperty(d));
        assertThrows(
                EvaluationException.class,
                () -> TypedParameterAdapterRegistry.decode(
                        job, d, true, JSONArray.fromObject("['unknown']"), Map.of(), null, ProviderOperation.VALIDATE));
        d.setStandardApiInputMode("repeated");
        assertFalse(d.isValid(legacy));
        assertThrows(RuntimeException.class, () -> form(d, "unknown"));
        d.setStandardApiInputMode("extended-choice");
        d.setEnvironmentExport("json");
        assertFalse(DefinitionValidationService.validateStoredDefinition(d).isValid());
        d.setEnvironmentExport("delimited");
        d.setEnvironmentExportOrder("canonical");
        assertFalse(DefinitionValidationService.validateStoredDefinition(d).isValid());
    }

    @Test
    void actualFormBuildPersistsLiteralAndRebuildKeepsEditableHistoricalInput(JenkinsRule j) throws Exception {
        var job = j.createFreeStyleProject("literal-form");
        job.addProperty(new ParametersDefinitionProperty(definition()));
        job.getBuildersList().add(new hudson.tasks.Shell("printf '%s' \"$SAMPLE\" > literal.txt"));
        j.jenkins.setCrumbIssuer(null);
        String literal = " b,unknown,a,b ";
        var request = new WebRequest(new URL(j.getURL(), job.getUrl() + "build?delay=0sec"), HttpMethod.POST);
        var body = new JSONObject();
        body.put("parameter", JSONArray.fromObject(List.of(Map.of("name", "SAMPLE", "value", literal))));
        request.setRequestParameters(List.of(new NameValuePair("json", body.toString())));
        j.createWebClient().getPage(request);
        j.waitUntilNoActivity();
        j.assertBuildStatusSuccess(job.getLastBuild());
        assertEquals(
                literal, job.getLastBuild().getWorkspace().child("literal.txt").readToString());
        j.jenkins.reload();
        var restored = j.jenkins.getItemByFullName("literal-form", hudson.model.FreeStyleProject.class);
        var historical =
                restored.getLastBuild().getAction(ParametersAction.class).getParameter("SAMPLE");
        assertEquals(literal, export(historical));
        var page = (HtmlPage) j.createWebClient()
                .withJavaScriptEnabled(false)
                .getPage(restored.getLastBuild(), "rebuild/parameterized");
        assertFalse(page.getByXPath("//input[@name='value' and @value=' b,unknown,a,b ']")
                .isEmpty());
        var d = (FoundryChoiceParameterDefinition)
                restored.getProperty(ParametersDefinitionProperty.class).getParameterDefinition("SAMPLE");
        var blankHistory = form(d, " ");
        var copy = (FoundryChoiceParameterDefinition) d.copyWithDefaultValue(blankHistory);
        assertTrue(copy.isLegacyRebuildInput());
        assertEquals(" ", copy.getLegacyRebuildValue());
    }

    @Test
    void realPreviousSnapshotXmlRetainsExportEqualityAndRebuildLiteral(JenkinsRule j) throws Exception {
        // Captured from a synthetic build made by the verified 0.8.291 HPI, not generated by this version.
        FoundryChoiceParameterValue historical;
        try (var input = getClass()
                .getResourceAsStream("/io/jenkins/plugins/parameterfoundry/extended-choice-default-0.8.291.xml")) {
            assertNotNull(input);
            historical = (FoundryChoiceParameterValue) jenkins.model.Jenkins.XSTREAM2.fromXML(input);
        }
        String literal = " gamma,unknown,alpha,gamma ";
        var d = new FoundryChoiceParameterDefinition("TARGETS");
        d.setSelectionMode("multiple");
        d.setChoices(List.of(new Option("alpha"), new Option("gamma")));
        d.setStandardApiInputMode("extended-choice");
        d.setEnvironmentExportOrder("submitted");
        d.setDuplicatePolicy("deduplicate");
        var body = new JSONObject();
        body.put("value", literal);
        var current = d.createValue((StaplerRequest2) null, body);
        var environment = new EnvVars();
        historical.buildEnvironment(null, environment);
        assertEquals(literal, environment.get("TARGETS"));
        assertEquals(historical, current);
        assertEquals(historical.hashCode(), current.hashCode());
        var copy = (FoundryChoiceParameterDefinition) d.copyWithDefaultValue(historical);
        assertTrue(copy.isLegacyRebuildInput());
        assertEquals(literal, copy.getLegacyRebuildValue());
    }

    @Test
    void explicitCanonicalConstraintsStillApplyAndOrdinaryTypedOrderIsHonored(JenkinsRule j) throws Exception {
        var d = definition();
        d.setRequired(true);
        assertThrows(RuntimeException.class, () -> form(d, "unknown"));
        d.setRequired(false);
        d.setMaxSelections(0);
        assertThrows(RuntimeException.class, () -> form(d, "a,unknown"));
        d.setMaxSelections(-1);
        d.setStandardApiInputMode("repeated");
        d.setEnvironmentExport("json");
        var job = j.createFreeStyleProject();
        job.addProperty(new ParametersDefinitionProperty(d));
        var value = TypedParameterAdapterRegistry.decode(
                job, d, true, JSONArray.fromObject("['b','a']"), Map.of(), null, ProviderOperation.VALIDATE);
        assertEquals("[\"b\",\"a\"]", export(value));
    }
}
