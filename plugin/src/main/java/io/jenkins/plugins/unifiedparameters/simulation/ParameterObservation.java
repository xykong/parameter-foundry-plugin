package io.jenkins.plugins.unifiedparameters.simulation;

/**
 * One recorded production build compared against its simulated replacement: what the source
 * control produced (value and environment export) and what the migrated control would produce for
 * the same input.
 */
public record ParameterObservation(
        int buildNumber,
        String sourceValue,
        String sourceEnvironment,
        String targetValue,
        String targetEnvironment,
        boolean representable,
        boolean environmentMatches,
        String note) {

    public int getBuildNumber() {
        return buildNumber;
    }

    public String getSourceValue() {
        return sourceValue;
    }

    public String getSourceEnvironment() {
        return sourceEnvironment;
    }

    public String getTargetValue() {
        return targetValue;
    }

    public String getTargetEnvironment() {
        return targetEnvironment;
    }

    public boolean isRepresentable() {
        return representable;
    }

    public boolean isEnvironmentMatches() {
        return environmentMatches;
    }

    public String getNote() {
        return note;
    }
}
