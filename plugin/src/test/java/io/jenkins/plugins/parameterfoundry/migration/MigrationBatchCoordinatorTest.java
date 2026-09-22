package io.jenkins.plugins.parameterfoundry.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cwctravel.hudson.plugins.extended_choice_parameter.ExtendedChoiceParameterDefinition;
import example.invalid.InvalidTargetParameterDefinition;
import hudson.model.FreeStyleProject;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition;
import io.jenkins.plugins.parameterfoundry.evaluation.ErrorCode;
import io.jenkins.plugins.parameterfoundry.evaluation.EvaluationException;
import java.nio.file.Files;
import java.util.List;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class MigrationBatchCoordinatorTest {

    @Test
    void previewsTheJobDiffAndAppliesOneAtomicBatch(JenkinsRule jenkins) throws Exception {
        FreeStyleProject job = jenkins.createFreeStyleProject("legacy-batch");
        job.addProperty(new ParametersDefinitionProperty(staticSingle(), multiWithEmptyOption(), dynamic()));

        MigrationBatchPreview preview = MigrationCoordinator.previewBatch(job, List.of());

        assertEquals(2, preview.getApplicableCount(), preview.getDiff());
        assertEquals(1, preview.getBlockedCount());
        assertTrue(preview.isApplicable());
        assertEquals(List.of("LEGACY_DYNAMIC"), preview.getBlockedParameterNames());
        assertFalse(preview.getRequiredDecisions().isEmpty());
        assertTrue(preview.getDiff().contains("LEGACY_CHANNEL"));
        assertTrue(preview.getDiff().contains("LEGACY_TARGETS"));
        assertTrue(preview.getDiff().contains("ExtendedChoiceParameterDefinition"));
        assertTrue(preview.getDiff().contains("FoundryChoiceParameterDefinition"));
        assertTrue(preview.getDiff().contains("- "), preview.getDiff());
        assertTrue(preview.getDiff().contains("+ "), preview.getDiff());

        EvaluationException refused =
                assertThrows(EvaluationException.class, () -> MigrationCoordinator.applyBatch(job, preview, List.of()));
        assertEquals(ErrorCode.CONFIGURATION_INVALID, refused.getCode());
        assertInstanceOf(ExtendedChoiceParameterDefinition.class, definition(job, "LEGACY_CHANNEL"));
        assertInstanceOf(ExtendedChoiceParameterDefinition.class, definition(job, "LEGACY_TARGETS"));

        MigrationBatchResult result = MigrationCoordinator.applyBatch(job, preview, preview.getRequiredDecisions());

        assertEquals(2, result.getRecordCount());
        assertInstanceOf(FoundryChoiceParameterDefinition.class, definition(job, "LEGACY_CHANNEL"));
        assertInstanceOf(FoundryChoiceParameterDefinition.class, definition(job, "LEGACY_TARGETS"));
        assertInstanceOf(ExtendedChoiceParameterDefinition.class, definition(job, "LEGACY_DYNAMIC"));
        MigrationRecord record = result.getRecords().get(0);
        assertEquals(result.getBatchId(), record.getBatchId());
        assertEquals("APPLIED", record.getStatus());
        assertTrue(Files.isRegularFile(jenkins.jenkins
                .getRootDir()
                .toPath()
                .resolve("parameter-foundry/migration-backups")
                .resolve(record.getBackupFile())));

        EvaluationException singleRollback =
                assertThrows(EvaluationException.class, () -> MigrationCoordinator.rollback(job, record.getId()));
        assertEquals(ErrorCode.CONFIGURATION_CHANGED, singleRollback.getCode());

        MigrationCoordinator.rollbackBatch(job, result.getBatchId());

        assertInstanceOf(ExtendedChoiceParameterDefinition.class, definition(job, "LEGACY_CHANNEL"));
        assertInstanceOf(ExtendedChoiceParameterDefinition.class, definition(job, "LEGACY_TARGETS"));
        assertEquals("ROLLED_BACK", record.getStatus());
    }

    @Test
    void refusesAStaleBatchAndUnknownParameterNames(JenkinsRule jenkins) throws Exception {
        FreeStyleProject job = jenkins.createFreeStyleProject("batch-drift");
        ExtendedChoiceParameterDefinition source = staticSingle();
        job.addProperty(new ParametersDefinitionProperty(source));
        MigrationBatchPreview preview = MigrationCoordinator.previewBatch(job, List.of("LEGACY_CHANNEL"));

        source.setDescription("Changed after the batch preview");
        job.save();

        EvaluationException drift = assertThrows(
                EvaluationException.class,
                () -> MigrationCoordinator.applyBatch(job, preview, preview.getRequiredDecisions()));
        assertEquals(ErrorCode.CONFIGURATION_CHANGED, drift.getCode());

        EvaluationException unknown = assertThrows(
                EvaluationException.class, () -> MigrationCoordinator.previewBatch(job, List.of("MISSING_PARAMETER")));
        assertEquals(ErrorCode.CONFIGURATION_INVALID, unknown.getCode());
    }

    @Test
    void reportsAnInvalidGeneratedTargetAsBlockedInsteadOfFailing(JenkinsRule jenkins) throws Exception {
        FreeStyleProject job = jenkins.createFreeStyleProject("invalid-target");
        job.addProperty(new ParametersDefinitionProperty(new InvalidTargetParameterDefinition("INVALID_TARGET")));

        MigrationBatchPreview preview = MigrationCoordinator.previewBatch(job, List.of());

        assertEquals(0, preview.getApplicableCount());
        assertEquals(1, preview.getBlockedCount());
        assertTrue(
                preview.getBlocked().get(0).reason().contains("generated target is invalid"),
                preview.getBlocked().get(0).reason());
    }

    @Test
    void blocksParametersThatThePipelineDeclares(JenkinsRule jenkins) throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline-declared");
        job.setDefinition(new CpsFlowDefinition(declarativePipeline("LEGACY_CHANNEL"), true));
        job.scheduleBuild2(0).get();

        // The operator migrated the Job configuration after the last build, but the Pipeline
        // declares the same name and re-applies its own definition on the next run.
        replaceParameters(job, new ParametersDefinitionProperty(staticSingle()));

        MigrationBatchPreview preview = MigrationCoordinator.previewBatch(job, List.of());

        assertEquals(0, preview.getApplicableCount());
        assertEquals(1, preview.getBlockedCount());
        assertFalse(preview.isApplicable());
        assertEquals(
                ExtendedChoiceParameterDefinition.class.getName(),
                preview.getBlocked().get(0).sourceClass());
        assertTrue(
                preview.getBlocked().get(0).reason().contains("Pipeline declares this parameter"),
                preview.getBlocked().get(0).reason());
    }

    @Test
    void stillMigratesParametersThatThePipelineDoesNotDeclare(JenkinsRule jenkins) throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline-other-parameter");
        job.setDefinition(new CpsFlowDefinition(declarativePipeline("SOMETHING_ELSE"), true));
        job.scheduleBuild2(0).get();

        replaceParameters(job, new ParametersDefinitionProperty(staticSingle()));

        MigrationBatchPreview preview = MigrationCoordinator.previewBatch(job, List.of());

        assertEquals(1, preview.getApplicableCount(), String.valueOf(preview.getBlocked()));
        assertEquals(0, preview.getBlockedCount(), String.valueOf(preview.getBlocked()));
        assertTrue(preview.isApplicable());
    }

    private static void replaceParameters(WorkflowJob job, ParametersDefinitionProperty property) throws Exception {
        job.removeProperty(ParametersDefinitionProperty.class);
        job.addProperty(property);
    }

    private static String declarativePipeline(String declaredParameter) {
        return "pipeline {\n"
                + "  agent any\n"
                + "  parameters { string(name: '" + declaredParameter + "', defaultValue: 'staging') }\n"
                + "  stages { stage('run') { steps { echo 'declared' } } }\n"
                + "}\n";
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

    private static ExtendedChoiceParameterDefinition dynamic() {
        return new ExtendedChoiceParameterDefinition(
                "LEGACY_DYNAMIC",
                ExtendedChoiceParameterDefinition.PARAMETER_TYPE_SINGLE_SELECT,
                "dynamic-value",
                null,
                "return ['a', 'b']");
    }

    private static ExtendedChoiceParameterDefinition multiWithEmptyOption() {
        return new ExtendedChoiceParameterDefinition(
                "LEGACY_TARGETS",
                ExtendedChoiceParameterDefinition.PARAMETER_TYPE_CHECK_BOX,
                "android,,web",
                "android",
                null);
    }
}
