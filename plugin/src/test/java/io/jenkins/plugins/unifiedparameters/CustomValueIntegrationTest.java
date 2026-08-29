package io.jenkins.plugins.unifiedparameters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import hudson.cli.CLICommandInvoker;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import io.jenkins.plugins.unifiedparameters.definition.UnifiedChoiceParameterDefinition;
import io.jenkins.plugins.unifiedparameters.definition.UnifiedChoiceParameterValue;
import io.jenkins.plugins.unifiedparameters.model.Option;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import net.sf.json.JSONObject;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.htmlunit.util.NameValuePair;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class CustomValueIntegrationTest {

    @Test
    void customValueHasStandardTypedAndCliParity(JenkinsRule jenkins) throws Exception {
        FreeStyleProject standard = customJob(jenkins, "custom-standard");
        Page response = postForm(jenkins, standard, List.of(new NameValuePair("RELEASE", "release-42")));
        assertEquals(201, response.getWebResponse().getStatusCode());
        jenkins.waitUntilNoActivity();
        assertValue(standard, "release-42");

        FreeStyleProject typed = customJob(jenkins, "custom-typed");
        JSONObject values = new JSONObject();
        values.put("RELEASE", "release-43");
        JSONObject body = new JSONObject();
        body.put("values", values);
        JenkinsRule.JSONWebResponse typedResponse =
                postJson(jenkins, typed.getUrl() + "unified-parameters/api/v1/build", body);
        assertEquals(201, typedResponse.getStatusCode());
        jenkins.waitUntilNoActivity();
        assertValue(typed, "release-43");

        FreeStyleProject cli = customJob(jenkins, "custom-cli");
        CLICommandInvoker.Result cliResult = new CLICommandInvoker(jenkins, "build")
                .invokeWithArgs(cli.getFullName(), "-p", "RELEASE=release-44", "-s");
        assertEquals(0, cliResult.returnCode(), cliResult.stderr());
        assertValue(cli, "release-44");
    }

    private static FreeStyleProject customJob(JenkinsRule jenkins, String name) throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject(name);
        UnifiedChoiceParameterDefinition definition = new UnifiedChoiceParameterDefinition("RELEASE");
        definition.setChoices(List.of(new Option("stable")));
        definition.setAllowCustomValue(true);
        definition.setCustomValuePattern("release-[0-9]+");
        definition.setCustomValueMaxLength(16);
        project.addProperty(new ParametersDefinitionProperty(definition));
        return project;
    }

    private static void assertValue(FreeStyleProject project, String expected) {
        UnifiedChoiceParameterValue value = assertInstanceOf(
                UnifiedChoiceParameterValue.class,
                project.getLastBuild().getAction(ParametersAction.class).getParameter("RELEASE"));
        assertEquals(expected, value.getValue());
    }

    private static Page postForm(JenkinsRule jenkins, FreeStyleProject project, List<NameValuePair> parameters)
            throws Exception {
        JenkinsRule.WebClient client = jenkins.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false)
                .withRedirectEnabled(false);
        WebRequest request = new WebRequest(
                new URL(jenkins.getURL(), project.getUrl() + "buildWithParameters?delay=0sec"), HttpMethod.POST);
        request.setRequestParameters(new ArrayList<>(parameters));
        client.addCrumb(request);
        return client.getPage(request);
    }

    private static JenkinsRule.JSONWebResponse postJson(JenkinsRule jenkins, String path, JSONObject body)
            throws Exception {
        JenkinsRule.WebClient client = jenkins.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false)
                .withRedirectEnabled(false);
        WebRequest request = new WebRequest(new URL(jenkins.getURL(), path), HttpMethod.POST);
        request.setAdditionalHeader("Content-Type", "application/json");
        request.setRequestBody(body.toString());
        hudson.security.csrf.CrumbIssuer issuer = jenkins.jenkins.getCrumbIssuer();
        if (issuer != null) {
            request.setAdditionalHeader(issuer.getCrumbRequestField(), issuer.getCrumb());
        }
        return new JenkinsRule.JSONWebResponse(client.loadWebResponse(request));
    }
}
