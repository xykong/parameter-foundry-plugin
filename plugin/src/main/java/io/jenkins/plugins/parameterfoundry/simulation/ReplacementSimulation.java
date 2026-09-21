package io.jenkins.plugins.unifiedparameters.simulation;

import java.util.List;

/** The simulated replacement of every source parameter of one Job. */
public record ReplacementSimulation(
        String jobFullName, String jobClass, List<ReplacementSimulationEntry> entries, int observationCount) {

    public ReplacementSimulation {
        entries = List.copyOf(entries);
    }

    public String getJobFullName() {
        return jobFullName;
    }

    public String getJobClass() {
        return jobClass;
    }

    public List<ReplacementSimulationEntry> getEntries() {
        return entries;
    }

    public int getObservationCount() {
        return observationCount;
    }

    public int getParameterCount() {
        return entries.size();
    }

    public int parameterCount() {
        return entries.size();
    }
}
