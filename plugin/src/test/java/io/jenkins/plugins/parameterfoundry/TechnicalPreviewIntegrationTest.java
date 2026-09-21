package io.jenkins.plugins.parameterfoundry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.cli.CLICommandInvoker;
import hudson.model.BooleanParameterDefinition;
import hudson.model.ChoiceParameterDefinition;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.PasswordParameterDefinition;
import hudson.model.Result;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.model.TopLevelItem;
import hudson.security.csrf.CrumbIssuer;
import io.jenkins.plugins.parameterfoundry.api.ParameterFoundryJobAction;
import io.jenkins.plugins.parameterfoundry.definition.AbstractFoundryParameterDefinition;
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition;
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterValue;
import io.jenkins.plugins.parameterfoundry.model.Option;
import io.jenkins.plugins.parameterfoundry.validation.DefinitionValidationService;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.util.NameValuePair;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class TechnicalPreviewIntegrationTest {

    @Test
    void definitionSurvivesSaveReloadCopyAndRename(JenkinsRule jenkins) throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject("source");
        project.addProperty(new ParametersDefinitionProperty(singleDefinition(), multipleDefinition()));

        jenkins.configRoundtrip(project);
        assertDefinitions(project);

        TopLevelItem original = project;
        FreeStyleProject copy = (FreeStyleProject) jenkins.jenkins.copy(original, "copy");
        assertDefinitions(copy);

        copy.renameTo("renamed");
        FreeStyleProject renamed = jenkins.jenkins.getItemByFullName("renamed", FreeStyleProject.class);
        assertNotNull(renamed);
        assertDefinitions(renamed);
    }

    @Test
    void malformedXstreamDefinitionIsDiagnosedAndCannotQueue(JenkinsRule jenkins) throws Exception {
        FoundryChoiceParameterDefinition valid = multipleDefinition();
        String xml = Jenkins.XSTREAM2.toXML(valid);
        String malformedXml = xml.replace("<value>ios</value>", "<value>android</value>");
        FoundryChoiceParameterDefinition malformed =
                (FoundryChoiceParameterDefinition) Jenkins.XSTREAM2.fromXML(malformedXml);
        assertFalse(
                DefinitionValidationService.validateStoredDefinition(malformed).isValid());

        FreeStyleProject project = jenkins.createFreeStyleProject("invalid-loaded");
        project.addProperty(new ParametersDefinitionProperty(malformed));
        int queueLength = jenkins.jenkins.getQueue().getItems().length;

        Page response = postForm(
                jenkins,
                project,
                List.of(new NameValuePair(AbstractFoundryParameterDefinition.PRESENCE_FIELD, "TARGETS")));

        assertEquals(400, response.getWebResponse().getStatusCode());
        assertEquals(queueLength, jenkins.jenkins.getQueue().getItems().length);
        assertTrue(response.getWebResponse().getContentAsString().contains("CONFIGURATION_INVALID"));
    }

    @Test
    void standardRestInvalidValueReturns400AndDoesNotQueue(JenkinsRule jenkins) throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject("standard-rest");
        project.addProperty(new ParametersDefinitionProperty(multipleDefinition()));
        int queueLength = jenkins.jenkins.getQueue().getItems().length;

        Page response = postForm(
                jenkins,
                project,
                List.of(
                        new NameValuePair(AbstractFoundryParameterDefinition.PRESENCE_FIELD, "TARGETS"),
                        new NameValuePair("TARGETS", "unknown")));

        assertEquals(400, response.getWebResponse().getStatusCode());
        assertEquals(queueLength, jenkins.jenkins.getQueue().getItems().length);
        assertTrue(response.getWebResponse().getContentAsString().contains("UNKNOWN_VALUE"));
    }

    @Test
    void standardRestPresenceMarkerDistinguishesEmptyFromAbsent(JenkinsRule jenkins) throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject("presence");
        FoundryChoiceParameterDefinition definition = multipleDefinition();
        definition.setDefaultMode("explicit");
        definition.setDefaultValues(List.of("android"));
        project.addProperty(new ParametersDefinitionProperty(definition));

        Page response = postForm(
                jenkins,
                project,
                List.of(new NameValuePair(AbstractFoundryParameterDefinition.PRESENCE_FIELD, "TARGETS")));

        assertEquals(201, response.getWebResponse().getStatusCode());
        jenkins.waitUntilNoActivity();
        FoundryChoiceParameterValue value = assertInstanceOf(
                FoundryChoiceParameterValue.class,
                project.getLastBuild().getAction(ParametersAction.class).getParameter("TARGETS"));
        assertEquals(List.of(), value.getValue());
    }

    @Test
    void typedApiSchedulesMixedCoreAndFoundryDefinitions(JenkinsRule jenkins) throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject("typed-mixed");
        project.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("MESSAGE", "default", ""),
                new BooleanParameterDefinition("FLAG", false, ""),
                new ChoiceParameterDefinition("CORE_CHOICE", new String[] {"one", "two"}, ""),
                multipleDefinition()));
        assertNotNull(project.getAction(ParameterFoundryJobAction.class));

        JSONObject values = new JSONObject();
        values.put("MESSAGE", "hello");
        values.put("FLAG", true);
        values.put("CORE_CHOICE", "two");
        values.put("TARGETS", JSONArray.fromObject(List.of("web", "android")));
        JSONObject body = new JSONObject();
        body.put("values", values);
        body.put("delaySeconds", 0);

        JenkinsRule.JSONWebResponse response =
                postJson(jenkins, project.getUrl() + "parameter-foundry/api/v1/build", body, true);

        assertEquals(201, response.getStatusCode());
        jenkins.waitUntilNoActivity();
        ParametersAction parameters = project.getLastBuild().getAction(ParametersAction.class);
        assertEquals("hello", parameters.getParameter("MESSAGE").getValue());
        assertEquals(true, parameters.getParameter("FLAG").getValue());
        assertEquals("two", parameters.getParameter("CORE_CHOICE").getValue());
        assertEquals(
                List.of("android", "web"), parameters.getParameter("TARGETS").getValue());
    }

    @Test
    void typedApiRejectsUnsupportedDefinitionsAtomically(JenkinsRule jenkins) throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject("typed-unsupported");
        project.addProperty(new ParametersDefinitionProperty(
                new PasswordParameterDefinition("SECRET", "secret", ""), multipleDefinition()));
        JSONObject body = new JSONObject();
        body.put("values", new JSONObject());
        int queueLength = jenkins.jenkins.getQueue().getItems().length;

        JenkinsRule.JSONWebResponse response =
                postJson(jenkins, project.getUrl() + "parameter-foundry/api/v1/build", body, false);

        assertEquals(409, response.getStatusCode());
        assertEquals(queueLength, jenkins.jenkins.getQueue().getItems().length);
        assertTrue(response.getJSONObject().toString().contains("UNSUPPORTED_DEFINITION"));
    }

    @Test
    void inlineImportPreviewReturnsStructuredOptions(JenkinsRule jenkins) throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject("import-preview");
        project.addProperty(new ParametersDefinitionProperty(singleDefinition()));
        JSONObject body = new JSONObject();
        body.put("content", """
                options:
                  - value: android
                    label: Android
                    group: Mobile
                    tags: [device, release]
                  - value: web
                """);

        JenkinsRule.JSONWebResponse response =
                postJson(jenkins, project.getUrl() + "parameter-foundry/api/v1/import/preview", body, true);

        assertEquals(200, response.getStatusCode());
        assertEquals(2, response.getJSONObject().getInt("count"));
        assertEquals(
                "android",
                response.getJSONObject()
                        .getJSONArray("options")
                        .getJSONObject(0)
                        .getString("value"));
        assertEquals(
                JSONArray.fromObject(List.of("device", "release")),
                response.getJSONObject()
                        .getJSONArray("options")
                        .getJSONObject(0)
                        .getJSONArray("tags"));
    }

    @Test
    void schemaResolveAndValidateShareCanonicalSemantics(JenkinsRule jenkins) throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject("discovery");
        project.addProperty(new ParametersDefinitionProperty(singleDefinition(), multipleDefinition()));

        JenkinsRule.JSONWebResponse schema =
                jenkins.createWebClient().getJSON(project.getUrl() + "parameter-foundry/api/v1/schema");
        assertEquals(2, schema.getJSONObject().getJSONArray("parameters").size());

        JSONObject resolveBody = new JSONObject();
        resolveBody.put("parameter", "TARGETS");
        JenkinsRule.JSONWebResponse resolve =
                postJson(jenkins, project.getUrl() + "parameter-foundry/api/v1/resolve", resolveBody, true);
        assertEquals(3, resolve.getJSONObject().getJSONArray("options").size());

        JSONObject submitted = new JSONObject();
        submitted.put("TARGETS", JSONArray.fromObject(List.of("web", "android")));
        JSONObject validateBody = new JSONObject();
        validateBody.put("values", submitted);
        JenkinsRule.JSONWebResponse validate =
                postJson(jenkins, project.getUrl() + "parameter-foundry/api/v1/validate", validateBody, true);
        assertEquals(
                JSONArray.fromObject(List.of("android", "web")),
                validate.getJSONObject().getJSONObject("canonicalValues").getJSONArray("TARGETS"));
    }

    @Test
    void typedPostRequiresCrumbAndDoesNotQueueWithoutIt(JenkinsRule jenkins) throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject("csrf");
        project.addProperty(new ParametersDefinitionProperty(multipleDefinition()));
        int queueLength = jenkins.jenkins.getQueue().getItems().length;
        WebRequest request = new WebRequest(
                new URL(jenkins.getURL(), project.getUrl() + "parameter-foundry/api/v1/build"), HttpMethod.POST);
        request.setAdditionalHeader("Content-Type", "application/json");
        request.setRequestBody("{\"values\":{}}");
        JenkinsRule.WebClient client = jenkins.createWebClient().withThrowExceptionOnFailingStatusCode(false);

        Page response = client.getPage(request);

        assertEquals(403, response.getWebResponse().getStatusCode());
        assertEquals(queueLength, jenkins.jenkins.getQueue().getItems().length);
    }

    @Test
    void optionDisplayTextIsEscaped(JenkinsRule jenkins) throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject("escaping");
        FoundryChoiceParameterDefinition definition = new FoundryChoiceParameterDefinition("CHANNEL");
        Option option = new Option("safe");
        option.setLabel("<script>window.pwned=true</script>");
        option.setDescription("<img src=x onerror=alert(1)>");
        definition.setChoices(List.of(option));
        project.addProperty(new ParametersDefinitionProperty(definition));

        HtmlPage page = (HtmlPage) jenkins.createWebClient()
                .withJavaScriptEnabled(false)
                .withThrowExceptionOnFailingStatusCode(false)
                .getPage(project, "build?delay=0sec");

        assertTrue(page.getByXPath("//script[contains(text(),'pwned')]").isEmpty());
        assertTrue(page.getByXPath("//img[@onerror]").isEmpty());
    }

    @Test
    void duplicatePresenceMarkerIsRejected(JenkinsRule jenkins) throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject("duplicate-marker");
        project.addProperty(new ParametersDefinitionProperty(multipleDefinition()));

        Page response = postForm(
                jenkins,
                project,
                List.of(
                        new NameValuePair(AbstractFoundryParameterDefinition.PRESENCE_FIELD, "TARGETS"),
                        new NameValuePair(AbstractFoundryParameterDefinition.PRESENCE_FIELD, "TARGETS"),
                        new NameValuePair("TARGETS", "android")));

        assertEquals(400, response.getWebResponse().getStatusCode());
        assertTrue(response.getWebResponse().getContentAsString().contains("MALFORMED_INPUT"));
    }

    @Test
    void pipelineSymbolCreatesDefinitionAndParamsExposeDeterministicList(JenkinsRule jenkins) throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline");
        job.setDefinition(new CpsFlowDefinition("""
                properties([parameters([
                  foundryChoice(
                    name: 'TARGETS',
                    selectionMode: 'multiple',
                    choices: [[value: 'web'], [value: 'android'], [value: 'ios']],
                    defaultMode: 'explicit',
                    defaultValues: ['web', 'android'],
                    renderer: 'checkbox'
                  )
                ])])
                echo "TARGETS=${params.TARGETS == null ? 'missing' : params.TARGETS.join('|')}"
                """, true));

        jenkins.buildAndAssertSuccess(job);
        var build = jenkins.buildAndAssertSuccess(job);

        jenkins.assertLogContains("TARGETS=android|web", build);
    }

    @Test
    void declarativePipelineParametersDirectiveUsesPublicSymbols(JenkinsRule jenkins) throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "declarative");
        job.setDefinition(new CpsFlowDefinition("""
                pipeline {
                  agent none
                  parameters {
                    foundryChoice(
                      name: 'CHANNEL',
                      choices: [[value: 'staging'], [value: 'production']],
                      defaultMode: 'explicit',
                      defaultValue: 'staging',
                      renderer: 'searchable'
                    )
                    foundryChoice(
                      name: 'TARGETS',
                      selectionMode: 'multiple',
                      choices: [[value: 'web'], [value: 'android'], [value: 'ios']],
                      defaultMode: 'explicit',
                      defaultValues: ['web', 'android'],
                      renderer: 'checkbox',
                      selectedDisplay: 'chips'
                    )
                  }
                  stages {
                    stage('Inspect') {
                      steps {
                        echo "CHANNEL=${params.CHANNEL}"
                        echo "TARGETS=${params.TARGETS.join('|')}"
                      }
                    }
                  }
                }
                """, true));

        var build = jenkins.buildAndAssertSuccess(job);

        jenkins.assertLogContains("CHANNEL=staging", build);
        jenkins.assertLogContains("TARGETS=android|web", build);
        ParametersDefinitionProperty property = job.getProperty(ParametersDefinitionProperty.class);
        assertNotNull(property);
        assertInstanceOf(FoundryChoiceParameterDefinition.class, property.getParameterDefinition("CHANNEL"));
        assertInstanceOf(FoundryChoiceParameterDefinition.class, property.getParameterDefinition("TARGETS"));
    }

    @Test
    void pipelineBuildStepPassesTypedMultiChoiceValue(JenkinsRule jenkins) throws Exception {
        FreeStyleProject downstream = jenkins.createFreeStyleProject("downstream");
        downstream.addProperty(new ParametersDefinitionProperty(multipleDefinition()));
        WorkflowJob upstream = jenkins.createProject(WorkflowJob.class, "upstream");
        upstream.setDefinition(new CpsFlowDefinition("""
                build job: 'downstream', wait: true, parameters: [[
                  $class: 'FoundryChoiceParameterValue',
                  name: 'TARGETS',
                  value: ['web', 'android']
                ]]
                """, true));

        jenkins.buildAndAssertSuccess(upstream);

        FoundryChoiceParameterValue value = assertInstanceOf(
                FoundryChoiceParameterValue.class,
                downstream.getLastBuild().getAction(ParametersAction.class).getParameter("TARGETS"));
        assertEquals(List.of("android", "web"), value.getValue());
    }

    @Test
    void pipelineBuildStepPassesStringChoiceValue(JenkinsRule jenkins) throws Exception {
        FreeStyleProject downstream = jenkins.createFreeStyleProject("choice-downstream");
        FoundryChoiceParameterDefinition definition = new FoundryChoiceParameterDefinition("CHANNEL");
        definition.setChoices(List.of(new Option("staging"), new Option("production")));
        downstream.addProperty(new ParametersDefinitionProperty(definition));
        WorkflowJob upstream = jenkins.createProject(WorkflowJob.class, "choice-upstream");
        upstream.setDefinition(new CpsFlowDefinition("""
                build job: 'choice-downstream', wait: true, parameters: [[
                  $class: 'StringParameterValue',
                  name: 'CHANNEL',
                  value: 'production'
                ]]
                """, true));

        jenkins.buildAndAssertSuccess(upstream);

        StringParameterValue value = assertInstanceOf(
                StringParameterValue.class,
                downstream.getLastBuild().getAction(ParametersAction.class).getParameter("CHANNEL"));
        assertEquals("production", value.getValue());
    }

    @Test
    void pipelineBuildStepPassesDelimitedStringMultiChoiceValue(JenkinsRule jenkins) throws Exception {
        FreeStyleProject downstream = jenkins.createFreeStyleProject("delimited-downstream");
        FoundryChoiceParameterDefinition definition = multipleDefinition();
        definition.setStandardApiInputMode("delimited");
        downstream.addProperty(new ParametersDefinitionProperty(definition));
        WorkflowJob upstream = jenkins.createProject(WorkflowJob.class, "delimited-upstream");
        upstream.setDefinition(new CpsFlowDefinition("""
                build job: 'delimited-downstream', wait: true, parameters: [[
                  $class: 'StringParameterValue',
                  name: 'TARGETS',
                  value: 'web,android'
                ]]
                """, true));

        jenkins.buildAndAssertSuccess(upstream);

        StringParameterValue value = assertInstanceOf(
                StringParameterValue.class,
                downstream.getLastBuild().getAction(ParametersAction.class).getParameter("TARGETS"));
        assertEquals("web,android", value.getValue());
    }

    @Test
    void cliBuildAcceptsJsonArray(JenkinsRule jenkins) throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject("cli");
        project.addProperty(new ParametersDefinitionProperty(multipleDefinition()));

        CLICommandInvoker.Result result = new CLICommandInvoker(jenkins, "build")
                .invokeWithArgs(project.getFullName(), "-p", "TARGETS=[\"web\",\"android\"]", "-s");

        assertEquals(0, result.returnCode(), result.stderr());
        FoundryChoiceParameterValue value = assertInstanceOf(
                FoundryChoiceParameterValue.class,
                project.getLastBuild().getAction(ParametersAction.class).getParameter("TARGETS"));
        assertEquals(List.of("android", "web"), value.getValue());
    }

    @Test
    void noJavaScriptBuildFormContainsPresenceMetadata(JenkinsRule jenkins) throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject("form");
        project.addProperty(new ParametersDefinitionProperty(singleDefinition(), multipleDefinition()));

        HtmlPage page = (HtmlPage) jenkins.createWebClient()
                .withJavaScriptEnabled(false)
                .withThrowExceptionOnFailingStatusCode(false)
                .getPage(project, "build?delay=0sec");

        List<?> markers = page.getByXPath("//input[@name='__parameter_foundry_presence']");
        assertEquals(2, markers.size());
        assertFalse(
                page.getByXPath("//input[@name='CHANNEL' or @name='TARGETS']").isEmpty());
        assertEquals(
                1,
                page.getByXPath("//button[normalize-space()='Build without JavaScript']")
                        .size());
        assertEquals(
                1, page.getByXPath("//noscript//input[@name='Jenkins-Crumb']").size());
    }

    @Test
    void noJavaScriptLargeListIsBounded(JenkinsRule jenkins) throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject("large-form");
        FoundryChoiceParameterDefinition definition = MultipleChoiceFixtures.multipleChoice("TARGETS");
        List<Option> options = new ArrayList<>();
        for (int index = 0; index < 1000; index++) {
            options.add(new Option(String.format("option-%04d", index)));
        }
        definition.setChoices(options);
        definition.setDefaultMode("explicit");
        definition.setDefaultValues(List.of("option-0001", "option-0999"));
        project.addProperty(new ParametersDefinitionProperty(definition));

        HtmlPage page = (HtmlPage) jenkins.createWebClient()
                .withJavaScriptEnabled(false)
                .withThrowExceptionOnFailingStatusCode(false)
                .getPage(project, "build?delay=0sec");

        assertEquals(
                200,
                page.getByXPath("//input[@type='checkbox' and @name='TARGETS']").size());
        assertTrue(page.asNormalizedText().contains("This parameter has 1000 options"));
        assertFalse(page.getByXPath("//input[@value='option-0999']").isEmpty());
    }

    @Test
    void rebuildUsesCurrentDefinitionRendererAndHistoricalValue(JenkinsRule jenkins) throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject("rebuild");
        FoundryChoiceParameterDefinition definition = multipleDefinition();
        definition.setDefaultMode("explicit");
        definition.setDefaultValues(List.of("web", "android"));
        project.addProperty(new ParametersDefinitionProperty(definition));
        FreeStyleBuild build = jenkins.buildAndAssertSuccess(project);

        HtmlPage page = jenkins.createWebClient().withJavaScriptEnabled(false).getPage(build, "rebuild/parameterized");

        assertFalse(page.getByXPath("//input[@type='checkbox' and @value='android']")
                .isEmpty());
        assertFalse(
                page.getByXPath("//input[@type='checkbox' and @value='web']").isEmpty());
    }

    @Test
    void rebuildShowsHistoricalCustomValueInFallbackControls(JenkinsRule jenkins) throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject("rebuild-custom");
        FoundryChoiceParameterDefinition definition = new FoundryChoiceParameterDefinition("RELEASE");
        definition.setChoices(List.of(new Option("stable")));
        definition.setDefaultMode("explicit");
        definition.setDefaultValue("stable");
        definition.setAllowCustomValue(true);
        definition.setCustomValuePattern("release-[0-9]+");
        project.addProperty(new ParametersDefinitionProperty(definition));
        project.scheduleBuild2(
                0,
                new ParametersAction(new FoundryChoiceParameterValue(
                        "RELEASE", "release-42", null, definition.getEnvironmentExport())));
        jenkins.waitUntilNoActivity();
        FreeStyleBuild build = project.getLastBuild();
        assertNotNull(build);
        assertEquals(Result.SUCCESS, build.getResult());

        HtmlPage page = jenkins.createWebClient().getPage(build, "rebuild/parameterized");

        assertFalse(page.getByXPath("//input[@data-up-fallback-json-value='true' and @value='release-42']")
                .isEmpty());
    }

    private static Page postForm(JenkinsRule jenkins, FreeStyleProject project, List<NameValuePair> parameters)
            throws Exception {
        JenkinsRule.WebClient webClient = jenkins.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false)
                .withRedirectEnabled(false);
        URL url = new URL(jenkins.getURL(), project.getUrl() + "buildWithParameters?delay=0sec");
        WebRequest request = new WebRequest(url, HttpMethod.POST);
        request.setRequestParameters(new ArrayList<>(parameters));
        webClient.addCrumb(request);
        return webClient.getPage(request);
    }

    private static JenkinsRule.JSONWebResponse postJson(
            JenkinsRule jenkins, String path, JSONObject body, boolean throwOnFailure) throws Exception {
        JenkinsRule.WebClient webClient = jenkins.createWebClient()
                .withThrowExceptionOnFailingStatusCode(throwOnFailure)
                .withRedirectEnabled(false);
        WebRequest request = new WebRequest(new URL(jenkins.getURL(), path), HttpMethod.POST);
        request.setAdditionalHeader("Content-Type", "application/json");
        CrumbIssuer crumbIssuer = jenkins.jenkins.getCrumbIssuer();
        if (crumbIssuer != null) {
            request.setAdditionalHeader(crumbIssuer.getCrumbRequestField(), crumbIssuer.getCrumb());
        }
        request.setRequestBody(body.toString());
        return new JenkinsRule.JSONWebResponse(webClient.loadWebResponse(request));
    }

    private static FoundryChoiceParameterDefinition singleDefinition() {
        FoundryChoiceParameterDefinition definition = new FoundryChoiceParameterDefinition("CHANNEL");
        definition.setChoices(List.of(new Option("staging"), new Option("production")));
        definition.setDefaultMode("explicit");
        definition.setDefaultValue("staging");
        return definition;
    }

    private static FoundryChoiceParameterDefinition multipleDefinition() {
        FoundryChoiceParameterDefinition definition = MultipleChoiceFixtures.multipleChoice("TARGETS");
        definition.setChoices(List.of(new Option("web"), new Option("android"), new Option("ios")));
        definition.setDefaultMode("explicit");
        definition.setDefaultValues(List.of("web", "android"));
        return definition;
    }

    private static void assertDefinitions(FreeStyleProject project) {
        ParametersDefinitionProperty property = project.getProperty(ParametersDefinitionProperty.class);
        assertNotNull(property);
        List<ParameterDefinition> definitions = property.getParameterDefinitions();
        assertEquals(2, definitions.size());
        FoundryChoiceParameterDefinition single =
                assertInstanceOf(FoundryChoiceParameterDefinition.class, definitions.get(0));
        FoundryChoiceParameterDefinition multiple =
                assertInstanceOf(FoundryChoiceParameterDefinition.class, definitions.get(1));
        assertEquals("staging", single.getDefaultValue());
        assertEquals(List.of("web", "android"), multiple.getDefaultValues());
    }
}
