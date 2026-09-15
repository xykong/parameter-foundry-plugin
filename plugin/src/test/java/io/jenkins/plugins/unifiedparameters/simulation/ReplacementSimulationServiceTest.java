package io.jenkins.plugins.unifiedparameters.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cwctravel.hudson.plugins.extended_choice_parameter.ExtendedChoiceParameterDefinition;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterValue;
import java.util.List;
import net.sf.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ReplacementSimulationServiceTest {

    @Test
    void recordedValuesStayEquivalentForAStaticChoice(JenkinsRule jenkins) throws Exception {
        FreeStyleProject job = jenkins.createFreeStyleProject("simulated-static");
        job.addProperty(new ParametersDefinitionProperty(staticSingle()));
        run(job, "LEGACY_CHANNEL", "staging");
        run(job, "LEGACY_CHANNEL", "production");

        ReplacementSimulation simulation = ReplacementSimulationService.simulate(job, 5);

        assertEquals(1, simulation.parameterCount());
        ReplacementSimulationEntry entry = simulation.entries().get(0);
        assertEquals(ReplacementSimulationService.EQUIVALENT, entry.verdict());
        assertEquals(2, entry.observationCount());
        assertEquals("UnifiedChoiceParameterDefinition", entry.targetType());
        for (ParameterObservation observation : entry.observations()) {
            assertTrue(observation.representable(), observation.note());
            assertTrue(observation.environmentMatches(), observation.note());
            assertEquals(observation.sourceEnvironment(), observation.targetEnvironment());
        }
    }

    @Test
    void aRecordedValueOutsideTheOptionSetIsAMismatch(JenkinsRule jenkins) throws Exception {
        FreeStyleProject job = jenkins.createFreeStyleProject("simulated-stale-value");
        job.addProperty(new ParametersDefinitionProperty(staticSingle()));
        run(job, "LEGACY_CHANNEL", "retired-value");

        ReplacementSimulationEntry entry =
                ReplacementSimulationService.simulate(job, 5).entries().get(0);

        assertEquals(ReplacementSimulationService.MISMATCH, entry.verdict());
        assertTrue(entry.observations().get(0).note().contains("not among the migrated options"));
    }

    @Test
    void aScriptSourceNeedsAManualRedesignAndKeepsItsRecordedContract(JenkinsRule jenkins) throws Exception {
        FreeStyleProject job = jenkins.createFreeStyleProject("simulated-script");
        ExtendedChoiceParameterDefinition source = new ExtendedChoiceParameterDefinition(
                "DYNAMIC", ExtendedChoiceParameterDefinition.PARAMETER_TYPE_SINGLE_SELECT, "a,b", null, "return ['a']");
        job.addProperty(new ParametersDefinitionProperty(source));
        run(job, "DYNAMIC", "a");

        ReplacementSimulationEntry entry =
                ReplacementSimulationService.simulate(job, 5).entries().get(0);

        assertEquals(ReplacementSimulationService.MANUAL_REDESIGN, entry.verdict());
        assertEquals("C", entry.classification());
        assertTrue(entry.getDecisions().size() >= 1);
        assertEquals(1, entry.observationCount());
        assertEquals("a", entry.observations().get(0).sourceValue());
    }

    @Test
    void aJobWithoutRecordedValuesIsUnverified(JenkinsRule jenkins) throws Exception {
        FreeStyleProject job = jenkins.createFreeStyleProject("simulated-no-builds");
        job.addProperty(new ParametersDefinitionProperty(staticSingle()));

        ReplacementSimulationEntry entry =
                ReplacementSimulationService.simulate(job, 5).entries().get(0);

        assertEquals(ReplacementSimulationService.UNVERIFIED, entry.verdict());
        assertTrue(entry.observations().isEmpty());
    }

    @Test
    void aMultiSelectionKeepsTheSubmittedOrderInItsEnvironmentExport(JenkinsRule jenkins) throws Exception {
        FreeStyleProject job = jenkins.createFreeStyleProject("simulated-multi-order");
        ExtendedChoiceParameterDefinition source = new ExtendedChoiceParameterDefinition(
                "TARGETS", ExtendedChoiceParameterDefinition.PARAMETER_TYPE_CHECK_BOX, "android,web", "android", null);
        job.addProperty(new ParametersDefinitionProperty(source));
        run(job, "TARGETS", "web,android");

        ReplacementSimulationEntry entry =
                ReplacementSimulationService.simulate(job, 5).entries().get(0);

        assertEquals(
                ReplacementSimulationService.EQUIVALENT,
                entry.verdict(),
                entry.observations().toString());
        ParameterObservation observation = entry.observations().get(0);
        assertTrue(observation.representable());
        assertEquals("web,android", observation.sourceEnvironment());
        assertEquals("web,android", observation.targetEnvironment());
        assertEquals("android,web", observation.targetValue());
    }

    @Test
    void verdictsSeparateMismatchesFromAnAddedEnvironmentExport() {
        assertEquals(
                ReplacementSimulationService.EQUIVALENT,
                ReplacementSimulationService.verdictOf(
                        List.of(new ParameterObservation(1, "a", "a", "a", "a", true, true, false, null))));
        assertEquals(
                ReplacementSimulationService.EQUIVALENT_ADDED_ENV,
                ReplacementSimulationService.verdictOf(
                        List.of(new ParameterObservation(1, "a", null, "a", "a", true, false, false, "added"))));
        assertEquals(
                ReplacementSimulationService.MISMATCH,
                ReplacementSimulationService.verdictOf(List.of(
                        new ParameterObservation(1, "a", "a", null, null, false, false, false, "not representable"))));
        assertEquals(ReplacementSimulationService.UNVERIFIED, ReplacementSimulationService.verdictOf(List.of()));
    }

    @Test
    void theControllerReportEncodesEveryJobWithASourceParameter(JenkinsRule jenkins) throws Exception {
        FreeStyleProject job = jenkins.createFreeStyleProject("simulated-report");
        job.addProperty(new ParametersDefinitionProperty(staticSingle()));
        run(job, "LEGACY_CHANNEL", "staging");

        JSONObject report = jenkins.createWebClient()
                .getJSON("unified-parameters-replacement-simulation/report")
                .getJSONObject();

        assertNotNull(report);
        JSONObject summary = report.getJSONObject("summary");
        assertTrue(summary.getInt("jobs") >= 1, summary.toString());
        assertTrue(summary.getJSONObject("byVerdict").getInt("EQUIVALENT") >= 1, summary.toString());
        assertEquals(
                "simulated-report", report.getJSONArray("jobs").getJSONObject(0).getString("job"));
    }

    private static void run(FreeStyleProject job, String parameterName, String value) throws Exception {
        job.scheduleBuild2(0, new ParametersAction(new StringParameterValue(parameterName, value)))
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
