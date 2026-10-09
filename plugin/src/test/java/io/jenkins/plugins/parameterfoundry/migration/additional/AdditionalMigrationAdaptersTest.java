package io.jenkins.plugins.parameterfoundry.migration.additional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bluersw.CheckboxParameterDefinition;
import com.github.cyanbaz.jenkins.plugins.jsonparameter.JsonParameterDefinition;
import com.github.cyanbaz.jenkins.plugins.jsonparameter.JsonParameterReferenceDefinition;
import de.westemeyer.plugins.multiselect.MultiselectParameterDefinition;
import hudson.plugins.validating_string_parameter.ValidatingStringParameterDefinition;
import io.jenkins.plugins.editable_choice.EditableChoiceParameterDefinition;
import io.jenkins.plugins.file_parameters.StashedFileParameterDefinition;
import io.jenkins.plugins.json_editor_parameter.JsonEditorParameterDefinition;
import io.jenkins.plugins.parameterfoundry.migration.MigrationClassification;
import io.jenkins.plugins.parameterfoundry.migration.SourceMigrationAdapter;
import io.jenkins.plugins.restlistparam.RestListParameterDefinition;
import java.util.List;
import jp.ikedam.jenkins.plugins.extensible_choice_parameter.ExtensibleChoiceParameterDefinition;
import org.jenkinsci.plugins.uiparameter.HtmlFormParameterDefinition;
import org.junit.jupiter.api.Test;

class AdditionalMigrationAdaptersTest {

    @Test
    void allResearchedSourceFamiliesProduceNonExecutingPlans() {
        List<SourceCase> cases = List.of(
                new SourceCase(new ExtensibleChoiceMigrationAdapter(), new ExtensibleChoiceParameterDefinition("EXT")),
                new SourceCase(new EditableChoiceMigrationAdapter(), new EditableChoiceParameterDefinition("EDIT")),
                new SourceCase(new CustomCheckboxMigrationAdapter(), new CheckboxParameterDefinition("CHECK")),
                new SourceCase(new MultiselectMigrationAdapter(), new MultiselectParameterDefinition("MULTI")),
                new SourceCase(new JsonEditorMigrationAdapter(), new JsonEditorParameterDefinition("JSON_EDITOR")),
                new SourceCase(new HtmlParametersMigrationAdapter(), new HtmlFormParameterDefinition("HTML")),
                new SourceCase(new JsonParameterMigrationAdapter(), new JsonParameterDefinition("JSON")),
                new SourceCase(new RestListMigrationAdapter(), new RestListParameterDefinition("REST")),
                new SourceCase(
                        new ValidatingStringMigrationAdapter(), new ValidatingStringParameterDefinition("VALIDATING")));
        cases = new java.util.ArrayList<>(cases);
        cases.add(new SourceCase(new StashedFileMigrationAdapter(), new StashedFileParameterDefinition("UPLOAD")));

        for (SourceCase sourceCase : cases) {
            var plan = sourceCase.adapter.inspect(sourceCase.definition);
            if (sourceCase.adapter instanceof StashedFileMigrationAdapter) {
                assertEquals(MigrationClassification.B, plan.getClassification());
                assertTrue(plan.getReason().contains("multipart"));
                assertTrue(
                        plan.getTarget()
                                instanceof
                                io.jenkins.plugins.parameterfoundry.companion.file
                                        .FoundryStashedFileParameterDefinition);
            } else {
                assertEquals(MigrationClassification.C, plan.getClassification());
            }
            if (!(sourceCase.adapter instanceof StashedFileMigrationAdapter)) {
                assertNull(plan.getTarget());
            }
            assertTrue(plan.getVerificationFixture().contains("Rebuild"));
            if (plan.getTargetSkeleton() != null) {
                assertTrue(plan.getTargetSkeleton().contains("TODO"));
                assertTrue(plan.getReason().contains("never executes"));
            }
        }
    }

    @Test
    void jsonReferenceIsCoveredByTheSameExplicitBoundary() {
        var adapter = new JsonParameterMigrationAdapter();

        var plan = adapter.inspect(new JsonParameterReferenceDefinition("REFERENCE"));

        assertTrue(plan.getTargetSkeleton().contains("foundryForm"));
        assertTrue(plan.getDecisions().stream().anyMatch(decision -> decision.contains("Provider versus Form")));
    }

    private record SourceCase(SourceMigrationAdapter adapter, StubParameterDefinition definition) {}
}
