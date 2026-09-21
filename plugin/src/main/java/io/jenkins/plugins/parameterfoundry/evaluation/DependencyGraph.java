package io.jenkins.plugins.unifiedparameters.evaluation;

import hudson.model.ParameterDefinition;
import io.jenkins.plugins.unifiedparameters.definition.AbstractUnifiedParameterDefinition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DependencyGraph {

    private DependencyGraph() {}

    public static List<String> validate(List<ParameterDefinition> definitions) {
        List<String> errors = new ArrayList<>();
        Map<String, ParameterDefinition> byName = byName(definitions);
        for (ParameterDefinition definition : definitions) {
            if (!(definition instanceof AbstractUnifiedParameterDefinition unified)) {
                continue;
            }
            Set<String> unique = new HashSet<>();
            for (String dependency : unified.getDependsOn()) {
                if (dependency == null || dependency.isBlank()) {
                    errors.add("Dependency names must be non-empty for parameter: " + definition.getName());
                } else if (!unique.add(dependency)) {
                    errors.add("Duplicate dependency " + dependency + " for parameter: " + definition.getName());
                } else if (dependency.equals(definition.getName())) {
                    errors.add("Parameter cannot depend on itself: " + definition.getName());
                } else if (!byName.containsKey(dependency)) {
                    errors.add("Unknown dependency " + dependency + " for parameter: " + definition.getName());
                }
            }
        }
        if (errors.isEmpty()) {
            try {
                topologicalOrder(definitions);
            } catch (EvaluationException exception) {
                errors.add(exception.getMessage());
            }
        }
        return List.copyOf(errors);
    }

    public static List<ParameterDefinition> topologicalOrder(List<ParameterDefinition> definitions) {
        Map<String, ParameterDefinition> byName = byName(definitions);
        Map<String, VisitState> states = new HashMap<>();
        List<ParameterDefinition> ordered = new ArrayList<>();
        List<String> path = new ArrayList<>();
        for (ParameterDefinition definition : definitions) {
            visit(definition, byName, states, path, ordered);
        }
        return List.copyOf(ordered);
    }

    private static void visit(
            ParameterDefinition definition,
            Map<String, ParameterDefinition> byName,
            Map<String, VisitState> states,
            List<String> path,
            List<ParameterDefinition> ordered) {
        String name = definition.getName();
        VisitState state = states.get(name);
        if (state == VisitState.VISITED) {
            return;
        }
        if (state == VisitState.VISITING) {
            int start = path.indexOf(name);
            List<String> cycle = new ArrayList<>(path.subList(Math.max(0, start), path.size()));
            cycle.add(name);
            throw new EvaluationException(
                    ErrorCode.DEPENDENCY_CYCLE, name, "Reactive dependency cycle: " + String.join(" -> ", cycle));
        }
        states.put(name, VisitState.VISITING);
        path.add(name);
        if (definition instanceof AbstractUnifiedParameterDefinition unified) {
            for (String dependency : unified.getDependsOn()) {
                ParameterDefinition dependencyDefinition = byName.get(dependency);
                if (dependencyDefinition != null) {
                    visit(dependencyDefinition, byName, states, path, ordered);
                }
            }
        }
        path.remove(path.size() - 1);
        states.put(name, VisitState.VISITED);
        ordered.add(definition);
    }

    private static Map<String, ParameterDefinition> byName(List<ParameterDefinition> definitions) {
        Map<String, ParameterDefinition> result = new LinkedHashMap<>();
        for (ParameterDefinition definition : definitions) {
            if (definition != null && definition.getName() != null) {
                result.putIfAbsent(definition.getName(), definition);
            }
        }
        return result;
    }

    private enum VisitState {
        VISITING,
        VISITED
    }
}
