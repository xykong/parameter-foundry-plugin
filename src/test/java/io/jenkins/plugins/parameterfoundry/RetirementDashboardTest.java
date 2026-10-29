package io.jenkins.plugins.parameterfoundry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import example.legacy.LegacyParameterDefinition;
import example.legacy.LegacyParameterValue;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import io.jenkins.plugins.parameterfoundry.retirement.RetirementFinding;
import io.jenkins.plugins.parameterfoundry.retirement.RetirementScanner;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class RetirementDashboardTest {

    @Test
    void scannerSeparatesActiveAndHistoricalReferences(JenkinsRule jenkins) throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject("legacy-job");
        project.addProperty(new ParametersDefinitionProperty(new LegacyParameterDefinition("LEGACY")));
        project.scheduleBuild2(0, new ParametersAction(new LegacyParameterValue("LEGACY", "value")));
        jenkins.waitUntilNoActivity();

        List<RetirementFinding> findings = new RetirementScanner().scan();

        RetirementFinding definition = findings.stream()
                .filter(finding -> finding.sourceClass().endsWith("LegacyParameterDefinition"))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of("legacy-job"), definition.activeJobs());
        assertFalse(definition.safeToUninstall());
        RetirementFinding value = findings.stream()
                .filter(finding -> finding.sourceClass().endsWith("LegacyParameterValue"))
                .findFirst()
                .orElseThrow();
        assertEquals(1, value.historicalBuilds());
        assertTrue(value.activeJobs().isEmpty());

        JenkinsRule.JSONWebResponse report = jenkins.createWebClient().getJSON("parameter-foundry-retirement/report");
        assertTrue(report.getJSONObject().getJSONArray("findings").size() >= 2);
        String dashboard =
                jenkins.createWebClient().goTo("parameter-foundry-retirement/").asNormalizedText();
        assertTrue(dashboard.contains("unknown-external"));
        assertTrue(dashboard.contains("Active Jobs"));
    }
}
