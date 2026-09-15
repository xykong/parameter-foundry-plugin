package io.jenkins.plugins.unifiedparameters.simulation;

import java.util.List;

/** The simulated replacement of one source parameter, including the recorded-build comparison. */
public record ReplacementSimulationEntry(
        String parameterName,
        String sourceClass,
        String adapterId,
        String classification,
        String targetType,
        String verdict,
        String reason,
        List<String> decisions,
        List<ParameterObservation> observations) {

    public ReplacementSimulationEntry {
        decisions = List.copyOf(decisions);
        observations = List.copyOf(observations);
    }

    public String getParameterName() {
        return parameterName;
    }

    public String getSourceClass() {
        return sourceClass;
    }

    public String getAdapterId() {
        return adapterId;
    }

    public String getClassification() {
        return classification;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getVerdict() {
        return verdict;
    }

    public String getReason() {
        return reason;
    }

    public List<String> getDecisions() {
        return decisions;
    }

    public List<ParameterObservation> getObservations() {
        return observations;
    }

    public int getObservationCount() {
        return observations.size();
    }

    public int observationCount() {
        return observations.size();
    }
}
