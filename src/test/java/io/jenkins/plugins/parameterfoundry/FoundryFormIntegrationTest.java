package io.jenkins.plugins.parameterfoundry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.EnvVars;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import io.jenkins.plugins.parameterfoundry.form.FormEvaluationService;
import io.jenkins.plugins.parameterfoundry.form.FormField;
import io.jenkins.plugins.parameterfoundry.form.FormLayout;
import io.jenkins.plugins.parameterfoundry.form.FoundryFormParameterDefinition;
import io.jenkins.plugins.parameterfoundry.form.FoundryFormParameterValue;
import io.jenkins.plugins.parameterfoundry.model.Option;
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

@WithJenkins
class FoundryFormIntegrationTest {

    @Test
    void typedBuildPersistsStructuredValueAndExportsGovernedFields(JenkinsRule jenkins) throws Exception {
        FreeStyleProject project = formJob(jenkins, "form-typed");

        JSONObject formValue = new JSONObject();
        formValue.put("service", "checkout");
        formValue.put("replicas", 3);
        formValue.put("enabled", true);
        formValue.put("region", "us-east");
        formValue.put("features", JSONArray.fromObject(List.of("audit", "cache")));
        formValue.put("metadata", JSONObject.fromObject(Map.of("owner", "platform")));
        formValue.put("ports", JSONArray.fromObject(List.of(8080, 8443)));
        JSONObject values = new JSONObject();
        values.put("DEPLOYMENT", formValue);
        JSONObject body = new JSONObject();
        body.put("values", values);

        JenkinsRule.JSONWebResponse response =
                postJson(jenkins, project.getUrl() + "parameter-foundry/api/v1/build", body);

        assertEquals(201, response.getStatusCode(), response.getJSONObject().toString());
        jenkins.waitUntilNoActivity();
        FoundryFormParameterValue value = assertInstanceOf(
                FoundryFormParameterValue.class,
                project.getLastBuild().getAction(ParametersAction.class).getParameter("DEPLOYMENT"));
        Map<String, Object> structured = value.getStructuredValue();
        assertEquals("checkout", structured.get("service"));
        assertEquals(3L, structured.get("replicas"));
        assertEquals(List.of("audit", "cache"), structured.get("features"));
        EnvVars environment = new EnvVars();
        value.buildEnvironment(project.getLastBuild(), environment);
        assertTrue(environment.get("DEPLOYMENT").contains("\"service\":\"checkout\""));
        assertEquals("checkout", environment.get("SERVICE_NAME"));
    }

    @Test
    void typedBuildRejectsUnknownFieldsAndInvalidTypes(JenkinsRule jenkins) throws Exception {
        FreeStyleProject project = formJob(jenkins, "form-invalid");
        JSONObject formValue = new JSONObject();
        formValue.put("service", "checkout");
        formValue.put("replicas", "three");
        formValue.put("unknown", true);
        JSONObject values = new JSONObject();
        values.put("DEPLOYMENT", formValue);
        JSONObject body = new JSONObject();
        body.put("values", values);

        JenkinsRule.JSONWebResponse response =
                postJson(jenkins, project.getUrl() + "parameter-foundry/api/v1/validate", body);

        assertEquals(400, response.getStatusCode());
        String message =
                response.getJSONObject().getJSONArray("errors").getJSONObject(0).getString("message");
        assertTrue(message.contains("Unknown form field"));
    }

    @Test
    void browserSubmittedJsonArrayWrapperIsDecoded(JenkinsRule jenkins) throws Exception {
        FoundryFormParameterDefinition definition =
                (FoundryFormParameterDefinition) formJob(jenkins, "form-browser-wrapper")
                        .getProperty(ParametersDefinitionProperty.class)
                        .getParameterDefinition("DEPLOYMENT");
        JSONObject form = new JSONObject();
        form.put("service", "catalog");
        form.put("replicas", 2);
        form.put("enabled", false);
        form.put("region", "us-east");
        form.put("features", JSONArray.fromObject(List.of("cache")));
        form.put("metadata", JSONObject.fromObject(Map.of("owner", "platform")));
        form.put("ports", JSONArray.fromObject(List.of(8080)));
        JSONObject submitted = new JSONObject();
        submitted.put("value", JSONArray.fromObject(List.of(form.toString())));

        FoundryFormParameterValue value = (FoundryFormParameterValue)
                definition.createValue((org.kohsuke.stapler.StaplerRequest2) null, submitted);

        assertEquals("catalog", value.getStructuredValue().get("service"));
    }

    @Test
    void rebuildCopyPreservesHistoricalStructuredValueAsDefault(JenkinsRule jenkins) throws Exception {
        FoundryFormParameterDefinition definition = (FoundryFormParameterDefinition) formJob(jenkins, "form-rebuild")
                .getProperty(ParametersDefinitionProperty.class)
                .getParameterDefinition("DEPLOYMENT");
        Map<String, Object> historical = Map.of(
                "service",
                "catalog",
                "replicas",
                3L,
                "enabled",
                true,
                "region",
                "eu-central",
                "features",
                List.of("audit"),
                "metadata",
                Map.of("owner", "platform"),
                "ports",
                List.of(8080L));

        FoundryFormParameterDefinition copy = assertInstanceOf(
                FoundryFormParameterDefinition.class,
                definition.copyWithDefaultValue(new FoundryFormParameterValue("DEPLOYMENT", historical)));
        JSONObject defaultValue = JSONObject.fromObject(copy.getDefaultJson());

        assertEquals("catalog", defaultValue.getString("service"));
        assertEquals(3L, defaultValue.getLong("replicas"));
        assertEquals("eu-central", defaultValue.getString("region"));
    }

    @Test
    void definitionValidationRejectsSecretWholeFormExportAndUnknownLayoutField() {
        FoundryFormParameterDefinition definition = new FoundryFormParameterDefinition("FORM");
        FormField secret = new FormField("token", "string");
        secret.setSecret(true);
        FormLayout layout = new FormLayout("main", "section");
        layout.setFieldNames(List.of("missing"));
        definition.setFields(List.of(secret));
        definition.setLayouts(List.of(layout));
        definition.setEnvironmentExport("json");

        List<String> errors = FormEvaluationService.validateDefinition(definition);

        assertTrue(errors.stream().anyMatch(error -> error.contains("secret fields")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("unknown field")));
    }

    @Test
    void definitionValidationRejectsReservedName() {
        FoundryFormParameterDefinition definition = new FoundryFormParameterDefinition("__parameter_foundry_presence");
        List<String> errors =
                io.jenkins.plugins.parameterfoundry.validation.DefinitionValidationService.validateStoredDefinition(
                                definition)
                        .getErrors();

        assertTrue(errors.stream().anyMatch(error -> error.contains("reserved")));
    }

    private static FreeStyleProject formJob(JenkinsRule jenkins, String name) throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject(name);
        FoundryFormParameterDefinition definition = new FoundryFormParameterDefinition("DEPLOYMENT");

        FormField service = new FormField("service", "string");
        service.setRequired(true);
        service.setPattern("[a-z][a-z0-9-]{1,31}");
        service.setEnvironmentVariable("SERVICE_NAME");

        FormField replicas = new FormField("replicas", "integer");
        replicas.setRequired(true);
        replicas.setMinimum(1.0);
        replicas.setMaximum(20.0);

        FormField enabled = new FormField("enabled", "boolean");

        FormField region = new FormField("region", "choice");
        region.setRequired(true);
        region.setChoices(List.of(new Option("us-east"), new Option("eu-central")));

        FormField features = new FormField("features", "multi-choice");
        features.setChoices(List.of(new Option("cache"), new Option("audit"), new Option("canary")));
        features.setMaxItems(2);

        FormField owner = new FormField("owner", "string");
        owner.setRequired(true);
        FormField metadata = new FormField("metadata", "object");
        metadata.setFields(List.of(owner));

        FormField ports = new FormField("ports", "array");
        ports.setItemType("integer");
        ports.setMinItems(1);
        ports.setMaxItems(4);

        FormLayout primary = new FormLayout("primary", "grid");
        primary.setColumns(2);
        primary.setFieldNames(List.of("service", "replicas", "enabled", "region"));
        FormLayout advanced = new FormLayout("advanced", "collapsible");
        advanced.setFieldNames(List.of("features", "metadata", "ports"));
        advanced.setCollapsed(true);

        definition.setFields(List.of(service, replicas, enabled, region, features, metadata, ports));
        definition.setLayouts(List.of(primary, advanced));
        project.addProperty(new ParametersDefinitionProperty(definition));
        return project;
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
