package io.jenkins.plugins.unifiedparameters.retirement;

import java.util.List;

public record RetirementFinding(
        String sourcePluginId,
        String sourceClass,
        List<String> activeJobs,
        List<String> generatedSources,
        int queuedBuilds,
        int runningBuilds,
        int historicalBuilds,
        boolean adapterInstalled,
        boolean safeToUninstall) {

    public RetirementFinding {
        activeJobs = List.copyOf(activeJobs);
        generatedSources = List.copyOf(generatedSources);
    }

    public String getSourcePluginId() {
        return sourcePluginId;
    }

    public String getSourceClass() {
        return sourceClass;
    }

    public List<String> getActiveJobs() {
        return activeJobs;
    }

    public List<String> getGeneratedSources() {
        return generatedSources;
    }

    public int getQueuedBuilds() {
        return queuedBuilds;
    }

    public int getRunningBuilds() {
        return runningBuilds;
    }

    public int getHistoricalBuilds() {
        return historicalBuilds;
    }

    public boolean isAdapterInstalled() {
        return adapterInstalled;
    }

    public boolean isSafeToUninstall() {
        return safeToUninstall;
    }
}
