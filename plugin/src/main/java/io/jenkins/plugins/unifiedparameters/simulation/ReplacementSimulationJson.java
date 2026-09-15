package io.jenkins.plugins.unifiedparameters.simulation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/** JSON encoding shared by the Job-level and controller-level simulation endpoints. */
public final class ReplacementSimulationJson {

    private ReplacementSimulationJson() {}

    public static JSONObject encode(ReplacementSimulation simulation) {
        JSONObject encoded = new JSONObject();
        encoded.put("job", simulation.jobFullName());
        encoded.put("jobClass", simulation.jobClass());
        encoded.put("parameters", entries(simulation.entries()));
        encoded.put("observations", simulation.observationCount());
        return encoded;
    }

    public static JSONArray entries(List<ReplacementSimulationEntry> entries) {
        JSONArray encoded = new JSONArray();
        for (ReplacementSimulationEntry entry : entries) {
            encoded.add(encodeEntry(entry));
        }
        return encoded;
    }

    public static JSONObject encodeEntry(ReplacementSimulationEntry entry) {
        JSONObject encoded = new JSONObject();
        encoded.put("parameter", entry.parameterName());
        encoded.put("sourceClass", entry.sourceClass());
        encoded.put("adapter", entry.adapterId());
        encoded.put("classification", entry.classification());
        encoded.put("targetType", entry.targetType());
        encoded.put("verdict", entry.verdict());
        encoded.put("reason", entry.reason());
        encoded.put("decisions", JSONArray.fromObject(entry.decisions()));
        JSONArray observations = new JSONArray();
        for (ParameterObservation observation : entry.observations()) {
            observations.add(encodeObservation(observation));
        }
        encoded.put("observations", observations);
        return encoded;
    }

    public static JSONObject encodeObservation(ParameterObservation observation) {
        JSONObject encoded = new JSONObject();
        encoded.put("build", observation.buildNumber());
        encoded.put("sourceValue", observation.sourceValue());
        encoded.put("sourceEnvironment", observation.sourceEnvironment());
        encoded.put("targetValue", observation.targetValue());
        encoded.put("targetEnvironment", observation.targetEnvironment());
        encoded.put("representable", observation.representable());
        encoded.put("environmentMatches", observation.environmentMatches());
        encoded.put("orderOnlyDifference", observation.orderOnlyDifference());
        encoded.put("note", observation.note());
        return encoded;
    }

    /** Counts by verdict and by source family for the controller-wide report. */
    public static JSONObject summary(List<ReplacementSimulation> simulations) {
        Map<String, Integer> verdicts = new LinkedHashMap<>();
        Map<String, Integer> families = new LinkedHashMap<>();
        int parameters = 0;
        int observations = 0;
        int parametersWithObservations = 0;
        for (ReplacementSimulation simulation : simulations) {
            observations += simulation.observationCount();
            for (ReplacementSimulationEntry entry : simulation.entries()) {
                parameters++;
                verdicts.merge(entry.verdict(), 1, Integer::sum);
                families.merge(familyOf(entry), 1, Integer::sum);
                if (entry.observationCount() > 0) {
                    parametersWithObservations++;
                }
            }
        }
        JSONObject summary = new JSONObject();
        summary.put("jobs", simulations.size());
        summary.put("parameters", parameters);
        summary.put("parametersWithObservations", parametersWithObservations);
        summary.put("observations", observations);
        summary.put("byVerdict", JSONObject.fromObject(verdicts));
        summary.put("byFamily", JSONObject.fromObject(families));
        return summary;
    }

    private static String familyOf(ReplacementSimulationEntry entry) {
        String className = entry.sourceClass();
        int separator = className.lastIndexOf('.');
        return separator < 0 ? className : className.substring(separator + 1);
    }
}
