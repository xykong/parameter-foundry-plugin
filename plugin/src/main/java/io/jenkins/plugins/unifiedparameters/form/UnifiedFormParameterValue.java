package io.jenkins.plugins.unifiedparameters.form;

import hudson.EnvVars;
import hudson.model.ParameterValue;
import hudson.model.Run;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean(defaultVisibility = 2)
public final class UnifiedFormParameterValue extends ParameterValue {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Map<String, Object> value;
    private final String outputMode;
    private final String environmentExport;
    private final Map<String, String> fieldEnvironmentVariables;

    @DataBoundConstructor
    public UnifiedFormParameterValue(String name, Map<String, Object> value) {
        this(name, value, null, "map", "json", Map.of());
    }

    public UnifiedFormParameterValue(
            String name,
            Map<String, Object> value,
            String description,
            String outputMode,
            String environmentExport,
            Map<String, String> fieldEnvironmentVariables) {
        super(name, description);
        this.value = deepCopyMap(value == null ? Map.of() : value);
        this.outputMode = outputMode == null ? "map" : outputMode;
        this.environmentExport = environmentExport == null ? "json" : environmentExport;
        this.fieldEnvironmentVariables = new LinkedHashMap<>(fieldEnvironmentVariables);
    }

    @Override
    @Exported(visibility = 4)
    public Object getValue() {
        return "json".equals(outputMode) ? getCanonicalJson() : value;
    }

    @Exported
    public Map<String, Object> getStructuredValue() {
        return Collections.unmodifiableMap(value);
    }

    @Exported
    public String getCanonicalJson() {
        return FormEvaluationService.toJson(value);
    }

    public String getEnvironmentExport() {
        return environmentExport;
    }

    public String getOutputMode() {
        return outputMode;
    }

    public Map<String, String> getFieldEnvironmentVariables() {
        return Collections.unmodifiableMap(fieldEnvironmentVariables);
    }

    @Override
    public void buildEnvironment(Run<?, ?> build, EnvVars environment) {
        if ("json".equals(environmentExport)) {
            environment.put(getName(), getCanonicalJson());
        }
        for (Map.Entry<String, String> mapping : fieldEnvironmentVariables.entrySet()) {
            Object fieldValue = value.get(mapping.getKey());
            if (fieldValue != null) {
                environment.put(mapping.getValue(), encodeEnvironmentValue(fieldValue));
            }
        }
    }

    @Override
    public String getShortDescription() {
        return getName() + "=" + getCanonicalJson();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof UnifiedFormParameterValue valueObject)) {
            return false;
        }
        return Objects.equals(getName(), valueObject.getName()) && Objects.equals(value, valueObject.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getName(), value);
    }

    private static String encodeEnvironmentValue(Object value) {
        if (value instanceof Map<?, ?> || value instanceof Iterable<?>) {
            return net.sf.json.JSONSerializer.toJSON(value).toString();
        }
        return String.valueOf(value);
    }

    private static Map<String, Object> deepCopyMap(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, deepCopy(value)));
        return copy;
    }

    private static Object deepCopy(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, item) -> copy.put(String.valueOf(key), deepCopy(item)));
            return copy;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> copy = new ArrayList<>();
            for (Object item : collection) {
                copy.add(deepCopy(item));
            }
            return copy;
        }
        return value;
    }
}
