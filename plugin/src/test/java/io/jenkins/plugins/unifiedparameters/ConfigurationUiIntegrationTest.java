package io.jenkins.plugins.unifiedparameters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.FreeStyleProject;
import hudson.model.ParametersDefinitionProperty;
import io.jenkins.plugins.unifiedparameters.definition.UnifiedChoiceParameterDefinition;
import io.jenkins.plugins.unifiedparameters.definition.UnifiedMultiChoiceParameterDefinition;
import io.jenkins.plugins.unifiedparameters.form.UnifiedFormParameterDefinition;
import io.jenkins.plugins.unifiedparameters.model.Option;
import java.net.URL;
import java.util.List;
import org.htmlunit.html.HtmlPage;
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
    void globalProviderConfigurationIsRendered(JenkinsRule jenkins) throws Exception {
        HtmlPage page =
                jenkins.createWebClient().withJavaScriptEnabled(false).getPage(new URL(jenkins.getURL(), "configure"));

        assertTrue(page.asNormalizedText().contains("Unified Parameters feature switches"));
        assertEquals(
                4,
                page.getByXPath(
                                "//input[contains(@name, 'migrationAdaptersEnabled') or contains(@name, 'scriptProviderEnabled') or contains(@name, 'gitRefsProviderEnabled') or contains(@name, 'accessibleChoiceGridEnabled')]")
                        .size());
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
}
