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
import java.lang.reflect.Proxy;
import java.net.URL;
import java.util.List;
import java.util.Map;
import net.sf.json.JSONArray;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.util.NameValuePair;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.StaplerRequest2;

@WithJenkins
class ExtendedChoiceEmptyTransportTest {
    private FoundryChoiceParameterDefinition definition(boolean multiple) {
        var d = new FoundryChoiceParameterDefinition("SAMPLE");
        d.setSelectionMode(multiple ? "multiple" : "single");
        d.setChoices(List.of(new Option("a"), new Option("b")));
        d.setStandardApiInputMode("extended-choice");
        d.setEnvironmentExportOrder("submitted");
        d.setDuplicatePolicy("deduplicate");
        d.setLegacyEmptyChoiceAllowed(true);
        d.setLegacyDefaultLiteral("placeholder");
        return d;
    }

    private String export(hudson.model.ParameterValue value) {
        var env = new EnvVars();
        value.buildEnvironment(null, env);
        return env.get("SAMPLE");
    }

    @Test
    void cliAndStandardPreserveEmptyTokenPositionsForBothCardinalities(JenkinsRule j) throws Exception {
        for (boolean multiple : new boolean[] {true, false}) {
            var d = definition(multiple);
            var cli = d.createValue((CLICommand) null, ",a,,b,");
            assertEquals(",a,,b", export(cli));
            String[] fields = {"", "a", "", "b", ""};
            var request = (StaplerRequest2) Proxy.newProxyInstance(
                    StaplerRequest2.class.getClassLoader(),
                    new Class<?>[] {StaplerRequest2.class},
                    (proxy, method, args) ->
                            method.getName().equals("getParameterValues") && "SAMPLE".equals(args[0]) ? fields : null);
            var standard = d.createValue(request);
            assertEquals(",a,,b,", export(standard));
            assertEquals("placeholder", export(d.createValue((CLICommand) null, ",,")));
            assertEquals("", export(d.createValue((CLICommand) null, "")));
            if (multiple) {
                assertEquals(List.of("a", "b"), ((FoundryChoiceParameterValue) standard).getValues());
                assertEquals(
                        List.of("", "a", "", "b", ""), ((FoundryChoiceParameterValue) standard).getSubmittedValues());
            }
            assertEquals(
                    List.of("a", "b"),
                    d.getChoices().stream().map(Option::getValue).toList());
            d.setLegacyEmptyChoiceAllowed(false);
            assertEquals("a,b", export(d.createValue((CLICommand) null, ",a,,b,")));
        }
    }

    @Test
    void actualRepeatedHttpBuildReloadAndConfigurationKeepTransportFlag(JenkinsRule j) throws Exception {
        var job = j.createFreeStyleProject("empty-fields");
        job.addProperty(new ParametersDefinitionProperty(definition(true)));
        j.configRoundtrip(job);
        var configured = (FoundryChoiceParameterDefinition)
                job.getProperty(ParametersDefinitionProperty.class).getParameterDefinition("SAMPLE");
        assertTrue(configured.isLegacyEmptyChoiceAllowed());
        j.jenkins.setCrumbIssuer(null);
        var request =
                new WebRequest(new URL(j.getURL(), job.getUrl() + "buildWithParameters?delay=0sec"), HttpMethod.POST);
        request.setRequestParameters(List.of(
                new NameValuePair("SAMPLE", ""), new NameValuePair("SAMPLE", "a"), new NameValuePair("SAMPLE", "")));
        j.createWebClient().getPage(request);
        j.waitUntilNoActivity();
        j.assertBuildStatusSuccess(job.getLastBuild());
        j.jenkins.reload();
        var restored = j.jenkins.getItemByFullName("empty-fields", hudson.model.FreeStyleProject.class);
        var value = restored.getLastBuild().getAction(ParametersAction.class).getParameter("SAMPLE");
        assertEquals(",a,", export(value));
        assertTrue(((FoundryChoiceParameterDefinition)
                        restored.getProperty(ParametersDefinitionProperty.class).getParameterDefinition("SAMPLE"))
                .isLegacyEmptyChoiceAllowed());
    }

    @Test
    void emptyTokensDoNotBypassCanonicalConstraintsAndSurviveXmlReload(JenkinsRule j) throws Exception {
        var d = definition(true);
        d.setRequired(true);
        assertThrows(hudson.AbortException.class, () -> d.createValue((CLICommand) null, ""));
        d.setRequired(false);
        d.setMinSelections(1);
        assertThrows(hudson.AbortException.class, () -> d.createValue((CLICommand) null, ""));
        d.setMinSelections(0);
        d.setDuplicatePolicy("reject");
        assertThrows(hudson.AbortException.class, () -> d.createValue((CLICommand) null, ",a,,a"));
        d.setDuplicatePolicy("deduplicate");
        var value = (FoundryChoiceParameterValue) d.createValue((CLICommand) null, ",a,,a");
        var reloaded = (FoundryChoiceParameterValue)
                jenkins.model.Jenkins.XSTREAM2.fromXML(jenkins.model.Jenkins.XSTREAM2.toXML(value));
        assertEquals(",a,,a", export(reloaded));
        assertEquals(List.of("", "a", "", "a"), reloaded.getSubmittedValues());
        assertEquals(List.of("a"), reloaded.getValues());
    }

    @Test
    void ordinaryAndTypedExplicitValuesRemainStrict(JenkinsRule j) throws Exception {
        var d = definition(true);
        var job = j.createFreeStyleProject();
        job.addProperty(new ParametersDefinitionProperty(d));
        assertThrows(
                EvaluationException.class,
                () -> TypedParameterAdapterRegistry.decode(
                        job, d, true, JSONArray.fromObject("['','a']"), Map.of(), null, ProviderOperation.VALIDATE));
        d.setStandardApiInputMode("repeated");
        d.setLegacyDefaultLiteral(null);
        assertFalse(DefinitionValidationService.validateStoredDefinition(d).isValid());
        d.setLegacyEmptyChoiceAllowed(false);
        assertTrue(DefinitionValidationService.validateStoredDefinition(d).isValid());
    }
}
