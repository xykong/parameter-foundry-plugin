package io.jenkins.plugins.unifiedparameters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.EnvVars;
import hudson.cli.CLICommandInvoker;
import hudson.model.BooleanParameterDefinition;
import hudson.model.ChoiceParameterDefinition;
import hudson.model.FileParameterDefinition;
import hudson.model.FileParameterValue;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import io.jenkins.plugins.unifiedparameters.definition.AbstractUnifiedParameterDefinition;
import io.jenkins.plugins.unifiedparameters.definition.UnifiedChoiceParameterDefinition;
import io.jenkins.plugins.unifiedparameters.definition.UnifiedChoiceParameterValue;
import io.jenkins.plugins.unifiedparameters.definition.UnifiedMultiChoiceParameterDefinition;
import io.jenkins.plugins.unifiedparameters.definition.UnifiedMultiChoiceParameterValue;
import io.jenkins.plugins.unifiedparameters.form.FormField;
import io.jenkins.plugins.unifiedparameters.form.UnifiedFormParameterDefinition;
import io.jenkins.plugins.unifiedparameters.form.UnifiedFormParameterValue;
import io.jenkins.plugins.unifiedparameters.model.Option;
import io.jenkins.plugins.unifiedparameters.provider.ProviderLoadContext;
import io.jenkins.plugins.unifiedparameters.provider.SharedProviderDefinition;
import io.jenkins.plugins.unifiedparameters.provider.UnifiedParametersGlobalConfiguration;
import io.jenkins.plugins.unifiedparameters.snapshot.ProviderSnapshotStore;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import net.sf.json.JSONArray;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.util.NameValuePair;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class FormatCompatibilityIntegrationTest {

    @Test
    void standardRestFormatsProduceTheSameCanonicalMultipleValue(JenkinsRule jenkins) throws Exception {
        List<StandardCase> cases = List.of(
                new StandardCase(
                        "repeated",
                        List.of(
                                new NameValuePair(AbstractUnifiedParameterDefinition.PRESENCE_FIELD, "TARGETS"),
                                new NameValuePair("TARGETS", "web"),
                                new NameValuePair("TARGETS", "android"))),
                new StandardCase(
                        "json-array",
                        List.of(
                                new NameValuePair(AbstractUnifiedParameterDefinition.PRESENCE_FIELD, "TARGETS"),
                                new NameValuePair("TARGETS", "[\"web\",\"android\"]"))),
                new StandardCase(
                        "delimited",
                        List.of(
                                new NameValuePair(AbstractUnifiedParameterDefinition.PRESENCE_FIELD, "TARGETS"),
                                new NameValuePair("TARGETS", "web|android"))));

        for (StandardCase testCase : cases) {
            FreeStyleProject project =
                    standardMultipleJob(jenkins, testCase.mode(), "standard-format-" + testCase.mode(), "|");
            Page response = postForm(jenkins, project, testCase.parameters());

            assertEquals(201, response.getWebResponse().getStatusCode(), testCase.mode());
            jenkins.waitUntilNoActivity();
            assertEquals(
                    List.of("android", "web"),
                    multipleValue(project.getLastBuild()).getValue(),
                    testCase.mode());
        }
    }

    @Test
    void standardRestAbsentAndPresenceOnlyEmptyRemainDistinct(JenkinsRule jenkins) throws Exception {
        FreeStyleProject project = standardMultipleJob(jenkins, "repeated", "standard-empty", ",");
        UnifiedMultiChoiceParameterDefinition definition = (UnifiedMultiChoiceParameterDefinition)
                project.getProperty(ParametersDefinitionProperty.class).getParameterDefinition("TARGETS");
        definition.setDefaultMode("explicit");
        definition.setDefaultValues(List.of("android"));

        assertEquals(201, postForm(jenkins, project, List.of()).getWebResponse().getStatusCode());
        jenkins.waitUntilNoActivity();
        assertEquals(
                List.of("android"),
                multipleValue(project.getBuildByNumber(1)).getValue(),
                "missing field uses the configured default");

        assertEquals(
                201,
                postForm(
                                jenkins,
                                project,
                                List.of(new NameValuePair(
                                        AbstractUnifiedParameterDefinition.PRESENCE_FIELD, "TARGETS")))
                        .getWebResponse()
                        .getStatusCode());
        jenkins.waitUntilNoActivity();
        assertEquals(
                List.of(),
                multipleValue(project.getBuildByNumber(2)).getValue(),
                "presence marker without a value is explicit empty");
    }

    @Test
    void browserFormEmitsPresenceMetadataForNativeSubmission(JenkinsRule jenkins) throws Exception {
        FreeStyleProject project = standardMultipleJob(jenkins, "repeated", "browser-presence", ",");
        HtmlPage page = (HtmlPage) jenkins.createWebClient()
                .withJavaScriptEnabled(false)
                .withThrowExceptionOnFailingStatusCode(false)
                .getPage(project, "build?delay=0sec");

        assertEquals(
                1,
                page.getByXPath("//input[@name='__unified_parameters_presence' and @value='TARGETS']")
                        .size());
        assertEquals(
                1,
                page.getByXPath("//input[@name='__up_snapshot_TARGETS' and @data-up-snapshot-id='true']")
                        .size());
        assertFalse(page.getByXPath("//button[normalize-space()='Build without JavaScript']")
                .isEmpty());
    }

    @Test
    void standardRestFormUsesOneJsonStringField(JenkinsRule jenkins) throws Exception {
        FreeStyleProject project = formJob(jenkins, "standard-form-json");
        JSONObject submitted = new JSONObject();
        submitted.put("service", "checkout");
        submitted.put("replicas", 3);

        Page response = postForm(jenkins, project, List.of(new NameValuePair("DEPLOYMENT", submitted.toString())));

        assertEquals(201, response.getWebResponse().getStatusCode());
        jenkins.waitUntilNoActivity();
        UnifiedFormParameterValue value = assertInstanceOf(
                UnifiedFormParameterValue.class,
                project.getLastBuild().getAction(ParametersAction.class).getParameter("DEPLOYMENT"));
        assertEquals("checkout", value.getStructuredValue().get("service"));
        assertEquals(3L, value.getStructuredValue().get("replicas"));
    }

    @Test
    void typedApiPreservesStringArrayObjectAndExplicitNullShapes(JenkinsRule jenkins) throws Exception {
        FreeStyleProject project = typedShapeJob(jenkins, "typed-shapes");

        JSONObject form = new JSONObject();
        form.put("service", "checkout");
        form.put("replicas", 3);
        JSONObject values = new JSONObject();
        values.put("MESSAGE", "hello");
        values.put("FLAG", true);
        values.put("CORE_CHOICE", "two");
        values.put("CHANNEL", JSONNull.getInstance());
        values.put("TARGETS", JSONArray.fromObject(List.of("web", "android")));
        values.put("DEPLOYMENT", form);
        JSONObject body = new JSONObject();
        body.put("values", values);

        JenkinsRule.JSONWebResponse response =
                postJson(jenkins, project.getUrl() + "unified-parameters/api/v1/validate", body);

        assertEquals(200, response.getStatusCode(), response.getJSONObject().toString());
        JSONObject canonical = response.getJSONObject().getJSONObject("canonicalValues");
        assertEquals("hello", canonical.getString("MESSAGE"));
        assertTrue(canonical.getBoolean("FLAG"));
        assertEquals("two", canonical.getString("CORE_CHOICE"));
        assertTrue(canonical.get("CHANNEL") == null || canonical.get("CHANNEL") instanceof JSONNull);
        assertEquals(JSONArray.fromObject(List.of("android", "web")), canonical.getJSONArray("TARGETS"));
        assertEquals("checkout", canonical.getJSONObject("DEPLOYMENT").getString("service"));
        assertEquals(3, canonical.getJSONObject("DEPLOYMENT").getInt("replicas"));

        JenkinsRule.JSONWebResponse schema =
                jenkins.createWebClient().getJSON(project.getUrl() + "unified-parameters/api/v1/schema");
        assertEquals("string", schemaFor(schema, "MESSAGE").getString("type"));
        assertEquals("array", schemaFor(schema, "TARGETS").getString("type"));
        assertEquals("object", schemaFor(schema, "DEPLOYMENT").getString("type"));
    }

    @Test
    void unifiedChoiceMultipleModeUsesArrayShapeInTypedApi(JenkinsRule jenkins) throws Exception {
        FreeStyleProject project = unifiedMultipleJob(jenkins, "unified-typed-array");

        JSONObject values = new JSONObject();
        values.put("TARGETS", JSONArray.fromObject(List.of("web", "android")));
        JSONObject body = new JSONObject();
        body.put("values", values);

        JenkinsRule.JSONWebResponse response =
                postJson(jenkins, project.getUrl() + "unified-parameters/api/v1/validate", body);

        assertEquals(200, response.getStatusCode(), response.getJSONObject().toString());
        assertEquals(
                JSONArray.fromObject(List.of("android", "web")),
                response.getJSONObject().getJSONObject("canonicalValues").getJSONArray("TARGETS"));

        JenkinsRule.JSONWebResponse schema =
                jenkins.createWebClient().getJSON(project.getUrl() + "unified-parameters/api/v1/schema");
        JSONObject targets = schemaFor(schema, "TARGETS");
        assertEquals("array", targets.getString("type"));
        assertEquals("multiple", targets.getString("selectionMode"));
    }

    @Test
    void resolveReturnsOptionsForUnifiedChoiceMultipleMode(JenkinsRule jenkins) throws Exception {
        FreeStyleProject project = unifiedMultipleJob(jenkins, "unified-resolve-multiple");

        JSONObject body = new JSONObject();
        body.put("parameter", "TARGETS");
        JenkinsRule.JSONWebResponse response =
                postJson(jenkins, project.getUrl() + "unified-parameters/api/v1/resolve", body);

        assertEquals(200, response.getStatusCode(), response.getJSONObject().toString());
        JSONObject resolved = response.getJSONObject();
        assertEquals(3, resolved.getJSONArray("options").size());
        assertTrue(resolved.getJSONArray("defaults").isEmpty());
        assertEquals(
                "android", resolved.getJSONArray("options").getJSONObject(1).getString("value"));
    }

    @Test
    void typedApiUsesDefaultsForOmittedValuesAndEmptyArrayForExplicitEmpty(JenkinsRule jenkins) throws Exception {
        FreeStyleProject project = standardMultipleJob(jenkins, "repeated", "typed-empty-default", ",");
        UnifiedMultiChoiceParameterDefinition definition = (UnifiedMultiChoiceParameterDefinition)
                project.getProperty(ParametersDefinitionProperty.class).getParameterDefinition("TARGETS");
        definition.setDefaultMode("explicit");
        definition.setDefaultValues(List.of("android"));

        JSONObject omitted = new JSONObject();
        JenkinsRule.JSONWebResponse omittedResponse =
                postJson(jenkins, project.getUrl() + "unified-parameters/api/v1/validate", valuesBody(omitted));
        assertEquals(
                JSONArray.fromObject(List.of("android")),
                omittedResponse.getJSONObject().getJSONObject("canonicalValues").getJSONArray("TARGETS"));

        JSONObject explicitEmpty = new JSONObject();
        explicitEmpty.put("TARGETS", new JSONArray());
        JenkinsRule.JSONWebResponse emptyResponse =
                postJson(jenkins, project.getUrl() + "unified-parameters/api/v1/validate", valuesBody(explicitEmpty));
        assertEquals(
                JSONArray.fromObject(List.of()),
                emptyResponse.getJSONObject().getJSONObject("canonicalValues").getJSONArray("TARGETS"));
    }

    @Test
    void cliJsonArrayAndDelimitedFormatsProduceTheSameCanonicalValue(JenkinsRule jenkins) throws Exception {
        FreeStyleProject json = standardMultipleJob(jenkins, "repeated", "cli-json-array", ",");
        CLICommandInvoker.Result jsonResult = new CLICommandInvoker(jenkins, "build")
                .invokeWithArgs(json.getFullName(), "-p", "TARGETS=[\"web\",\"android\"]", "-s");
        assertEquals(0, jsonResult.returnCode(), jsonResult.stderr());
        assertEquals(
                List.of("android", "web"), multipleValue(json.getLastBuild()).getValue());

        FreeStyleProject delimited = standardMultipleJob(jenkins, "delimited", "cli-delimited", "|");
        CLICommandInvoker.Result delimitedResult = new CLICommandInvoker(jenkins, "build")
                .invokeWithArgs(delimited.getFullName(), "-p", "TARGETS=web|android", "-s");
        assertEquals(0, delimitedResult.returnCode(), delimitedResult.stderr());
        assertEquals(
                List.of("android", "web"),
                multipleValue(delimited.getLastBuild()).getValue());
    }

    @Test
    void nativeFileMultipartFormatRemainsSeparateFromTypedJson(JenkinsRule jenkins) throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject("multipart-native-file");
        FileParameterDefinition upload = new FileParameterDefinition("UPLOAD");
        project.addProperty(new ParametersDefinitionProperty(upload));

        Page response = postMultipart(jenkins, project, List.of(), "UPLOAD", "payload.txt", "native multipart payload");

        assertEquals(201, response.getWebResponse().getStatusCode());
        jenkins.waitUntilNoActivity();
        ParametersAction parameters = project.getLastBuild().getAction(ParametersAction.class);
        FileParameterValue file = assertInstanceOf(FileParameterValue.class, parameters.getParameter("UPLOAD"));
        assertEquals("payload.txt", file.getOriginalFileName());
    }

    @Test
    void standardDependencyJsonCanReuseTypedResolveSnapshot(JenkinsRule jenkins) throws Exception {
        UnifiedParametersGlobalConfiguration.get().setProviders(List.of(new DependencyProvider("artifacts")));
        ProviderSnapshotStore.get().clear();
        FreeStyleProject project = reactiveJob(jenkins, "standard-snapshot-format");

        JSONObject dependencies = new JSONObject();
        dependencies.put("ENVIRONMENT", "production");
        JSONObject resolveBody = new JSONObject();
        resolveBody.put("parameter", "ARTIFACT");
        resolveBody.put("dependencies", dependencies);
        JenkinsRule.JSONWebResponse resolved =
                postJson(jenkins, project.getUrl() + "unified-parameters/api/v1/resolve", resolveBody);
        assertEquals(200, resolved.getStatusCode());
        String snapshotId = resolved.getJSONObject().getString("snapshotId");

        Page response = postForm(
                jenkins,
                project,
                List.of(
                        new NameValuePair(AbstractUnifiedParameterDefinition.PRESENCE_FIELD, "ENVIRONMENT"),
                        new NameValuePair(AbstractUnifiedParameterDefinition.PRESENCE_FIELD, "ARTIFACT"),
                        new NameValuePair("ENVIRONMENT", "production"),
                        new NameValuePair("ARTIFACT", "production-artifact"),
                        new NameValuePair("__up_dependencies_ARTIFACT", "{\"ENVIRONMENT\":\"production\"}"),
                        new NameValuePair("__up_snapshot_ARTIFACT", snapshotId)));

        assertEquals(
                201,
                response.getWebResponse().getStatusCode(),
                response.getWebResponse().getContentAsString());
        jenkins.waitUntilNoActivity();
        UnifiedChoiceParameterValue value = assertInstanceOf(
                UnifiedChoiceParameterValue.class,
                project.getLastBuild().getAction(ParametersAction.class).getParameter("ARTIFACT"));
        assertEquals("production-artifact", value.getValue());
        assertNotNull(value.getProviderSnapshot());
        assertEquals(snapshotId, value.getProviderSnapshot().getSnapshotId());
    }

    @Test
    void expiredSnapshotIsRefreshedBeforeBuild(JenkinsRule jenkins) throws Exception {
        DependencyProvider provider = new DependencyProvider("artifacts");
        provider.setCacheTtlSeconds(0);
        UnifiedParametersGlobalConfiguration.get().setProviders(List.of(provider));
        ProviderSnapshotStore.get().clear();
        io.jenkins.plugins.unifiedparameters.provider.ProviderRuntime.get().clear();
        FreeStyleProject project = reactiveJob(jenkins, "expired-snapshot-format");

        JSONObject dependencies = new JSONObject();
        dependencies.put("ENVIRONMENT", "staging");
        JSONObject resolveBody = new JSONObject();
        resolveBody.put("parameter", "ARTIFACT");
        resolveBody.put("dependencies", dependencies);
        JenkinsRule.JSONWebResponse resolved =
                postJson(jenkins, project.getUrl() + "unified-parameters/api/v1/resolve", resolveBody);
        String snapshotId = resolved.getJSONObject().getString("snapshotId");

        JSONObject values = new JSONObject();
        values.put("ENVIRONMENT", "staging");
        values.put("ARTIFACT", "staging-artifact");
        JSONObject snapshots = new JSONObject();
        snapshots.put("ARTIFACT", snapshotId);
        JSONObject body = new JSONObject();
        body.put("values", values);
        body.put("providerSnapshots", snapshots);

        JenkinsRule.JSONWebResponse response =
                postJson(jenkins, project.getUrl() + "unified-parameters/api/v1/build", body);

        assertEquals(201, response.getStatusCode(), response.getJSONObject().toString());
        jenkins.waitUntilNoActivity();
        UnifiedChoiceParameterValue value = assertInstanceOf(
                UnifiedChoiceParameterValue.class,
                project.getLastBuild().getAction(ParametersAction.class).getParameter("ARTIFACT"));
        assertNotNull(value.getProviderSnapshot());
        assertNotEquals(snapshotId, value.getProviderSnapshot().getSnapshotId());
    }

    @Test
    void semanticAndShapeFailuresReturnStableCodesWithoutQueueing(JenkinsRule jenkins) throws Exception {
        assertStandardFailure(
                jenkins,
                "standard-unknown",
                List.of(
                        new NameValuePair(AbstractUnifiedParameterDefinition.PRESENCE_FIELD, "TARGETS"),
                        new NameValuePair("TARGETS", "unknown")),
                "UNKNOWN_VALUE");

        FreeStyleProject disabled = standardMultipleJob(jenkins, "repeated", "standard-disabled", ",");
        UnifiedMultiChoiceParameterDefinition disabledDefinition = (UnifiedMultiChoiceParameterDefinition)
                disabled.getProperty(ParametersDefinitionProperty.class).getParameterDefinition("TARGETS");
        Option disabledOption = disabledDefinition.findOption("android");
        disabledOption.setDisabled(true);
        assertStandardFailure(
                jenkins,
                disabled,
                List.of(
                        new NameValuePair(AbstractUnifiedParameterDefinition.PRESENCE_FIELD, "TARGETS"),
                        new NameValuePair("TARGETS", "android")),
                "DISABLED_VALUE");

        assertStandardFailure(
                jenkins,
                "standard-duplicate",
                List.of(
                        new NameValuePair(AbstractUnifiedParameterDefinition.PRESENCE_FIELD, "TARGETS"),
                        new NameValuePair("TARGETS", "android"),
                        new NameValuePair("TARGETS", "android")),
                "DUPLICATE_VALUE");

        FreeStyleProject malformed = standardMultipleJob(jenkins, "json-array", "standard-malformed-array", ",");
        assertStandardFailure(
                jenkins,
                malformed,
                List.of(
                        new NameValuePair(AbstractUnifiedParameterDefinition.PRESENCE_FIELD, "TARGETS"),
                        new NameValuePair("TARGETS", "[\"android\"")),
                "MALFORMED_INPUT");
    }

    @Test
    void typedApiRejectsWrongContainersInsteadOfApplyingDefaults(JenkinsRule jenkins) throws Exception {
        FreeStyleProject project = standardMultipleJob(jenkins, "repeated", "typed-wrong-container", ",");
        UnifiedMultiChoiceParameterDefinition definition = (UnifiedMultiChoiceParameterDefinition)
                project.getProperty(ParametersDefinitionProperty.class).getParameterDefinition("TARGETS");
        definition.setDefaultMode("explicit");
        definition.setDefaultValues(List.of("android"));
        int queueLength = jenkins.jenkins.getQueue().getItems().length;

        JSONObject wrongValues = new JSONObject();
        wrongValues.put("values", JSONArray.fromObject(List.of("android")));
        JenkinsRule.JSONWebResponse wrongValuesResponse =
                postJson(jenkins, project.getUrl() + "unified-parameters/api/v1/build", wrongValues);
        assertEquals(400, wrongValuesResponse.getStatusCode());
        assertEquals("UNIFIED_PARAMETER_INVALID_TYPE", errorCode(wrongValuesResponse));
        assertEquals(queueLength, jenkins.jenkins.getQueue().getItems().length);

        JSONObject wrongParameterValue = new JSONObject();
        JSONObject values = new JSONObject();
        values.put("TARGETS", "android");
        wrongParameterValue.put("values", values);
        JenkinsRule.JSONWebResponse wrongParameterValueResponse =
                postJson(jenkins, project.getUrl() + "unified-parameters/api/v1/build", wrongParameterValue);
        assertEquals(400, wrongParameterValueResponse.getStatusCode());
        assertEquals("UNIFIED_PARAMETER_INVALID_TYPE", errorCode(wrongParameterValueResponse));
        assertEquals(queueLength, jenkins.jenkins.getQueue().getItems().length);
    }

    @Test
    void snapshotBindingFailureIsAConflictAndDoesNotQueue(JenkinsRule jenkins) throws Exception {
        UnifiedParametersGlobalConfiguration.get().setProviders(List.of(new DependencyProvider("artifacts")));
        ProviderSnapshotStore.get().clear();
        FreeStyleProject project = reactiveJob(jenkins, "stale-snapshot-format");

        JSONObject dependencies = new JSONObject();
        dependencies.put("ENVIRONMENT", "staging");
        JSONObject resolveBody = new JSONObject();
        resolveBody.put("parameter", "ARTIFACT");
        resolveBody.put("dependencies", dependencies);
        JenkinsRule.JSONWebResponse resolved =
                postJson(jenkins, project.getUrl() + "unified-parameters/api/v1/resolve", resolveBody);
        String snapshotId = resolved.getJSONObject().getString("snapshotId");

        JSONObject values = new JSONObject();
        values.put("ENVIRONMENT", "production");
        values.put("ARTIFACT", "production-artifact");
        JSONObject snapshots = new JSONObject();
        snapshots.put("ARTIFACT", snapshotId);
        JSONObject body = new JSONObject();
        body.put("values", values);
        body.put("providerSnapshots", snapshots);
        int queueLength = jenkins.jenkins.getQueue().getItems().length;

        JenkinsRule.JSONWebResponse response =
                postJson(jenkins, project.getUrl() + "unified-parameters/api/v1/build", body);

        assertEquals(409, response.getStatusCode());
        assertEquals("UNIFIED_PARAMETER_SNAPSHOT_INVALID", errorCode(response));
        assertEquals(queueLength, jenkins.jenkins.getQueue().getItems().length);
    }

    @Test
    void multiChoiceExportUsesCanonicalFormatNotSubmissionOrder() {
        UnifiedMultiChoiceParameterValue delimited =
                new UnifiedMultiChoiceParameterValue("TARGETS", List.of("web", "android"), null, "delimited", "|");
        EnvVars delimitedEnvironment = new EnvVars();
        delimited.buildEnvironment(null, delimitedEnvironment);
        assertEquals("android|web", delimitedEnvironment.get("TARGETS"));

        UnifiedMultiChoiceParameterValue json =
                new UnifiedMultiChoiceParameterValue("TARGETS", List.of("web", "android"), null, "json", "|");
        EnvVars jsonEnvironment = new EnvVars();
        json.buildEnvironment(null, jsonEnvironment);
        assertEquals("[\"android\",\"web\"]", jsonEnvironment.get("TARGETS"));

        UnifiedMultiChoiceParameterValue none =
                new UnifiedMultiChoiceParameterValue("TARGETS", List.of("web", "android"), null, "none", "|");
        EnvVars noneEnvironment = new EnvVars();
        none.buildEnvironment(null, noneEnvironment);
        assertFalse(noneEnvironment.containsKey("TARGETS"));
    }

    private static void assertStandardFailure(
            JenkinsRule jenkins, String name, List<NameValuePair> parameters, String expectedCode) throws Exception {
        assertStandardFailure(jenkins, standardMultipleJob(jenkins, "repeated", name, ","), parameters, expectedCode);
    }

    private static void assertStandardFailure(
            JenkinsRule jenkins, FreeStyleProject project, List<NameValuePair> parameters, String expectedCode)
            throws Exception {
        int queueLength = jenkins.jenkins.getQueue().getItems().length;
        Page response = postForm(jenkins, project, parameters);
        assertEquals(400, response.getWebResponse().getStatusCode(), expectedCode);
        assertTrue(
                response.getWebResponse().getContentAsString().contains("UNIFIED_PARAMETER_" + expectedCode),
                response.getWebResponse().getContentAsString());
        assertEquals(queueLength, jenkins.jenkins.getQueue().getItems().length);
    }

    private static JSONObject valuesBody(JSONObject values) {
        JSONObject body = new JSONObject();
        body.put("values", values);
        return body;
    }

    private static JSONObject schemaFor(JenkinsRule.JSONWebResponse response, String name) {
        for (Object raw : response.getJSONObject().getJSONArray("parameters")) {
            JSONObject parameter = (JSONObject) raw;
            if (name.equals(parameter.getString("name"))) {
                return parameter;
            }
        }
        throw new AssertionError("Missing schema for " + name);
    }

    private static String errorCode(JenkinsRule.JSONWebResponse response) {
        return response.getJSONObject().getJSONArray("errors").getJSONObject(0).getString("code");
    }

    private static UnifiedMultiChoiceParameterValue multipleValue(FreeStyleBuild build) {
        return assertInstanceOf(
                UnifiedMultiChoiceParameterValue.class,
                build.getAction(ParametersAction.class).getParameter("TARGETS"));
    }

    private static FreeStyleProject standardMultipleJob(JenkinsRule jenkins, String mode, String name, String delimiter)
            throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject(name);
        UnifiedMultiChoiceParameterDefinition definition = new UnifiedMultiChoiceParameterDefinition("TARGETS");
        definition.setChoices(List.of(new Option("web"), new Option("android"), new Option("ios")));
        definition.setStandardApiInputMode(mode);
        definition.setDelimiter(delimiter);
        project.addProperty(new ParametersDefinitionProperty(definition));
        return project;
    }

    private static FreeStyleProject unifiedMultipleJob(JenkinsRule jenkins, String name) throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject(name);
        UnifiedChoiceParameterDefinition definition = new UnifiedChoiceParameterDefinition("TARGETS");
        definition.setSelectionMode("multiple");
        definition.setChoices(List.of(new Option("web"), new Option("android"), new Option("ios")));
        project.addProperty(new ParametersDefinitionProperty(definition));
        return project;
    }

    private static FreeStyleProject formJob(JenkinsRule jenkins, String name) throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject(name);
        UnifiedFormParameterDefinition definition = new UnifiedFormParameterDefinition("DEPLOYMENT");
        FormField service = new FormField("service", "string");
        service.setRequired(true);
        FormField replicas = new FormField("replicas", "integer");
        replicas.setRequired(true);
        replicas.setMinimum(1.0);
        replicas.setMaximum(20.0);
        definition.setFields(List.of(service, replicas));
        project.addProperty(new ParametersDefinitionProperty(definition));
        return project;
    }

    private static FreeStyleProject typedShapeJob(JenkinsRule jenkins, String name) throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject(name);
        UnifiedChoiceParameterDefinition channel = new UnifiedChoiceParameterDefinition("CHANNEL");
        channel.setChoices(List.of(new Option("staging"), new Option("production")));
        UnifiedMultiChoiceParameterDefinition targets = new UnifiedMultiChoiceParameterDefinition("TARGETS");
        targets.setChoices(List.of(new Option("web"), new Option("android")));
        UnifiedFormParameterDefinition form = formDefinition();
        project.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("MESSAGE", "default", ""),
                new BooleanParameterDefinition("FLAG", false, ""),
                new ChoiceParameterDefinition("CORE_CHOICE", new String[] {"one", "two"}, ""),
                channel,
                targets,
                form));
        return project;
    }

    private static UnifiedFormParameterDefinition formDefinition() {
        UnifiedFormParameterDefinition definition = new UnifiedFormParameterDefinition("DEPLOYMENT");
        FormField service = new FormField("service", "string");
        service.setRequired(true);
        FormField replicas = new FormField("replicas", "integer");
        replicas.setRequired(true);
        replicas.setMinimum(1.0);
        replicas.setMaximum(20.0);
        definition.setFields(List.of(service, replicas));
        return definition;
    }

    private static FreeStyleProject reactiveJob(JenkinsRule jenkins, String name) throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject(name);
        UnifiedChoiceParameterDefinition environment = new UnifiedChoiceParameterDefinition("ENVIRONMENT");
        environment.setChoices(List.of(new Option("staging"), new Option("production")));
        environment.setDefaultMode("explicit");
        environment.setDefaultValue("staging");
        UnifiedChoiceParameterDefinition artifact = new UnifiedChoiceParameterDefinition("ARTIFACT");
        artifact.setProviderId("artifacts");
        artifact.setDependsOn(List.of("ENVIRONMENT"));
        artifact.setDefaultMode("first");
        project.addProperty(new ParametersDefinitionProperty(environment, artifact));
        return project;
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

    private static Page postMultipart(
            JenkinsRule jenkins,
            FreeStyleProject project,
            List<NameValuePair> parameters,
            String fileField,
            String fileName,
            String fileContent)
            throws Exception {
        String boundary = "----UnifiedParametersFormatTest";
        StringBuilder body = new StringBuilder();
        for (NameValuePair parameter : parameters) {
            body.append("--").append(boundary).append("\r\n");
            body.append("Content-Disposition: form-data; name=\"")
                    .append(parameter.getName())
                    .append("\"\r\n\r\n");
            body.append(parameter.getValue()).append("\r\n");
        }
        body.append("--").append(boundary).append("\r\n");
        body.append("Content-Disposition: form-data; name=\"")
                .append(fileField)
                .append("\"; filename=\"")
                .append(fileName)
                .append("\"\r\n");
        body.append("Content-Type: text/plain\r\n\r\n");
        body.append(fileContent).append("\r\n");
        body.append("--").append(boundary).append("--\r\n");

        JenkinsRule.WebClient client = jenkins.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false)
                .withRedirectEnabled(false);
        WebRequest request = new WebRequest(
                new URL(jenkins.getURL(), project.getUrl() + "buildWithParameters?delay=0sec"), HttpMethod.POST);
        request.setAdditionalHeader("Content-Type", "multipart/form-data; boundary=" + boundary);
        request.setRequestBody(body.toString());
        hudson.security.csrf.CrumbIssuer issuer = jenkins.jenkins.getCrumbIssuer();
        if (issuer != null) {
            request.setAdditionalHeader(issuer.getCrumbRequestField(), issuer.getCrumb());
        }
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

    private record StandardCase(String mode, List<NameValuePair> parameters) {}

    private static final class DependencyProvider extends SharedProviderDefinition {
        DependencyProvider(String id) {
            super(id);
        }

        @Override
        public List<Option> load(ProviderLoadContext context) {
            String environment = context.request().dependencyValues().get("ENVIRONMENT");
            return List.of(new Option(environment + "-artifact"));
        }

        @Override
        protected String providerFingerprint() {
            return "format-dependency-provider";
        }
    }
}
