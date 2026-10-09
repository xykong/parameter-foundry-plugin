package io.jenkins.plugins.parameterfoundry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.FileParameterDefinition;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.User;
import io.jenkins.plugins.parameterfoundry.definition.AbstractFoundryParameterDefinition;
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition;
import io.jenkins.plugins.parameterfoundry.model.Option;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.htmlunit.util.NameValuePair;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ApiSecurityIntegrationTest {

    @Test
    void typedAndConfigurationEndpointsEnforceThePermissionMatrix(JenkinsRule jenkins) throws Exception {
        FreeStyleProject project = parameterizedProject(jenkins, "permissions");
        configureSecurity(jenkins, project);

        JenkinsRule.WebClient anonymous = client(jenkins, null);
        JenkinsRule.WebClient reader = client(jenkins, "reader");
        JenkinsRule.WebClient builder = client(jenkins, "builder");
        JenkinsRule.WebClient configurator = client(jenkins, "configurator");
        JenkinsRule.WebClient administrator = client(jenkins, "administrator");
        String api = project.getUrl() + "parameter-foundry/api/v1/";

        assertEquals(403, get(anonymous, jenkins, api + "schema").getStatusCode(), "anonymous schema");
        assertEquals(403, get(reader, jenkins, api + "schema").getStatusCode(), "reader schema");
        assertEquals(403, get(configurator, jenkins, api + "schema").getStatusCode(), "configurator schema");
        assertEquals(200, get(builder, jenkins, api + "schema").getStatusCode(), "builder schema");
        assertEquals(200, get(administrator, jenkins, api + "schema").getStatusCode(), "administrator schema");

        JSONObject resolve = new JSONObject();
        resolve.put("parameter", "TARGETS");
        assertEquals(403, postJson(reader, jenkins, api + "resolve", resolve).getStatusCode());
        assertEquals(
                403, postJson(configurator, jenkins, api + "resolve", resolve).getStatusCode());
        assertEquals(200, postJson(builder, jenkins, api + "resolve", resolve).getStatusCode());

        JSONObject values = new JSONObject();
        values.put("TARGETS", JSONArray.fromObject(List.of("android")));
        JSONObject validate = new JSONObject();
        validate.put("values", values);
        assertEquals(403, postJson(reader, jenkins, api + "validate", validate).getStatusCode());
        assertEquals(
                403, postJson(configurator, jenkins, api + "validate", validate).getStatusCode());
        assertEquals(200, postJson(builder, jenkins, api + "validate", validate).getStatusCode());

        JSONObject invalidValues = new JSONObject();
        invalidValues.put("TARGETS", JSONArray.fromObject(List.of("unknown")));
        JSONObject build = new JSONObject();
        build.put("values", invalidValues);
        int queueLength = jenkins.jenkins.getQueue().getItems().length;
        assertEquals(403, postJson(reader, jenkins, api + "build", build).getStatusCode());
        assertEquals(403, postJson(configurator, jenkins, api + "build", build).getStatusCode());
        assertEquals(400, postJson(builder, jenkins, api + "build", build).getStatusCode());
        assertEquals(queueLength, jenkins.jenkins.getQueue().getItems().length);

        assertEquals(403, get(reader, jenkins, api + "providers/health").getStatusCode());
        assertEquals(403, get(builder, jenkins, api + "providers/health").getStatusCode());
        assertEquals(200, get(configurator, jenkins, api + "providers/health").getStatusCode());
        assertEquals(200, get(administrator, jenkins, api + "providers/health").getStatusCode());

        JSONObject importBody = new JSONObject();
        importBody.put("content", "- value: android");
        assertEquals(
                403,
                postJson(reader, jenkins, api + "import/preview", importBody).getStatusCode());
        assertEquals(
                403,
                postJson(builder, jenkins, api + "import/preview", importBody).getStatusCode());
        assertEquals(
                200,
                postJson(configurator, jenkins, api + "import/preview", importBody)
                        .getStatusCode());
        assertEquals(
                200,
                postJson(administrator, jenkins, api + "import/preview", importBody)
                        .getStatusCode());
    }

    @Test
    void apiTokenRemoteAndWebhookStylePostsUseNormalValidationWithoutCrumbs(JenkinsRule jenkins) throws Exception {
        FreeStyleProject project = parameterizedProject(jenkins, "remote");
        jenkins.jenkins.setSecurityRealm(jenkins.createDummySecurityRealm());
        MockAuthorizationStrategy authorization = new MockAuthorizationStrategy();
        authorization.grant(Jenkins.READ).onRoot().to("remote");
        authorization.grant(Item.READ, Item.BUILD).onItems(project).to("remote");
        jenkins.jenkins.setAuthorizationStrategy(authorization);
        User remote = User.getById("remote", true);

        JenkinsRule.WebClient client = jenkins.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false)
                .withRedirectEnabled(false)
                .withBasicApiToken(remote);
        WebRequest request = new WebRequest(
                new URL(jenkins.getURL(), project.getUrl() + "buildWithParameters?delay=0sec"), HttpMethod.POST);
        request.setAdditionalHeader("User-Agent", "Synthetic-Webhook/1.0");
        request.setRequestParameters(new ArrayList<>(List.of(
                new NameValuePair(AbstractFoundryParameterDefinition.PRESENCE_FIELD, "TARGETS"),
                new NameValuePair("TARGETS", "web"),
                new NameValuePair("TARGETS", "android"))));

        Page response = client.getPage(request);

        assertEquals(201, response.getWebResponse().getStatusCode());
        jenkins.waitUntilNoActivity();
        assertEquals(
                List.of("android", "web"),
                project.getLastBuild()
                        .getAction(hudson.model.ParametersAction.class)
                        .getParameter("TARGETS")
                        .getValue());
    }

    @Test
    void typedApiRejectsMalformedUnknownAndOversizedRequestsWithoutQueueing(JenkinsRule jenkins) throws Exception {
        FreeStyleProject project = parameterizedProject(jenkins, "request-contract");
        int queueLength = jenkins.jenkins.getQueue().getItems().length;
        String api = project.getUrl() + "parameter-foundry/api/v1/build";

        JenkinsRule.JSONWebResponse unknown =
                postRawJson(jenkins.createWebClient(), jenkins, api, "{\"values\":{},\"debug\":true}");
        assertEquals(400, unknown.getStatusCode());
        assertTrue(unknown.getJSONObject().toString().contains("MALFORMED_INPUT"));

        JenkinsRule.JSONWebResponse malformed = postRawJson(jenkins.createWebClient(), jenkins, api, "{\"values\":");
        assertEquals(400, malformed.getStatusCode());
        assertTrue(malformed.getJSONObject().toString().contains("MALFORMED_INPUT"));

        String oversized = "{\"values\":{},\"padding\":\"" + "x".repeat(1024 * 1024) + "\"}";
        JenkinsRule.JSONWebResponse tooLarge = postRawJson(jenkins.createWebClient(), jenkins, api, oversized);
        assertEquals(413, tooLarge.getStatusCode());
        assertTrue(tooLarge.getJSONObject().toString().contains("REQUEST_TOO_LARGE"));
        assertEquals(queueLength, jenkins.jenkins.getQueue().getItems().length);
    }

    @Test
    void foundryBinaryDefinitionsRejectTypedJsonWithoutQueueing(JenkinsRule jenkins) throws Exception {
        var definitions = List.of(
                new io.jenkins.plugins.parameterfoundry.companion.file.FoundryBase64FileParameterDefinition("SMALL"),
                new io.jenkins.plugins.parameterfoundry.companion.file.FoundryStashedFileParameterDefinition("LARGE"),
                new io.jenkins.plugins.parameterfoundry.companion.file.FoundryFileParameterDefinition("WORKSPACE"));
        for (var definition : definitions) {
            var project = jenkins.createFreeStyleProject("binary-" + definition.getName());
            project.addProperty(new ParametersDefinitionProperty(definition));
            var schema = jenkins.createWebClient().getJSON(project.getUrl() + "parameter-foundry/api/v1/schema");
            assertEquals(
                    false,
                    schema.getJSONObject()
                            .getJSONArray("parameters")
                            .getJSONObject(0)
                            .getBoolean("supported"));
            assertEquals(
                    "parameter-foundry",
                    schema.getJSONObject()
                            .getJSONArray("parameters")
                            .getJSONObject(0)
                            .getString("owner"));
            JSONObject body = new JSONObject();
            body.put("values", new JSONObject());
            var response = postJson(
                    jenkins.createWebClient(), jenkins, project.getUrl() + "parameter-foundry/api/v1/build", body);
            assertEquals(409, response.getStatusCode());
            assertTrue(response.getJSONObject().toString().contains("UNSUPPORTED_DEFINITION"));
            assertEquals(0, project.getBuilds().size());
            assertEquals(0, jenkins.jenkins.getQueue().getItems().length);
        }
    }

    @Test
    void nativeFileParametersRemainAvailableWhileTypedJsonFailsExplicitly(JenkinsRule jenkins) throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject("native-file-compatibility");
        FoundryChoiceParameterDefinition targets = MultipleChoiceFixtures.multipleChoice("TARGETS");
        targets.setChoices(List.of(new Option("android"), new Option("ios")));
        project.addProperty(new ParametersDefinitionProperty(new FileParameterDefinition("UPLOAD"), targets));

        JenkinsRule.JSONWebResponse schema =
                jenkins.createWebClient().getJSON(project.getUrl() + "parameter-foundry/api/v1/schema");
        JSONObject upload = schema.getJSONObject().getJSONArray("parameters").getJSONObject(0);
        assertEquals(false, upload.getBoolean("supported"));

        JSONObject values = new JSONObject();
        values.put("TARGETS", JSONArray.fromObject(List.of("android")));
        JSONObject body = new JSONObject();
        body.put("values", values);
        JenkinsRule.JSONWebResponse typed = postJson(
                jenkins.createWebClient(), jenkins, project.getUrl() + "parameter-foundry/api/v1/validate", body);
        assertEquals(409, typed.getStatusCode());
        assertTrue(typed.getJSONObject().toString().contains("UNSUPPORTED_DEFINITION"));

        jenkins.configRoundtrip(project);
        assertTrue(
                project.getProperty(ParametersDefinitionProperty.class).getParameterDefinition("UPLOAD")
                        instanceof FileParameterDefinition);
    }

    private static FreeStyleProject parameterizedProject(JenkinsRule jenkins, String name) throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject(name);
        FoundryChoiceParameterDefinition definition = MultipleChoiceFixtures.multipleChoice("TARGETS");
        definition.setChoices(List.of(new Option("web"), new Option("android"), new Option("ios")));
        definition.setDefaultMode("explicit");
        definition.setDefaultValues(List.of("android"));
        project.addProperty(new ParametersDefinitionProperty(definition));
        return project;
    }

    private static void configureSecurity(JenkinsRule jenkins, FreeStyleProject project) {
        jenkins.jenkins.setSecurityRealm(jenkins.createDummySecurityRealm());
        MockAuthorizationStrategy authorization = new MockAuthorizationStrategy();
        authorization.grant(Jenkins.READ).onRoot().to("reader", "builder", "configurator", "administrator");
        authorization.grant(Item.READ).onItems(project).to("reader", "builder", "configurator", "administrator");
        authorization.grant(Item.BUILD).onItems(project).to("builder", "administrator");
        authorization.grant(Item.CONFIGURE).onItems(project).to("configurator", "administrator");
        authorization.grant(Jenkins.ADMINISTER).onRoot().to("administrator");
        jenkins.jenkins.setAuthorizationStrategy(authorization);
    }

    private static JenkinsRule.WebClient client(JenkinsRule jenkins, String user) throws Exception {
        JenkinsRule.WebClient client = jenkins.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false)
                .withRedirectEnabled(false);
        return user == null ? client : client.login(user);
    }

    private static WebResponse get(JenkinsRule.WebClient client, JenkinsRule jenkins, String path) throws Exception {
        return client.loadWebResponse(new WebRequest(new URL(jenkins.getURL(), path), HttpMethod.GET));
    }

    private static JenkinsRule.JSONWebResponse postJson(
            JenkinsRule.WebClient client, JenkinsRule jenkins, String path, JSONObject body) throws Exception {
        return postRawJson(client, jenkins, path, body.toString());
    }

    private static JenkinsRule.JSONWebResponse postRawJson(
            JenkinsRule.WebClient client, JenkinsRule jenkins, String path, String body) throws Exception {
        client.withThrowExceptionOnFailingStatusCode(false).withRedirectEnabled(false);
        WebRequest request = new WebRequest(new URL(jenkins.getURL(), path), HttpMethod.POST);
        request.setAdditionalHeader("Content-Type", "application/json");
        hudson.security.csrf.CrumbIssuer crumbIssuer = jenkins.jenkins.getCrumbIssuer();
        if (crumbIssuer != null) {
            request.setAdditionalHeader(crumbIssuer.getCrumbRequestField(), crumbIssuer.getCrumb());
        }
        request.setRequestBody(body);
        return new JenkinsRule.JSONWebResponse(client.loadWebResponse(request));
    }
}
