package io.jenkins.plugins.parameterfoundry.simulation;

import hudson.Extension;
import hudson.model.Job;
import hudson.model.RootAction;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.verb.GET;

/**
 * Controller-wide read-only replacement simulation. Administrators use it to prove, Job by Job,
 * that the foundry controls reproduce the recorded behavior of the source controls before the
 * migration wave starts.
 */
@Extension
public final class ReplacementSimulationAction implements RootAction {

    @GET
    public void doReport(StaplerResponse2 response, @QueryParameter int builds) throws IOException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        int maxBuilds = builds <= 0 ? ReplacementSimulationService.DEFAULT_BUILDS : builds;
        List<ReplacementSimulation> simulations = new ArrayList<>();
        for (Job<?, ?> job : Jenkins.get().getAllItems(Job.class)) {
            ReplacementSimulation simulation = ReplacementSimulationService.simulate(job, maxBuilds);
            if (!simulation.entries().isEmpty()) {
                simulations.add(simulation);
            }
        }
        JSONArray jobs = new JSONArray();
        for (ReplacementSimulation simulation : simulations) {
            jobs.add(ReplacementSimulationJson.encode(simulation));
        }
        JSONObject body = new JSONObject();
        body.put("buildsPerJob", maxBuilds);
        body.put("summary", ReplacementSimulationJson.summary(simulations));
        body.put("jobs", jobs);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(body.toString());
    }

    @Override
    public String getIconFileName() {
        return "symbol-shield-outline";
    }

    @Override
    public String getDisplayName() {
        return "Replacement Simulation";
    }

    @Override
    public String getUrlName() {
        return "parameter-foundry-replacement-simulation";
    }
}
