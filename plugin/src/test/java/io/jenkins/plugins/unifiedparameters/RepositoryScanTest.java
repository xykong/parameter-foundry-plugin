package io.jenkins.plugins.unifiedparameters;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import example.legacy.LegacyParameterDefinition;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersDefinitionProperty;
import hudson.scm.NullSCM;
import io.jenkins.plugins.unifiedparameters.retirement.RepositoryScan;
import io.jenkins.plugins.unifiedparameters.retirement.RetirementScanner;
import jenkins.branch.OrganizationFolder;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class RepositoryScanTest {

    @Test
    void reportsJobsWhoseParametersComeFromRepositoryJenkinsfiles(JenkinsRule jenkins) throws Exception {
        FreeStyleProject materialized = jenkins.createFreeStyleProject("materialized");
        materialized.addProperty(new ParametersDefinitionProperty(new LegacyParameterDefinition("LEGACY")));

        WorkflowJob scmPipeline = jenkins.createProject(WorkflowJob.class, "scm-pipeline");
        scmPipeline.setDefinition(new CpsScmFlowDefinition(new NullSCM(), "Jenkinsfile"));

        WorkflowJob inlinePipeline = jenkins.createProject(WorkflowJob.class, "inline-pipeline");
        inlinePipeline.setDefinition(new CpsFlowDefinition("echo 'no parameters'", true));

        jenkins.jenkins.createProject(OrganizationFolder.class, "org-folder");

        RepositoryScan scan = new RetirementScanner().scanRepository();

        assertTrue(scan.isUnscanned());
        assertTrue(
                scan.getPipelineFromScmJobs().contains("scm-pipeline"),
                scan.getPipelineFromScmJobs().toString());
        assertFalse(scan.getPipelineFromScmJobs().contains("inline-pipeline"));
        assertTrue(scan.getMultibranchProjects().contains("org-folder"));
        assertTrue(scan.getMaterializedJobs() >= 1);

        String dashboard =
                jenkins.createWebClient().goTo("unified-parameters-retirement/").asNormalizedText();
        assertTrue(dashboard.contains("Unscanned sources"));
        assertTrue(dashboard.contains("scm-pipeline"));

        JSONObject report = jenkins.createWebClient()
                .getJSON("unified-parameters-retirement/report")
                .getJSONObject();
        assertTrue(report.getJSONObject("repositoryScan").getBoolean("unscanned"));
        assertTrue(report.getJSONObject("repositoryScan")
                .getJSONArray("pipelineFromScmJobs")
                .contains("scm-pipeline"));
    }
}
