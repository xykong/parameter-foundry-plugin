package io.jenkins.plugins.parameterfoundry.retirement;

import hudson.Extension;
import hudson.model.RootAction;
import java.io.IOException;
import java.util.List;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.verb.GET;

@Extension
public final class RetirementDashboardAction implements RootAction {

    /** One scan per page render: findings, repository-side blind spot, and JSON export. */
    public RetirementReport getReport() {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        return new RetirementScanner().scanAll();
    }

    public List<RetirementFinding> getFindings() {
        return getReport().getFindings();
    }

    public RepositoryScan getRepositoryScan() {
        return getReport().getRepositoryScan();
    }

    public boolean isEmpty() {
        return getFindings().isEmpty();
    }

    @GET
    public void doReport(StaplerResponse2 response) throws IOException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        RetirementReport report = new RetirementScanner().scanAll();
        JSONArray findings = new JSONArray();
        for (RetirementFinding finding : report.findings()) {
            JSONObject encoded = new JSONObject();
            encoded.put("sourcePluginId", finding.sourcePluginId());
            encoded.put("sourceClass", finding.sourceClass());
            encoded.put("activeJobs", JSONArray.fromObject(finding.activeJobs()));
            encoded.put("generatedSources", JSONArray.fromObject(finding.generatedSources()));
            encoded.put("queuedBuilds", finding.queuedBuilds());
            encoded.put("runningBuilds", finding.runningBuilds());
            encoded.put("historicalBuilds", finding.historicalBuilds());
            encoded.put("adapterInstalled", finding.adapterInstalled());
            encoded.put("safeToUninstall", finding.safeToUninstall());
            findings.add(encoded);
        }
        RepositoryScan repository = report.repositoryScan();
        JSONObject repositoryScan = new JSONObject();
        repositoryScan.put("unscanned", repository.isUnscanned());
        repositoryScan.put("materializedJobs", repository.getMaterializedJobs());
        repositoryScan.put("pipelineFromScmJobs", JSONArray.fromObject(repository.getPipelineFromScmJobs()));
        repositoryScan.put("multibranchProjects", JSONArray.fromObject(repository.getMultibranchProjects()));
        JSONObject body = new JSONObject();
        body.put("findings", findings);
        body.put("repositoryScan", repositoryScan);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(body.toString());
    }

    @Override
    public String getIconFileName() {
        return "symbol-archive";
    }

    @Override
    public String getDisplayName() {
        return "Parameter Foundry Retirement";
    }

    @Override
    public String getUrlName() {
        return "parameter-foundry-retirement";
    }
}
