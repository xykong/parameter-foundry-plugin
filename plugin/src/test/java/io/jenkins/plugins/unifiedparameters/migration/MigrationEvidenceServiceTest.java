package io.jenkins.plugins.unifiedparameters.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.cwctravel.hudson.plugins.extended_choice_parameter.ExtendedChoiceParameterDefinition;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterValue;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class MigrationEvidenceServiceTest {

    @Test
    void recordsPendingPassingAndFailingAbEvidence(JenkinsRule jenkins) throws Exception {
        FreeStyleProject job = jenkins.createFreeStyleProject("evidence-job");
        job.addProperty(new ParametersDefinitionProperty(staticSingle()));
        run(job, "staging");

        MigrationBatchPreview preview = MigrationCoordinator.previewBatch(job, List.of());
        MigrationBatchResult result = MigrationCoordinator.applyBatch(job, preview, preview.getRequiredDecisions());
        MigrationRecord record = result.getRecords().get(0);

        MigrationEvidence pending = MigrationEvidenceService.verify(job, record.getId());
        assertEquals(MigrationEvidence.PENDING, pending.getStatus(), pending.getSummary());

        run(job, "staging");
        MigrationEvidence pass = MigrationEvidenceService.verify(job, record.getId());
        assertEquals(MigrationEvidence.PASS, pass.getStatus(), pass.getSummary());
        assertEquals("staging", pass.getSourceValue());
        assertEquals("staging", pass.getTargetValue());

        run(job, "production");
        MigrationEvidence fail = MigrationEvidenceService.verify(job, record.getId());
        assertEquals(MigrationEvidence.FAIL, fail.getStatus(), fail.getSummary());
        assertEquals("production", fail.getTargetValue());
        assertEquals("staging", fail.getSourceValue());
        assertNotNull(record.getEvidence());
        assertEquals(MigrationEvidence.FAIL, record.getEvidenceStatus());

        MigrationEvidence forced = MigrationEvidenceService.verify(job, record.getId(), 1, 3);
        assertEquals(MigrationEvidence.FAIL, forced.getStatus());
    }

    private static void run(FreeStyleProject job, String value) throws Exception {
        job.scheduleBuild2(0, new ParametersAction(new StringParameterValue("LEGACY_CHANNEL", value)))
                .get();
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
