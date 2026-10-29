package io.jenkins.plugins.parameterfoundry.evaluation;

import hudson.model.Job;
import hudson.model.ParameterValue;
import io.jenkins.plugins.parameterfoundry.definition.AbstractFoundryParameterDefinition;
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition;
import io.jenkins.plugins.parameterfoundry.provider.ProviderRegistry;
import io.jenkins.plugins.parameterfoundry.provider.RestProviderDefinition;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.sf.json.JSONArray;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;

public final class DependencyValues {

    private DependencyValues() {}

    public static Map<String, String> fromJson(
            AbstractFoundryParameterDefinition definition, JSONObject submittedDependencies) {
        return fromJson(null, definition, submittedDependencies);
    }

    public static Map<String, String> fromJson(
            Job<?, ?> job, AbstractFoundryParameterDefinition definition, JSONObject submittedDependencies) {
        boolean sourceCascade = isSourceCascade(job, definition);
        if (definition.getDependsOn().isEmpty()) {
            if (submittedDependencies != null && !submittedDependencies.isEmpty()) {
                throw invalid(definition, "This parameter does not declare dependencies.");
            }
            return Map.of();
        }
        if (submittedDependencies == null) {
            if (sourceCascade) return Map.of();
            throw invalid(definition, "Dependencies are required: " + definition.getDependsOn());
        }
        for (Object key : submittedDependencies.keySet()) {
            if (!definition.getDependsOn().contains(String.valueOf(key))) {
                throw invalid(definition, "Undeclared dependency: " + key);
            }
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String dependency : definition.getDependsOn()) {
            if (!submittedDependencies.containsKey(dependency)) {
                if (sourceCascade) continue;
                throw invalid(definition, "Missing dependency: " + dependency);
            }
            Object value = submittedDependencies.get(dependency);
            result.put(
                    dependency, sourceCascade && (value == null || value instanceof JSONNull) ? "" : canonical(value));
        }
        return Map.copyOf(result);
    }

    private static boolean isSourceCascade(Job<?, ?> job, AbstractFoundryParameterDefinition definition) {
        return job != null
                && definition instanceof FoundryChoiceParameterDefinition choice
                && choice.isActiveChoicesInput()
                && definition.isProviderBacked()
                && ProviderRegistry.findProvider(job, definition.getProviderId())
                        .map(located -> located.provider() instanceof RestProviderDefinition rest
                                && "active-choices-cascade".equals(rest.getResponseMode()))
                        .orElse(false);
    }

    public static Map<String, String> fromParameterValues(
            AbstractFoundryParameterDefinition definition, Map<String, ParameterValue> values) {
        return fromParameterValues(null, definition, values);
    }

    public static Map<String, String> fromParameterValues(
            Job<?, ?> job, AbstractFoundryParameterDefinition definition, Map<String, ParameterValue> values) {
        boolean sourceCascade = isSourceCascade(job, definition);
        Map<String, String> result = new LinkedHashMap<>();
        for (String dependency : definition.getDependsOn()) {
            ParameterValue value = values.get(dependency);
            if (value == null) {
                if (sourceCascade) continue;
                throw invalid(definition, "Dependency has no canonical value: " + dependency);
            }
            result.put(dependency, sourceCascade && value.getValue() == null ? "" : canonical(value.getValue()));
        }
        return Map.copyOf(result);
    }

    public static JSONObject parseJsonObject(
            AbstractFoundryParameterDefinition definition, String encodedDependencies) {
        if (encodedDependencies == null || encodedDependencies.isBlank()) {
            return definition.getDependsOn().isEmpty() ? new JSONObject() : null;
        }
        try {
            return JSONObject.fromObject(encodedDependencies);
        } catch (RuntimeException exception) {
            throw invalid(definition, "Submitted dependencies are not valid JSON.");
        }
    }

    public static String canonical(Object value) {
        if (value == null || value instanceof JSONNull) {
            return "null";
        }
        if (value instanceof JSONArray array) {
            List<String> values = new ArrayList<>();
            for (Object item : array) {
                values.add(String.valueOf(item));
            }
            values.sort(String::compareTo);
            return JSONArray.fromObject(values).toString();
        }
        if (value instanceof Collection<?> collection) {
            List<String> values =
                    collection.stream().map(String::valueOf).sorted().toList();
            return JSONArray.fromObject(values).toString();
        }
        return String.valueOf(value);
    }

    private static EvaluationException invalid(AbstractFoundryParameterDefinition definition, String message) {
        return new EvaluationException(ErrorCode.DEPENDENCY_INVALID, definition.getName(), message);
    }
}
