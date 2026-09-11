package io.jenkins.plugins.unifiedparameters.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.cli.CLICommandInvoker;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import io.jenkins.plugins.unifiedparameters.MultipleChoiceFixtures;
import io.jenkins.plugins.unifiedparameters.definition.AbstractUnifiedParameterDefinition;
import io.jenkins.plugins.unifiedparameters.definition.UnifiedChoiceParameterDefinition;
import io.jenkins.plugins.unifiedparameters.definition.UnifiedChoiceParameterValue;
import io.jenkins.plugins.unifiedparameters.model.Option;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.sf.json.JSONArray;
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
class ProviderIntegrationTest {

    @Test
    void staticSharedProviderHasStandardTypedCliAndHealthParity(JenkinsRule jenkins) throws Exception {
        StaticProviderDefinition provider = new StaticProviderDefinition("releases");
        provider.setOptions(List.of(new Option("web"), new Option("android"), new Option("ios")));
        UnifiedParametersGlobalConfiguration.get().setProviders(List.of(provider));

        FreeStyleProject standard = providerJob(jenkins, "provider-standard");
        Page standardResponse = postForm(
                jenkins,
                standard,
                List.of(
                        new NameValuePair(AbstractUnifiedParameterDefinition.PRESENCE_FIELD, "TARGETS"),
                        new NameValuePair("TARGETS", "web"),
                        new NameValuePair("TARGETS", "android")));
        assertEquals(201, standardResponse.getWebResponse().getStatusCode());
        jenkins.waitUntilNoActivity();
        assertCanonical(standard, List.of("android", "web"));

        FreeStyleProject typed = providerJob(jenkins, "provider-typed");
        JSONObject values = new JSONObject();
        values.put("TARGETS", JSONArray.fromObject(List.of("ios", "android")));
        JSONObject body = new JSONObject();
        body.put("values", values);
        JenkinsRule.JSONWebResponse typedResponse =
                postJson(jenkins, typed.getUrl() + "unified-parameters/api/v1/build", body);
        assertEquals(201, typedResponse.getStatusCode());
        jenkins.waitUntilNoActivity();
        assertCanonical(typed, List.of("android", "ios"));

        FreeStyleProject cli = providerJob(jenkins, "provider-cli");
        CLICommandInvoker.Result cliResult = new CLICommandInvoker(jenkins, "build")
                .invokeWithArgs(cli.getFullName(), "-p", "TARGETS=[\"web\",\"ios\"]", "-s");
        assertEquals(0, cliResult.returnCode(), cliResult.stderr());
        assertCanonical(cli, List.of("ios", "web"));

        JSONObject resolveBody = new JSONObject();
        resolveBody.put("parameter", "TARGETS");
        JenkinsRule.JSONWebResponse resolve =
                postJson(jenkins, typed.getUrl() + "unified-parameters/api/v1/resolve", resolveBody);
        assertEquals(3, resolve.getJSONObject().getJSONArray("options").size());
        assertEquals(
                JSONArray.fromObject(List.of("android")),
                resolve.getJSONObject().getJSONArray("default"));
        assertTrue(resolve.getJSONObject().getString("providerDigest").startsWith("sha256:"));
        assertTrue(resolve.getJSONObject().getString("optionSetDigest").startsWith("sha256:"));

        JenkinsRule.JSONWebResponse health =
                jenkins.createWebClient().getJSON(typed.getUrl() + "unified-parameters/api/v1/providers/health");
        assertEquals(
                true,
                health.getJSONObject()
                        .getJSONArray("providers")
                        .getJSONObject(0)
                        .getBoolean("healthy"));
    }

    @Test
    void providerBackedBuildFormRequiresEnhancedUiOrTypedApi(JenkinsRule jenkins) throws Exception {
        StaticProviderDefinition provider = new StaticProviderDefinition("dynamic");
        provider.setOptions(List.of(new Option("one"), new Option("two")));
        UnifiedParametersGlobalConfiguration.get().setProviders(List.of(provider));
        FreeStyleProject project = providerJob(jenkins, "provider-form");

        HtmlPage page = (HtmlPage) jenkins.createWebClient()
                .withJavaScriptEnabled(false)
                .withThrowExceptionOnFailingStatusCode(false)
                .getPage(project, "build?delay=0sec");

        assertTrue(page.asNormalizedText().contains("uses shared Provider dynamic"));
        assertTrue(page.asNormalizedText().contains("Loading options..."));
        assertTrue(page.getByXPath("//button[normalize-space()='Build without JavaScript']")
                .isEmpty());
        assertFalse(page.getByXPath("//input[@name='__unified_parameters_presence']")
                .isEmpty());
    }

    @Test
    void providerBackedBuildFormRendersBeforeProviderResolution(JenkinsRule jenkins) throws Exception {
        AtomicInteger loadCount = new AtomicInteger();
        UnifiedParametersGlobalConfiguration.get()
                .setProviders(List.of(new CountingProvider("slow-dynamic", loadCount)));
        FreeStyleProject project = jenkins.createFreeStyleProject("provider-form-loading");
        UnifiedChoiceParameterDefinition definition = MultipleChoiceFixtures.multipleChoice("TARGETS");
        definition.setProviderId("slow-dynamic");
        definition.setDefaultMode("first");
        project.addProperty(new ParametersDefinitionProperty(definition));

        HtmlPage page = (HtmlPage) jenkins.createWebClient()
                .withJavaScriptEnabled(false)
                .withThrowExceptionOnFailingStatusCode(false)
                .getPage(project, "build?delay=0sec");

        assertEquals(0, loadCount.get());
        assertTrue(page.asNormalizedText().contains("Loading options..."));
    }

    @Test
    void providerBackedBuildFormKeepsBuildButtonInInitialMarkup(JenkinsRule jenkins) throws Exception {
        StaticProviderDefinition provider = new StaticProviderDefinition("button-gated");
        provider.setOptions(List.of(new Option("one")));
        UnifiedParametersGlobalConfiguration.get().setProviders(List.of(provider));
        FreeStyleProject project = providerJob(jenkins, "provider-button-gated");
        UnifiedChoiceParameterDefinition definition = (UnifiedChoiceParameterDefinition)
                project.getProperty(ParametersDefinitionProperty.class).getParameterDefinition("TARGETS");
        definition.setProviderId("button-gated");

        HtmlPage page = (HtmlPage) jenkins.createWebClient()
                .withJavaScriptEnabled(false)
                .withThrowExceptionOnFailingStatusCode(false)
                .getPage(project, "build?delay=0sec");

        assertEquals(1, page.getByXPath("//button[normalize-space()='Build']").size());
        assertTrue(page.asNormalizedText().contains("Loading options..."));
    }

    private static FreeStyleProject providerJob(JenkinsRule jenkins, String name) throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject(name);
        UnifiedChoiceParameterDefinition definition = MultipleChoiceFixtures.multipleChoice("TARGETS");
        definition.setProviderId("releases");
        if ("provider-form".equals(name)) {
            definition.setProviderId("dynamic");
        }
        definition.setDefaultMode("explicit");
        definition.setDefaultValues(List.of("android"));
        if ("provider-form".equals(name)) {
            definition.setDefaultValues(List.of("one"));
        }
        project.addProperty(new ParametersDefinitionProperty(definition));
        return project;
    }

    private static final class CountingProvider extends SharedProviderDefinition {
        private transient AtomicInteger loadCount;

        CountingProvider(String id, AtomicInteger loadCount) {
            super(id);
            this.loadCount = loadCount;
        }

        @Override
        public List<Option> load(ProviderLoadContext context) {
            loadCount.incrementAndGet();
            return List.of(new Option("one"));
        }

        @Override
        protected String providerFingerprint() {
            return "counting-provider";
        }
    }

    private static void assertCanonical(FreeStyleProject project, List<String> expected) {
        UnifiedChoiceParameterValue value = assertInstanceOf(
                UnifiedChoiceParameterValue.class,
                project.getLastBuild().getAction(ParametersAction.class).getParameter("TARGETS"));
        assertEquals(expected, value.getValues());
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
