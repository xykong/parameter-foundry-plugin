package io.jenkins.plugins.unifiedparameters.retirement;

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

    public List<RetirementFinding> getFindings() {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        return new RetirementScanner().scan();
    }

    public boolean isEmpty() {
        return getFindings().isEmpty();
    }

    @GET
    public void doReport(StaplerResponse2 response) throws IOException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        JSONArray findings = new JSONArray();
        for (RetirementFinding finding : new RetirementScanner().scan()) {
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
        JSONObject body = new JSONObject();
        body.put("findings", findings);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(body.toString());
    }

    @Override
    public String getIconFileName() {
        return "symbol-archive";
    }

    @Override
    public String getDisplayName() {
        return "Unified Parameters Retirement";
    }

    @Override
    public String getUrlName() {
        return "unified-parameters-retirement";
    }
}
