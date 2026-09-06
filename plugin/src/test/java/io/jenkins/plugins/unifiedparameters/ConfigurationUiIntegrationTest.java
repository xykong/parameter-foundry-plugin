package io.jenkins.plugins.unifiedparameters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudbees.hudson.plugins.folder.Folder;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersDefinitionProperty;
import io.jenkins.plugins.unifiedparameters.companion.git.GitRefProviderDefinition;
import io.jenkins.plugins.unifiedparameters.companion.script.ScriptCatalogEntry;
import io.jenkins.plugins.unifiedparameters.companion.script.ScriptProviderCatalog;
import io.jenkins.plugins.unifiedparameters.definition.UnifiedChoiceParameterDefinition;
import io.jenkins.plugins.unifiedparameters.definition.UnifiedMultiChoiceParameterDefinition;
import io.jenkins.plugins.unifiedparameters.form.UnifiedFormParameterDefinition;
import io.jenkins.plugins.unifiedparameters.model.Option;
import io.jenkins.plugins.unifiedparameters.provider.ManagedConfigProviderDefinition;
import io.jenkins.plugins.unifiedparameters.provider.RestProviderDefinition;
import io.jenkins.plugins.unifiedparameters.provider.ScriptProviderDefinition;
import io.jenkins.plugins.unifiedparameters.provider.StaticProviderDefinition;
import io.jenkins.plugins.unifiedparameters.provider.UnifiedParametersFolderProperty;
import io.jenkins.plugins.unifiedparameters.provider.UnifiedParametersGlobalConfiguration;
import java.net.URL;
import java.util.List;
import org.htmlunit.html.HtmlPage;
import org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.SecureGroovyScript;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ConfigurationUiIntegrationTest {

    @Test
    void configuredSelectValuesRemainSelectedAfterReload(JenkinsRule jenkins) throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject("configuration-ui");

        UnifiedChoiceParameterDefinition choice = new UnifiedChoiceParameterDefinition("CHANNEL");
        choice.setChoices(List.of(new Option("staging")));
        choice.setRenderer("searchable");
        choice.setDefaultMode("explicit");
        choice.setDefaultValue("staging");

        UnifiedMultiChoiceParameterDefinition multiple = new UnifiedMultiChoiceParameterDefinition("TARGETS");
        multiple.setChoices(List.of(new Option("web")));
        multiple.setRenderer("chips");
        multiple.setDefaultMode("explicit");
        multiple.setDefaultValues(List.of("web"));
        multiple.setStandardApiInputMode("delimited");

        UnifiedFormParameterDefinition form = new UnifiedFormParameterDefinition("FORM");
        form.setOutputMode("json");
        form.setEnvironmentExport("none");

        project.addProperty(new ParametersDefinitionProperty(choice, multiple, form));

        HtmlPage page = jenkins.createWebClient().withJavaScriptEnabled(false).getPage(project, "configure");

        assertSelected(
                page,
                "io.jenkins.plugins.unifiedparameters.definition.UnifiedChoiceParameterDefinition",
                "renderer",
                "searchable");
        assertSelected(
                page,
                "io.jenkins.plugins.unifiedparameters.definition.UnifiedChoiceParameterDefinition",
                "defaultMode",
                "explicit");
        assertSelected(
                page,
                "io.jenkins.plugins.unifiedparameters.definition.UnifiedMultiChoiceParameterDefinition",
                "renderer",
                "chips");
        assertSelected(
                page,
                "io.jenkins.plugins.unifiedparameters.definition.UnifiedMultiChoiceParameterDefinition",
                "standardApiInputMode",
                "delimited");
        assertSelected(
                page, "io.jenkins.plugins.unifiedparameters.form.UnifiedFormParameterDefinition", "outputMode", "json");
        assertSelected(
                page,
                "io.jenkins.plugins.unifiedparameters.form.UnifiedFormParameterDefinition",
                "environmentExport",
                "none");
    }

    @Test
    void requiredReactiveWithoutDefaultShowsConfigurationWarning(JenkinsRule jenkins) throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject("configuration-warning");
        UnifiedChoiceParameterDefinition environment = new UnifiedChoiceParameterDefinition("ENVIRONMENT");
        environment.setChoices(List.of(new Option("staging")));
        UnifiedChoiceParameterDefinition artifact = new UnifiedChoiceParameterDefinition("ARTIFACT");
        artifact.setProviderId("artifacts");
        artifact.setDependsOn(List.of("ENVIRONMENT"));
        artifact.setRequired(true);
        project.addProperty(new ParametersDefinitionProperty(environment, artifact));

        HtmlPage page = jenkins.createWebClient().withJavaScriptEnabled(false).getPage(project, "configure");

        assertTrue(page.asNormalizedText().contains("Required reactive parameter has no default"));
        assertTrue(page.asNormalizedText().contains("must submit it explicitly"));
    }

    @Test
    void globalProviderConfigurationIsRendered(JenkinsRule jenkins) throws Exception {
        UnifiedParametersGlobalConfiguration.get()
                .setProviders(
                        List.of(new ScriptProviderDefinition("script-options", "sandbox-groovy", "catalog:releases")));
        ScriptProviderCatalog.get()
                .setEntries(
                        List.of(new ScriptCatalogEntry("releases", new SecureGroovyScript("return ['stable']", true))));
        JenkinsRule.WebClient webClient = jenkins.createWebClient();
        HtmlPage page = webClient.getPage(new URL(jenkins.getURL(), "configure"));
        webClient.waitForBackgroundJavaScript(1000);

        assertTrue(page.asNormalizedText().contains("Unified Parameters feature switches"));
        assertTrue(page.asNormalizedText().contains("Groovy Script"));
        assertTrue(page.asNormalizedText().contains("Use Groovy Sandbox"));
        assertEquals(
                4,
                page.getByXPath(
                                "//input[contains(@name, 'migrationAdaptersEnabled') or contains(@name, 'scriptProviderEnabled') or contains(@name, 'gitRefsProviderEnabled') or contains(@name, 'accessibleChoiceGridEnabled')]")
                        .size());
        assertTrue(page.getByXPath("//h1[normalize-space()='Oops!']").isEmpty());
    }

    @Test
    void providerEnumValuesRemainSelectedAfterReload(JenkinsRule jenkins) throws Exception {
        Folder folder = jenkins.createProject(Folder.class, "provider-configuration");

        GitRefProviderDefinition git = new GitRefProviderDefinition("refs", "profile");
        git.setRefType("tag");
        git.setSortOrder("descending");
        git.setFailurePolicy("empty");

        RestProviderDefinition rest = new RestProviderDefinition("rest", "profile", "options");
        rest.setFormat("json");
        rest.setFailurePolicy("last-known-good");

        ManagedConfigProviderDefinition managed = new ManagedConfigProviderDefinition("managed", "config");
        managed.setFormat("xml");
        managed.setFailurePolicy("empty");

        StaticProviderDefinition staticProvider = new StaticProviderDefinition("static");
        staticProvider.setFailurePolicy("empty");

        UnifiedParametersFolderProperty property = new UnifiedParametersFolderProperty();
        property.setProviders(List.of(git, rest, managed, staticProvider));
        folder.addProperty(property);

        HtmlPage page = jenkins.createWebClient().withJavaScriptEnabled(false).getPage(folder, "configure");

        assertSelectedProviderOption(
                page,
                "io.jenkins.plugins.unifiedparameters.companion.git.GitRefProviderDefinition",
                "refs",
                "refType",
                "tag");
        assertSelectedProviderOption(
                page,
                "io.jenkins.plugins.unifiedparameters.companion.git.GitRefProviderDefinition",
                "refs",
                "sortOrder",
                "descending");
        assertSelectedProviderOption(
                page,
                "io.jenkins.plugins.unifiedparameters.companion.git.GitRefProviderDefinition",
                "refs",
                "failurePolicy",
                "empty");
        assertSelectedProviderOption(
                page, "io.jenkins.plugins.unifiedparameters.provider.RestProviderDefinition", "rest", "format", "json");
        assertSelectedProviderOption(
                page,
                "io.jenkins.plugins.unifiedparameters.provider.RestProviderDefinition",
                "rest",
                "failurePolicy",
                "last-known-good");
        assertSelectedProviderOption(
                page,
                "io.jenkins.plugins.unifiedparameters.provider.ManagedConfigProviderDefinition",
                "managed",
                "format",
                "xml");
        assertSelectedProviderOption(
                page,
                "io.jenkins.plugins.unifiedparameters.provider.ManagedConfigProviderDefinition",
                "managed",
                "failurePolicy",
                "empty");
        assertSelectedProviderOption(
                page,
                "io.jenkins.plugins.unifiedparameters.provider.StaticProviderDefinition",
                "static",
                "failurePolicy",
                "empty");
    }

    private static void assertSelected(HtmlPage page, String descriptorId, String field, String value) {
        String selected = "//div[@descriptorid='"
                + descriptorId
                + "']//select[@name='parameter."
                + field
                + "']/option[@selected='selected' and @value='"
                + value
                + "']";
        String invalid = "//div[@descriptorid='"
                + descriptorId
                + "']//select[@name='parameter."
                + field
                + "']/option[@selected='false']";
        assertEquals(1, page.getByXPath(selected).size(), descriptorId + " " + field);
        assertTrue(page.getByXPath(invalid).isEmpty(), descriptorId + " " + field);
    }

    private static void assertSelectedProviderOption(
            HtmlPage page, String descriptorId, String providerId, String field, String value) {
        String selected = "//div[@descriptorid='"
                + descriptorId
                + "'][.//input[@name='_.id' and @value='"
                + providerId
                + "']]//select[@name='_."
                + field
                + "']/option[@selected='selected' and @value='"
                + value
                + "']";
        assertEquals(1, page.getByXPath(selected).size(), providerId + " " + field);
    }
}
