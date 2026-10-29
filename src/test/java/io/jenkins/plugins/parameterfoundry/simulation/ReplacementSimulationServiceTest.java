package io.jenkins.plugins.parameterfoundry.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cwctravel.hudson.plugins.extended_choice_parameter.ExtendedChoiceParameterDefinition;
import example.partial.PartialOptionParameterDefinition;
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
        assertEquals("FoundryChoiceParameterDefinition", entry.targetType());
        assertTrue(entry.getTargetSnippet().contains("foundryChoice("), entry.getTargetSnippet());
        assertTrue(entry.getRequiredValues().containsAll(List.of("staging", "production")));
        assertEquals(ReplacementSimulationService.COMPARISON_MATCH, entry.getOptionSetComparison());
        for (ParameterObservation observation : entry.observations()) {
            assertTrue(observation.representable(), observation.note());
            assertTrue(observation.environmentMatches(), observation.note());
            assertEquals(observation.sourceEnvironment(), observation.targetEnvironment());
        }
    }

    @Test
    void aRecordedValueTheSourceAlsoDroppedIsHistoryNotALoss(JenkinsRule jenkins) throws Exception {
        FreeStyleProject job = jenkins.createFreeStyleProject("simulated-stale-value");
        job.addProperty(new ParametersDefinitionProperty(staticSingle()));
        run(job, "LEGACY_CHANNEL", "retired-value");

        ReplacementSimulationEntry entry =
                ReplacementSimulationService.simulate(job, 5).entries().get(0);

        assertEquals(ReplacementSimulationService.EQUIVALENT_WITH_HISTORY, entry.verdict());
        assertTrue(entry.observations().get(0).historicalValue());
        assertTrue(
                entry.observations().get(0).note().contains("no longer offers"),
                entry.observations().get(0).note());
    }

    @Test
    void aScriptSourceNeedsAProviderAndKeepsItsRecordedContract(JenkinsRule jenkins) throws Exception {
        FreeStyleProject job = jenkins.createFreeStyleProject("simulated-script");
        ExtendedChoiceParameterDefinition source = new ExtendedChoiceParameterDefinition(
                "DYNAMIC", ExtendedChoiceParameterDefinition.PARAMETER_TYPE_SINGLE_SELECT, "a,b", null, "return ['a']");
        job.addProperty(new ParametersDefinitionProperty(source));
        run(job, "DYNAMIC", "a");

        ReplacementSimulationEntry entry =
                ReplacementSimulationService.simulate(job, 5).entries().get(0);

        assertEquals(ReplacementSimulationService.NEEDS_PROVIDER, entry.verdict());
        assertEquals("C", entry.classification());
        assertEquals("Provider (skeleton)", entry.targetType());
        assertTrue(entry.getDecisions().size() >= 1);
        assertEquals(1, entry.observationCount());
        assertEquals("a", entry.observations().get(0).sourceValue());
    }

    @Test
    void aSourceWithoutATargetShapeNeedsAManualRedesign(JenkinsRule jenkins) throws Exception {
        FreeStyleProject job = jenkins.createFreeStyleProject("simulated-unknown-type");
        ExtendedChoiceParameterDefinition source =
                new ExtendedChoiceParameterDefinition("UNKNOWN_TYPE", "PT_UNKNOWN_SHAPE", "a,b", null, null);
        job.addProperty(new ParametersDefinitionProperty(source));

        ReplacementSimulationEntry entry =
                ReplacementSimulationService.simulate(job, 5).entries().get(0);

        assertEquals(ReplacementSimulationService.MANUAL_REDESIGN, entry.verdict());
        assertEquals("D", entry.classification());
    }

    @Test
    void aJobWithoutRecordedValuesIsVerifiedThroughItsDefault(JenkinsRule jenkins) throws Exception {
        FreeStyleProject job = jenkins.createFreeStyleProject("simulated-no-builds");
        job.addProperty(new ParametersDefinitionProperty(staticSingle()));

        ReplacementSimulationEntry entry =
                ReplacementSimulationService.simulate(job, 5).entries().get(0);

        assertEquals(ReplacementSimulationService.EQUIVALENT_DEFAULT_ONLY, entry.verdict());
        assertEquals(ReplacementSimulationService.COMPARISON_MATCH, entry.getDefaultCheck());
        assertEquals("staging", entry.getSourceDefault());
        assertTrue(entry.observations().isEmpty());
    }

    @Test
    void aSourceWithoutADefaultStaysUnverifiedWithoutBuildHistory(JenkinsRule jenkins) throws Exception {
        FreeStyleProject job = jenkins.createFreeStyleProject("simulated-no-default");
        ExtendedChoiceParameterDefinition source = new ExtendedChoiceParameterDefinition(
                "LEGACY_CHANNEL",
                ExtendedChoiceParameterDefinition.PARAMETER_TYPE_SINGLE_SELECT,
                "staging,production",
                null,
                null);
        job.addProperty(new ParametersDefinitionProperty(source));

        ReplacementSimulationEntry entry =
                ReplacementSimulationService.simulate(job, 5).entries().get(0);

        assertEquals(ReplacementSimulationService.UNVERIFIED, entry.verdict());
        assertEquals(ReplacementSimulationService.COMPARISON_UNKNOWN, entry.getDefaultCheck());
        assertEquals(ReplacementSimulationService.COMPARISON_MATCH, entry.getOptionSetComparison());
        assertTrue(entry.observations().isEmpty());
    }

    @Test
    void aStaleSourceDefaultNeedsADecisionEvenWhenBuildsAreEquivalent(JenkinsRule jenkins) throws Exception {
        FreeStyleProject job = jenkins.createFreeStyleProject("simulated-stale-default");
        ExtendedChoiceParameterDefinition source = new ExtendedChoiceParameterDefinition(
                "LEGACY_CHANNEL",
                ExtendedChoiceParameterDefinition.PARAMETER_TYPE_SINGLE_SELECT,
                "staging,production",
                "removed-channel",
                null);
        job.addProperty(new ParametersDefinitionProperty(source));
        run(job, "LEGACY_CHANNEL", "staging");

        ReplacementSimulationEntry entry =
                ReplacementSimulationService.simulate(job, 5).entries().get(0);

        assertEquals(ReplacementSimulationService.DECIDED_NO_DEFAULT, entry.verdict());
        assertEquals(ReplacementSimulationService.COMPARISON_MISMATCH, entry.getDefaultCheck());
        assertEquals("removed-channel", entry.getSourceDefault());
        assertEquals(ReplacementSimulationService.COMPARISON_MATCH, entry.getOptionSetComparison());
    }

    @Test
    void aStaleDefaultThatBuildsActuallyUsedStaysADecision(JenkinsRule jenkins) throws Exception {
        FreeStyleProject job = jenkins.createFreeStyleProject("simulated-used-stale-default");
        ExtendedChoiceParameterDefinition source = new ExtendedChoiceParameterDefinition(
                "LEGACY_CHANNEL",
                ExtendedChoiceParameterDefinition.PARAMETER_TYPE_SINGLE_SELECT,
                "staging,production",
                "removed-channel",
                null);
        job.addProperty(new ParametersDefinitionProperty(source));
        run(job, "LEGACY_CHANNEL", "removed-channel");

        ReplacementSimulationEntry entry =
                ReplacementSimulationService.simulate(job, 5).entries().get(0);

        assertEquals(ReplacementSimulationService.NEEDS_DEFAULT_DECISION, entry.verdict());
        assertEquals(ReplacementSimulationService.COMPARISON_MISMATCH, entry.getDefaultCheck());
    }

    @Test
    void aDroppedOptionIsReportedEvenWithoutBuildHistory(JenkinsRule jenkins) throws Exception {
        FreeStyleProject job = jenkins.createFreeStyleProject("simulated-dropped-option");
        job.addProperty(new ParametersDefinitionProperty(new PartialOptionParameterDefinition("PARTIAL")));

        ReplacementSimulationEntry entry =
                ReplacementSimulationService.simulate(job, 5).entries().get(0);

        assertEquals(ReplacementSimulationService.OPTION_SET_MISMATCH, entry.verdict());
        assertEquals(ReplacementSimulationService.COMPARISON_MISMATCH, entry.getOptionSetComparison());
        assertTrue(entry.getRequiredValues().containsAll(List.of("alpha", "beta")));
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
                        List.of(new ParameterObservation(1, "a", "a", "a", "a", true, true, false, false, null))));
        assertEquals(
                ReplacementSimulationService.EQUIVALENT_ADDED_ENV,
                ReplacementSimulationService.verdictOf(
                        List.of(new ParameterObservation(1, "a", null, "a", "a", true, false, false, false, "added"))));
        assertEquals(
                ReplacementSimulationService.MISMATCH,
                ReplacementSimulationService.verdictOf(List.of(new ParameterObservation(
                        1, "a", "a", null, null, false, false, false, false, "not representable"))));
        assertEquals(ReplacementSimulationService.UNVERIFIED, ReplacementSimulationService.verdictOf(List.of()));
    }

    @Test
    void theControllerReportEncodesEveryJobWithASourceParameter(JenkinsRule jenkins) throws Exception {
        FreeStyleProject job = jenkins.createFreeStyleProject("simulated-report");
        job.addProperty(new ParametersDefinitionProperty(staticSingle()));
        run(job, "LEGACY_CHANNEL", "staging");

        JSONObject report = jenkins.createWebClient()
                .getJSON("parameter-foundry-replacement-simulation/report")
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
