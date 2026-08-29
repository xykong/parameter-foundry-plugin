package io.jenkins.plugins.unifiedparameters.evaluation;

import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import io.jenkins.plugins.unifiedparameters.definition.AbstractUnifiedParameterDefinition;
import io.jenkins.plugins.unifiedparameters.definition.UnifiedMultiChoiceParameterDefinition;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest2;

public final class InputDecoders {

    private InputDecoders() {}

    public static DecodedInput fromStandardRequest(
            AbstractUnifiedParameterDefinition definition, StaplerRequest2 request) {
        validatePresenceMarkers(request);
        String[] raw = request.getParameterValues(definition.getName());
        boolean marked = hasMarker(
                request.getParameterValues(AbstractUnifiedParameterDefinition.PRESENCE_FIELD), definition.getName());
        if (raw == null) {
            return marked ? DecodedInput.present(List.of()) : DecodedInput.absent();
        }
        if (definition instanceof UnifiedMultiChoiceParameterDefinition multiple) {
            return DecodedInput.present(decodeMultiTransport(multiple, Arrays.asList(raw)));
        }
        return DecodedInput.present(Arrays.asList(raw));
    }

    public static DecodedInput fromSubmittedJson(AbstractUnifiedParameterDefinition definition, JSONObject submitted) {
        if (!submitted.containsKey("value")) {
            return DecodedInput.present(List.of());
        }
        Object raw = submitted.get("value");
        if (raw == null) {
            return DecodedInput.present(List.of());
        }
        if (raw instanceof JSONArray array) {
            List<String> values = new ArrayList<>();
            for (Object value : array) {
                values.add(String.valueOf(value));
            }
            return DecodedInput.present(values);
        }
        return DecodedInput.present(List.of(String.valueOf(raw)));
    }

    public static DecodedInput fromCli(AbstractUnifiedParameterDefinition definition, String rawValue) {
        if (rawValue == null) {
            return DecodedInput.absent();
        }
        if (!(definition instanceof UnifiedMultiChoiceParameterDefinition multiple)) {
            return DecodedInput.present(List.of(rawValue));
        }
        if (rawValue.startsWith("[")) {
            try {
                JSONArray array = JSONArray.fromObject(rawValue);
                List<String> values = new ArrayList<>();
                for (Object value : array) {
                    values.add(String.valueOf(value));
                }
                return DecodedInput.present(values);
            } catch (RuntimeException exception) {
                throw new EvaluationException(
                        ErrorCode.MALFORMED_INPUT, definition.getName(), "CLI JSON array is malformed.");
            }
        }
        if ("delimited".equals(multiple.getStandardApiInputMode())) {
            return DecodedInput.present(splitDelimited(rawValue, multiple.getDelimiter()));
        }
        return DecodedInput.present(List.of(rawValue));
    }

    private static List<String> decodeMultiTransport(
            UnifiedMultiChoiceParameterDefinition definition, List<String> raw) {
        return switch (definition.getStandardApiInputMode()) {
            case "repeated" -> List.copyOf(raw);
            case "json-array" -> {
                if (raw.size() != 1) {
                    throw malformed(definition, "json-array mode accepts exactly one field.");
                }
                try {
                    JSONArray array = JSONArray.fromObject(raw.get(0));
                    List<String> values = new ArrayList<>();
                    for (Object value : array) {
                        values.add(String.valueOf(value));
                    }
                    yield List.copyOf(values);
                } catch (RuntimeException exception) {
                    throw malformed(definition, "JSON array input is malformed.");
                }
            }
            case "delimited" -> {
                if (raw.size() != 1) {
                    throw malformed(definition, "delimited mode accepts exactly one field.");
                }
                yield splitDelimited(raw.get(0), definition.getDelimiter());
            }
            default ->
                throw malformed(definition, "Unsupported standard API mode: " + definition.getStandardApiInputMode());
        };
    }

    private static List<String> splitDelimited(String input, String delimiter) {
        if (input.isEmpty()) {
            return List.of();
        }
        return Arrays.asList(input.split(java.util.regex.Pattern.quote(delimiter), -1));
    }

    private static EvaluationException malformed(AbstractUnifiedParameterDefinition definition, String message) {
        return new EvaluationException(ErrorCode.MALFORMED_INPUT, definition.getName(), message);
    }

    private static boolean hasMarker(String[] markers, String name) {
        if (markers == null) {
            return false;
        }
        for (String marker : markers) {
            if (name.equals(marker)) {
                return true;
            }
        }
        return false;
    }

    private static void validatePresenceMarkers(StaplerRequest2 request) {
        String[] markers = request.getParameterValues(AbstractUnifiedParameterDefinition.PRESENCE_FIELD);
        if (markers == null) {
            return;
        }
        Set<String> known = new HashSet<>();
        ParametersDefinitionProperty property = request.findAncestorObject(ParametersDefinitionProperty.class);
        if (property != null) {
            for (ParameterDefinition definition : property.getParameterDefinitions()) {
                known.add(definition.getName());
            }
        } else {
            Job<?, ?> job = request.findAncestorObject(Job.class);
            if (job != null) {
                ParametersDefinitionProperty jobProperty = job.getProperty(ParametersDefinitionProperty.class);
                if (jobProperty != null) {
                    for (ParameterDefinition definition : jobProperty.getParameterDefinitions()) {
                        known.add(definition.getName());
                    }
                }
            }
        }
        Map<String, Integer> counts = new HashMap<>();
        for (String marker : markers) {
            counts.merge(marker, 1, Integer::sum);
            if (!known.isEmpty() && !known.contains(marker)) {
                throw new EvaluationException(ErrorCode.MALFORMED_INPUT, marker, "Unknown presence marker.");
            }
        }
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > 1) {
                throw new EvaluationException(ErrorCode.MALFORMED_INPUT, entry.getKey(), "Duplicate presence marker.");
            }
        }
    }
}
