package io.jenkins.plugins.unifiedparameters.migration.extendedchoice;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cwctravel.hudson.plugins.extended_choice_parameter.ExtendedChoiceParameterDefinition;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersDefinitionProperty;
import io.jenkins.plugins.unifiedparameters.api.UnifiedParametersJobAction;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ExtendedChoiceMigrationUiTest {

    @Test
    void jobActionShowsClassificationDecisionsTargetAndVerificationFixture(JenkinsRule jenkins) throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject("migration-preview");
        project.addProperty(new ParametersDefinitionProperty(staticMultiple()));
        UnifiedParametersJobAction action = project.getAction(UnifiedParametersJobAction.class);

        assertNotNull(action);
        assertTrue(action.hasMigrationCandidates());

        HtmlPage page = jenkins.createWebClient().withJavaScriptEnabled(false).getPage(project, "unified-parameters/");
        String text = page.asNormalizedText();

        assertTrue(text.contains("Class B"));
        assertTrue(text.contains("UnifiedChoiceParameterDefinition"));
        assertTrue(text.contains("Confirm that selection order is not part of the business requirement."));
        assertTrue(text.contains("unifiedChoice"));
        assertTrue(text.contains("selectionMode: 'multiple'"));
        assertTrue(text.contains("Standard REST, Typed REST, CLI, and Pipeline build"));
        assertTrue(text.contains("does not modify the Job"));
    }

    private static ExtendedChoiceParameterDefinition staticMultiple() {
        ExtendedChoiceParameterDefinition source = new ExtendedChoiceParameterDefinition(
                "LEGACY_TARGETS",
                ExtendedChoiceParameterDefinition.PARAMETER_TYPE_CHECK_BOX,
                "android,ios,web",
                "android,ios",
                null);
        source.setDescription("Synthetic source");
        return source;
    }
}
