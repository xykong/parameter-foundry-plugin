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
        List<ParameterObservation> observations,
        String targetSnippet,
        List<String> requiredValues,
        String optionSetComparison,
        String defaultCheck,
        String sourceDefault) {

    public ReplacementSimulationEntry {
        decisions = List.copyOf(decisions);
        observations = List.copyOf(observations);
        requiredValues = List.copyOf(requiredValues);
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

    /** Pipeline/Job DSL snippet of the target, or the adapter's skeleton for a provider target. */
    public String getTargetSnippet() {
        return targetSnippet;
    }

    /** Values that must stay selectable after the migration. */
    public List<String> getRequiredValues() {
        return requiredValues;
    }

    public String getOptionSetComparison() {
        return optionSetComparison;
    }

    public String getDefaultCheck() {
        return defaultCheck;
    }

    public String getSourceDefault() {
        return sourceDefault;
    }

    public String targetSnippet() {
        return targetSnippet;
    }

    public List<String> requiredValues() {
        return requiredValues;
    }

    public String optionSetComparison() {
        return optionSetComparison;
    }

    public String defaultCheck() {
        return defaultCheck;
    }

    public String sourceDefault() {
        return sourceDefault;
    }
}
