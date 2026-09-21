package io.jenkins.plugins.parameterfoundry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudbees.hudson.plugins.folder.Folder;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersDefinitionProperty;
import io.jenkins.plugins.parameterfoundry.companion.git.GitRefProviderDefinition;
import io.jenkins.plugins.parameterfoundry.companion.script.ScriptCatalogEntry;
import io.jenkins.plugins.parameterfoundry.companion.script.ScriptProviderCatalog;
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition;
import io.jenkins.plugins.parameterfoundry.form.FoundryFormParameterDefinition;
import io.jenkins.plugins.parameterfoundry.model.Option;
import io.jenkins.plugins.parameterfoundry.provider.ManagedConfigProviderDefinition;
import io.jenkins.plugins.parameterfoundry.provider.ParameterFoundryFolderProperty;
import io.jenkins.plugins.parameterfoundry.provider.ParameterFoundryGlobalConfiguration;
import io.jenkins.plugins.parameterfoundry.provider.RestProviderDefinition;
import io.jenkins.plugins.parameterfoundry.provider.ScriptProviderDefinition;
import io.jenkins.plugins.parameterfoundry.provider.StaticProviderDefinition;
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

        FoundryChoiceParameterDefinition choice = new FoundryChoiceParameterDefinition("CHANNEL");
        choice.setChoices(List.of(new Option("staging")));
        choice.setRenderer("searchable");
        choice.setDefaultMode("explicit");
        choice.setDefaultValue("staging");

        FoundryChoiceParameterDefinition multiple = MultipleChoiceFixtures.multipleChoice("TARGETS");
        multiple.setChoices(List.of(new Option("web")));
        multiple.setRenderer("checkbox");
        multiple.setSelectedDisplay("chips");
        multiple.setDefaultMode("explicit");
        multiple.setDefaultValues(List.of("web"));
        multiple.setStandardApiInputMode("delimited");

        FoundryFormParameterDefinition form = new FoundryFormParameterDefinition("FORM");
        form.setOutputMode("json");
        form.setEnvironmentExport("none");

        project.addProperty(new ParametersDefinitionProperty(choice, multiple, form));

        HtmlPage page = jenkins.createWebClient().withJavaScriptEnabled(false).getPage(project, "configure");

        assertSelected(
                page,
                "io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition",
                "CHANNEL",
                "renderer",
                "searchable");
        assertSelected(
                page,
                "io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition",
                "CHANNEL",
                "defaultMode",
                "explicit");
        assertSelected(
                page,
                "io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition",
                "TARGETS",
                "renderer",
                "checkbox");
        assertSelected(
                page,
                "io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition",
                "TARGETS",
                "selectedDisplay",
                "chips");
        assertSelected(
                page,
                "io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition",
                "TARGETS",
                "standardApiInputMode",
                "delimited");
        assertSelected(
                page,
                "io.jenkins.plugins.parameterfoundry.form.FoundryFormParameterDefinition",
                "FORM",
                "outputMode",
                "json");
        assertSelected(
                page,
                "io.jenkins.plugins.parameterfoundry.form.FoundryFormParameterDefinition",
                "FORM",
                "environmentExport",
                "none");
    }

    @Test
    void foundryChoiceMultipleModeRendersMultipleFields(JenkinsRule jenkins) throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject("configuration-ui-multiple");

        FoundryChoiceParameterDefinition multiple = new FoundryChoiceParameterDefinition("TARGETS");
        multiple.setSelectionMode("multiple");
        multiple.setChoices(List.of(new Option("web")));
        multiple.setRenderer("checkbox");
        multiple.setSelectedDisplay("chips");
        multiple.setDefaultMode("explicit");
        multiple.setDefaultValues(List.of("web"));
        multiple.setStandardApiInputMode("delimited");

        project.addProperty(new ParametersDefinitionProperty(multiple));

        HtmlPage page = jenkins.createWebClient().withJavaScriptEnabled(false).getPage(project, "configure");

        assertSelected(
                page,
                "io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition",
                "TARGETS",
                "selectionMode",
                "multiple");
        assertSelected(
                page,
                "io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition",
                "TARGETS",
                "renderer",
                "checkbox");
        assertSelected(
                page,
                "io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition",
                "TARGETS",
                "selectedDisplay",
                "chips");
        assertSelected(
                page,
                "io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition",
                "TARGETS",
                "standardApiInputMode",
                "delimited");
    }

    @Test
    void requiredReactiveWithoutDefaultShowsConfigurationWarning(JenkinsRule jenkins) throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject("configuration-warning");
        FoundryChoiceParameterDefinition environment = new FoundryChoiceParameterDefinition("ENVIRONMENT");
        environment.setChoices(List.of(new Option("staging")));
        FoundryChoiceParameterDefinition artifact = new FoundryChoiceParameterDefinition("ARTIFACT");
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
        ParameterFoundryGlobalConfiguration.get()
                .setProviders(
                        List.of(new ScriptProviderDefinition("script-options", "sandbox-groovy", "catalog:releases")));
        ScriptProviderCatalog.get()
                .setEntries(
                        List.of(new ScriptCatalogEntry("releases", new SecureGroovyScript("return ['stable']", true))));
        JenkinsRule.WebClient webClient = jenkins.createWebClient();
        HtmlPage page = webClient.getPage(new URL(jenkins.getURL(), "configure"));
        webClient.waitForBackgroundJavaScript(1000);

        assertTrue(page.asNormalizedText().contains("Parameter Foundry feature switches"));
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

        ParameterFoundryFolderProperty property = new ParameterFoundryFolderProperty();
        property.setProviders(List.of(git, rest, managed, staticProvider));
        folder.addProperty(property);

        HtmlPage page = jenkins.createWebClient().withJavaScriptEnabled(false).getPage(folder, "configure");

        assertSelectedProviderOption(
                page,
                "io.jenkins.plugins.parameterfoundry.companion.git.GitRefProviderDefinition",
                "refs",
                "refType",
                "tag");
        assertSelectedProviderOption(
                page,
                "io.jenkins.plugins.parameterfoundry.companion.git.GitRefProviderDefinition",
                "refs",
                "sortOrder",
                "descending");
        assertSelectedProviderOption(
                page,
                "io.jenkins.plugins.parameterfoundry.companion.git.GitRefProviderDefinition",
                "refs",
                "failurePolicy",
                "empty");
        assertSelectedProviderOption(
                page, "io.jenkins.plugins.parameterfoundry.provider.RestProviderDefinition", "rest", "format", "json");
        assertSelectedProviderOption(
                page,
                "io.jenkins.plugins.parameterfoundry.provider.RestProviderDefinition",
                "rest",
                "failurePolicy",
                "last-known-good");
        assertSelectedProviderOption(
                page,
                "io.jenkins.plugins.parameterfoundry.provider.ManagedConfigProviderDefinition",
                "managed",
                "format",
                "xml");
        assertSelectedProviderOption(
                page,
                "io.jenkins.plugins.parameterfoundry.provider.ManagedConfigProviderDefinition",
                "managed",
                "failurePolicy",
                "empty");
        assertSelectedProviderOption(
                page,
                "io.jenkins.plugins.parameterfoundry.provider.StaticProviderDefinition",
                "static",
                "failurePolicy",
                "empty");
    }

    private static void assertSelected(
            HtmlPage page, String descriptorId, String parameterName, String field, String value) {
        String panel = "//div[@descriptorid='"
                + descriptorId
                + "'][.//input[@name='parameter.name' and @value='"
                + parameterName
                + "']]";
        String selected = panel
                + "//select[@name='parameter."
                + field
                + "']/option[@selected='selected' and @value='"
                + value
                + "']";
        String invalid = panel + "//select[@name='parameter." + field + "']/option[@selected='false']";
        assertEquals(1, page.getByXPath(selected).size(), descriptorId + " " + parameterName + " " + field);
        assertTrue(page.getByXPath(invalid).isEmpty(), descriptorId + " " + parameterName + " " + field);
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
