package io.jenkins.plugins.parameterfoundry.api;

import static org.junit.jupiter.api.Assertions.*;

import hudson.cli.CLICommand;
import hudson.cli.CLICommandInvoker;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition;
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterValue;
import io.jenkins.plugins.parameterfoundry.evaluation.EvaluationException;
import io.jenkins.plugins.parameterfoundry.model.Option;
import io.jenkins.plugins.parameterfoundry.provider.ProviderOperation;
import java.lang.reflect.Proxy;
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
class ExtendedChoiceSingleCompatibilityTest {
    private FoundryChoiceParameterDefinition definition() {
        var d = new FoundryChoiceParameterDefinition("SAMPLE");
        d.setChoices(List.of(new Option("a"), new Option("b")));
        d.setStandardApiInputMode("extended-choice");
        d.setDefaultMode("first");
        d.setLegacyDefaultLiteral("placeholder");
        return d;
    }

    private String scalar(hudson.model.ParameterValue value) {
        return value == null ? null : ((FoundryChoiceParameterValue) value).getScalarValue();
    }

    @Test
    void defaultsAndCliKeepSourceScalarsWhileUiDefaultsAreIndependent(JenkinsRule j) throws Exception {
        var d = definition();
        assertEquals("placeholder", scalar(d.getDefaultParameterValue()));
        assertTrue(d.isDefault("a"));
        assertEquals("placeholder", scalar(d.createValue((CLICommand) null, ",,")));
        assertEquals("b,a,a", scalar(d.createValue((CLICommand) null, " b ,missing,a,a")));
        assertEquals("", scalar(d.createValue((CLICommand) null, "unknown")));
        assertEquals("", scalar(d.createValue((CLICommand) null, "")));
        d.setLegacyDefaultLiteral(null);
        assertNull(d.getDefaultParameterValue());
        assertTrue(d.isDefault("a"));
    }

    @Test
    void standardRepeatedFieldsAndFormsUseDifferentSourceRules(JenkinsRule j) {
        var d = definition();
        String[] fields = {" b ", "a", "unknown", "a"};
        var request = (StaplerRequest2) Proxy.newProxyInstance(
                StaplerRequest2.class.getClassLoader(),
                new Class<?>[] {StaplerRequest2.class},
                (proxy, method, args) -> method.getName().equals("getParameterValues") ? fields : null);
        assertEquals("b,a,a", scalar(d.createValue(request)));
        for (String literal : List.of("unknown", " b,a ", "")) {
            var body = new JSONObject();
            body.put("value", literal);
            assertEquals(literal, scalar(d.createValue((StaplerRequest2) null, body)));
        }
        d.setDelimiter(";");
        assertEquals(
                "42;true;null",
                scalar(d.createValue((StaplerRequest2) null, JSONObject.fromObject("{value:[42,true,null]}"))));
        assertEquals("", scalar(d.createValue((StaplerRequest2) null, JSONObject.fromObject("{value:true}"))));
    }

    @Test
    void typedOmissionSharesSourceDefaultButExplicitStringsRemainStrict(JenkinsRule j) throws Exception {
        var d = definition();
        var job = j.createFreeStyleProject();
        job.addProperty(new ParametersDefinitionProperty(d));
        assertEquals(
                "placeholder",
                scalar(TypedParameterAdapterRegistry.decode(
                        job, d, false, null, Map.of(), null, ProviderOperation.VALIDATE)));
        assertEquals(
                "b",
                scalar(TypedParameterAdapterRegistry.decode(
                        job, d, true, "b", Map.of(), null, ProviderOperation.VALIDATE)));
        assertThrows(
                EvaluationException.class,
                () -> TypedParameterAdapterRegistry.decode(
                        job, d, true, "unknown", Map.of(), null, ProviderOperation.VALIDATE));
        assertThrows(
                EvaluationException.class,
                () -> TypedParameterAdapterRegistry.decode(
                        job, d, true, new JSONArray(), Map.of(), null, ProviderOperation.VALIDATE));
    }

    @Test
    void actualHttpBuildsPreserveOmissionRepeatedFieldsAndFormArrays(JenkinsRule j) throws Exception {
        var job = j.createFreeStyleProject("single-http");
        job.addProperty(new ParametersDefinitionProperty(definition()));
        j.jenkins.setCrumbIssuer(null);
        var request =
                new WebRequest(new URL(j.getURL(), job.getUrl() + "buildWithParameters?delay=0sec"), HttpMethod.POST);
        j.createWebClient().getPage(request);
        j.waitUntilNoActivity();
        assertEquals(
                "placeholder",
                scalar(job.getLastBuild().getAction(ParametersAction.class).getParameter("SAMPLE")));
        request.setRequestParameters(List.of(
                new NameValuePair("SAMPLE", " b "),
                new NameValuePair("SAMPLE", "a"),
                new NameValuePair("SAMPLE", "unknown")));
        j.createWebClient().getPage(request);
        j.waitUntilNoActivity();
        assertEquals(
                "b,a",
                scalar(job.getLastBuild().getAction(ParametersAction.class).getParameter("SAMPLE")));
        var formRequest = new WebRequest(new URL(j.getURL(), job.getUrl() + "build?delay=0sec"), HttpMethod.POST);
        formRequest.setRequestParameters(
                List.of(new NameValuePair("json", "{parameter:[{name:'SAMPLE',value:['unknown',42,true,null]}]}")));
        j.createWebClient().getPage(formRequest);
        j.waitUntilNoActivity();
        j.assertBuildStatusSuccess(job.getLastBuild());
        assertEquals(
                "unknown,42,true,null",
                scalar(job.getLastBuild().getAction(ParametersAction.class).getParameter("SAMPLE")));
        var d = definition();
        var disabled = new Option("a");
        disabled.setDisabled(true);
        d.setChoices(List.of(disabled, new Option("b")));
        assertFalse(
                io.jenkins.plugins.parameterfoundry.validation.DefinitionValidationService.validateStoredDefinition(d)
                        .isValid());
    }

    @Test
    void actualCliConfigReloadAndRebuildRetainHistoricalScalar(JenkinsRule j) throws Exception {
        var job = j.createFreeStyleProject("single-literal");
        job.addProperty(new ParametersDefinitionProperty(definition()));
        j.configRoundtrip(job);
        var configured = (FoundryChoiceParameterDefinition)
                job.getProperty(ParametersDefinitionProperty.class).getParameterDefinition("SAMPLE");
        assertEquals("extended-choice", configured.getStandardApiInputMode());
        assertEquals("placeholder", configured.getLegacyDefaultLiteral());
        var result = new CLICommandInvoker(j, "build").invokeWithArgs(job.getFullName(), "-f", "-p", "SAMPLE=b,a,a");
        assertEquals(0, result.returnCode(), result.stderr());
        j.waitUntilNoActivity();
        j.jenkins.reload();
        var restored = j.jenkins.getItemByFullName("single-literal", hudson.model.FreeStyleProject.class);
        var value = restored.getLastBuild().getAction(ParametersAction.class).getParameter("SAMPLE");
        assertEquals("b,a,a", scalar(value));
        var page = (HtmlPage) j.createWebClient()
                .withJavaScriptEnabled(false)
                .getPage(restored.getLastBuild(), "rebuild/parameterized");
        assertFalse(page.getByXPath("//input[@name='value' and @value='b,a,a']").isEmpty());
        var d = (FoundryChoiceParameterDefinition)
                restored.getProperty(ParametersDefinitionProperty.class).getParameterDefinition("SAMPLE");
        assertEquals("b,a,a", scalar(d.copyWithDefaultValue(value).getDefaultParameterValue()));
    }
}
