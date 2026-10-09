package io.jenkins.plugins.parameterfoundry;

import static org.junit.jupiter.api.Assertions.*;

import hudson.EnvVars;
import hudson.cli.CLICommandInvoker;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition;
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterValue;
import io.jenkins.plugins.parameterfoundry.model.Option;
import io.jenkins.plugins.parameterfoundry.validation.DefinitionValidationService;
import java.net.URL;
import java.util.List;
import net.sf.json.JSONObject;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.util.NameValuePair;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ExtendedChoiceTransportCompatibilityTest {
    private FoundryChoiceParameterDefinition definition() {
        var d = new FoundryChoiceParameterDefinition("CLOUDS");
        d.setSelectionMode("multiple");
        d.setChoices(List.of(new Option("he"), new Option("tx"), new Option("hw"), new Option("volc")));
        d.setStandardApiInputMode("extended-choice");
        d.setEnvironmentExportOrder("submitted");
        d.setDuplicatePolicy("deduplicate");
        return d;
    }

    @Test
    void cliFiltersUnknownsTrimsAndRetainsRepeatedExport(JenkinsRule j) throws Exception {
        var job = j.createFreeStyleProject("legacy-cli");
        job.addProperty(new ParametersDefinitionProperty(definition()));
        var result = new CLICommandInvoker(j, "build")
                .invokeWithArgs(job.getFullName(), "-f", "-p", "CLOUDS=volc, he,missing,he");
        assertEquals(0, result.returnCode(), result.stderr());
        j.waitUntilNoActivity();
        var value = (FoundryChoiceParameterValue)
                job.getLastBuild().getAction(ParametersAction.class).getParameter("CLOUDS");
        assertEquals(List.of("he", "volc"), value.getValues());
        assertEquals(List.of("volc", "he", "he"), value.getSubmittedValues());
        var environment = new EnvVars();
        value.buildEnvironment(job.getLastBuild(), environment);
        assertEquals("volc,he,he", environment.get("CLOUDS"));
        j.jenkins.reload();
        var reloaded = (FoundryChoiceParameterValue) j.jenkins
                .getItemByFullName("legacy-cli", hudson.model.FreeStyleProject.class)
                .getLastBuild()
                .getAction(ParametersAction.class)
                .getParameter("CLOUDS");
        assertEquals(List.of("volc", "he", "he"), reloaded.getSubmittedValues());
    }

    @Test
    void standardRequestsUseRepeatedFieldsAndDoNotExpandCommaStrings(JenkinsRule j) throws Exception {
        var job = j.createFreeStyleProject("legacy-rest");
        job.addProperty(new ParametersDefinitionProperty(definition()));
        j.jenkins.setCrumbIssuer(null);
        var request =
                new WebRequest(new URL(j.getURL(), job.getUrl() + "buildWithParameters?delay=0sec"), HttpMethod.POST);
        request.setRequestParameters(List.of(new NameValuePair("CLOUDS", "volc,he")));
        j.createWebClient().getPage(request);
        j.waitUntilNoActivity();
        var value = (FoundryChoiceParameterValue)
                job.getLastBuild().getAction(ParametersAction.class).getParameter("CLOUDS");
        assertEquals(List.of(), value.getValues());
        request.setRequestParameters(List.of(
                new NameValuePair("CLOUDS", "volc"),
                new NameValuePair("CLOUDS", " he "),
                new NameValuePair("CLOUDS", "missing"),
                new NameValuePair("CLOUDS", "he")));
        j.createWebClient().getPage(request);
        j.waitUntilNoActivity();
        value = (FoundryChoiceParameterValue)
                job.getLastBuild().getAction(ParametersAction.class).getParameter("CLOUDS");
        assertEquals(List.of("volc", "he", "he"), value.getSubmittedValues());
    }

    @Test
    void legacyFormJsonAcceptsDelimitedStringAndArray(JenkinsRule j) {
        var d = definition();
        var value = (FoundryChoiceParameterValue) d.createValue(
                (org.kohsuke.stapler.StaplerRequest2) null,
                JSONObject.fromObject("{name:'CLOUDS',value:'volc,he,he'}"));
        assertEquals(List.of("he", "volc"), value.getValues());
        assertEquals(List.of("volc", "he", "he"), value.getSubmittedValues());
        value = (FoundryChoiceParameterValue) d.createValue(
                (org.kohsuke.stapler.StaplerRequest2) null,
                JSONObject.fromObject("{name:'CLOUDS',value:['volc','he','he']}"));
        assertEquals(List.of("volc", "he", "he"), value.getSubmittedValues());
    }

    @Test
    void compatibilityIsRestrictedToStaticMultipleChoices(JenkinsRule j) {
        var d = definition();
        assertTrue(DefinitionValidationService.validateStoredDefinition(d).isValid());
        d.setProviderId("external");
        var validation = DefinitionValidationService.validateStoredDefinition(d);
        assertFalse(validation.isValid());
        assertTrue(validation.summarize().contains("Extended Choice transport compatibility"));
    }

    @Test
    void legacyFormScalarTypesDoNotBecomeSelections(JenkinsRule j) {
        var d = definition();
        d.setChoices(List.of(new Option("null"), new Option("true"), new Option("42")));
        for (String raw : List.of("null", "true", "42", "{nested:'value'}")) {
            var submitted = JSONObject.fromObject("{name:'CLOUDS',value:" + raw + "}");
            var value =
                    (FoundryChoiceParameterValue) d.createValue((org.kohsuke.stapler.StaplerRequest2) null, submitted);
            assertEquals(List.of(), value.getValues());
        }
    }

    @Test
    void configurationRoundTripPreservesCompatibilityDuplicatePolicy(JenkinsRule j) throws Exception {
        var job = j.createFreeStyleProject("compatibility-config");
        job.addProperty(new ParametersDefinitionProperty(definition()));
        j.configRoundtrip(job);
        var configured = (FoundryChoiceParameterDefinition)
                job.getProperty(ParametersDefinitionProperty.class).getParameterDefinition("CLOUDS");
        assertEquals("extended-choice", configured.getStandardApiInputMode());
        assertEquals("deduplicate", configured.getDuplicatePolicy());
        assertEquals("submitted", configured.getEnvironmentExportOrder());
    }
}
