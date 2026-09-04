package io.jenkins.plugins.unifiedparameters.migration.extendedchoice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cwctravel.hudson.plugins.extended_choice_parameter.ExtendedChoiceParameterDefinition;
import hudson.model.FreeStyleProject;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import io.jenkins.plugins.unifiedparameters.definition.UnifiedChoiceParameterDefinition;
import io.jenkins.plugins.unifiedparameters.evaluation.ErrorCode;
import io.jenkins.plugins.unifiedparameters.evaluation.EvaluationException;
import io.jenkins.plugins.unifiedparameters.migration.MigrationCoordinator;
import io.jenkins.plugins.unifiedparameters.migration.MigrationPreviewHandle;
import io.jenkins.plugins.unifiedparameters.migration.MigrationRecord;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ExtendedChoiceMigrationCoordinatorTest {

    @Test
    void cloneApplyAndRollbackPreserveSourceAndAuditBackup(JenkinsRule jenkins) throws Exception {
        FreeStyleProject source = jenkins.createFreeStyleProject("legacy");
        source.addProperty(new ParametersDefinitionProperty(staticSingle()));
        MigrationPreviewHandle preview = MigrationCoordinator.preview(source, "LEGACY_CHANNEL");

        MigrationRecord record = MigrationCoordinator.cloneAndApply(source, "unified", preview, List.of());

        assertInstanceOf(ExtendedChoiceParameterDefinition.class, definition(source, "LEGACY_CHANNEL"));
        FreeStyleProject clone = jenkins.jenkins.getItemByFullName("unified", FreeStyleProject.class);
        assertNotNull(clone);
        assertInstanceOf(UnifiedChoiceParameterDefinition.class, definition(clone, "LEGACY_CHANNEL"));
        assertEquals("legacy", record.getSourceJob());
        assertEquals("unified", record.getTargetJob());
        assertEquals("APPLIED", record.getStatus());
        assertTrue(Files.isRegularFile(jenkins.jenkins
                .getRootDir()
                .toPath()
                .resolve("unified-parameters/migration-backups")
                .resolve(record.getBackupFile())));

        MigrationCoordinator.rollback(clone, record.getId());

        assertEquals("ROLLED_BACK", record.getStatus());
        assertInstanceOf(ExtendedChoiceParameterDefinition.class, definition(clone, "LEGACY_CHANNEL"));
    }

    @Test
    void compareBeforeWriteAndRollbackConflictRefuseDrift(JenkinsRule jenkins) throws Exception {
        FreeStyleProject source = jenkins.createFreeStyleProject("drift-source");
        ExtendedChoiceParameterDefinition original = staticSingle();
        source.addProperty(new ParametersDefinitionProperty(original));
        MigrationPreviewHandle stalePreview = MigrationCoordinator.preview(source, "LEGACY_CHANNEL");
        original.setDescription("Changed after preview");
        source.save();

        EvaluationException drift = assertThrows(
                EvaluationException.class, () -> MigrationCoordinator.applyInPlace(source, stalePreview, List.of()));
        assertEquals(ErrorCode.CONFIGURATION_CHANGED, drift.getCode());

        MigrationPreviewHandle currentPreview = MigrationCoordinator.preview(source, "LEGACY_CHANNEL");
        MigrationRecord record = MigrationCoordinator.cloneAndApply(source, "drift-target", currentPreview, List.of());
        FreeStyleProject target = jenkins.jenkins.getItemByFullName("drift-target", FreeStyleProject.class);
        target.setDescription("Manual edit after migration");
        target.save();

        EvaluationException rollbackConflict =
                assertThrows(EvaluationException.class, () -> MigrationCoordinator.rollback(target, record.getId()));
        assertEquals(ErrorCode.CONFIGURATION_CHANGED, rollbackConflict.getCode());
        assertInstanceOf(UnifiedChoiceParameterDefinition.class, definition(target, "LEGACY_CHANNEL"));
    }

    private static ParameterDefinition definition(FreeStyleProject project, String name) {
        return project.getProperty(ParametersDefinitionProperty.class).getParameterDefinition(name);
    }

    private static ExtendedChoiceParameterDefinition staticSingle() {
        ExtendedChoiceParameterDefinition source = new ExtendedChoiceParameterDefinition(
                "LEGACY_CHANNEL",
                ExtendedChoiceParameterDefinition.PARAMETER_TYPE_SINGLE_SELECT,
                "staging,production",
                "staging",
                null);
        source.setDescription("Synthetic source");
        return source;
    }
}
