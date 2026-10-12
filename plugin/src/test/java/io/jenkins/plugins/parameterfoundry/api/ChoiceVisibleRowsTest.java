package io.jenkins.plugins.parameterfoundry.api;

import static org.junit.jupiter.api.Assertions.*;

import hudson.model.ParametersDefinitionProperty;
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition;
import io.jenkins.plugins.parameterfoundry.validation.DefinitionValidationService;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ChoiceVisibleRowsTest {
    @Test
    void explicitRowsPersistThroughConfigurationAndFallbackMarkup(JenkinsRule j) throws Exception {
        var job = j.createFreeStyleProject();
        var d = new FoundryChoiceParameterDefinition("PLATFORMS");
        d.setSelectionMode("multiple");
        d.setRenderer("list");
        d.setChoices(java.util.List.of(new io.jenkins.plugins.parameterfoundry.model.Option("he")));
        d.setVisibleItemCount(16);
        job.addProperty(new ParametersDefinitionProperty(d));
        j.configRoundtrip(job);
        var restored = (FoundryChoiceParameterDefinition)
                job.getProperty(ParametersDefinitionProperty.class).getParameterDefinition("PLATFORMS");
        assertEquals(16, restored.getVisibleItemCount());
        assertTrue(job.isParameterized());
        var page = j.createWebClient()
                .withJavaScriptEnabled(false)
                .withThrowExceptionOnFailingStatusCode(false)
                .goTo(job.getUrl() + "build?delay=0sec");
        var select = page.getDocumentElement().getFirstByXPath("//select[@multiple and @data-up-native-value]");
        assertNotNull(select);
        assertEquals("16", ((org.htmlunit.html.HtmlSelect) select).getAttribute("size"));
    }

    @Test
    void singleListSnippetAndDefaultCopyRetainRows(JenkinsRule j) {
        var d = new FoundryChoiceParameterDefinition("PLATFORMS");
        d.setChoices(java.util.List.of(new io.jenkins.plugins.parameterfoundry.model.Option("he")));
        d.setRenderer("list");
        d.setVisibleItemCount(16);
        assertTrue(io.jenkins.plugins.parameterfoundry.migration.MigrationSnippets.forTarget(d)
                .contains("visibleItemCount: 16"));
        var model =
                org.jenkinsci.plugins.structs.describable.DescribableModel.of(FoundryChoiceParameterDefinition.class);
        var rebound = model.instantiate(model.uninstantiate2(d).toMap());
        assertEquals(16, rebound.getVisibleItemCount());
        var copied = (FoundryChoiceParameterDefinition)
                d.copyWithDefaultValue(new hudson.model.StringParameterValue("PLATFORMS", "he"));
        assertEquals(16, copied.getVisibleItemCount());
    }

    @Test
    void rowsHaveBoundedValidationAndAutomaticDefault() {
        var d = new FoundryChoiceParameterDefinition("PLATFORMS");
        assertEquals(0, d.getVisibleItemCount());
        for (int value : new int[] {-1, 1001}) {
            d.setVisibleItemCount(value);
            assertTrue(DefinitionValidationService.validateStoredDefinition(d)
                    .getErrors()
                    .contains("Visible item count must be between 0 and 1000."));
        }
        d.setVisibleItemCount(16);
        assertEquals(16, d.getListVisibleRows());
    }
}
