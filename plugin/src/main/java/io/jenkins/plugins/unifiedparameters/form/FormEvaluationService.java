package io.jenkins.plugins.unifiedparameters.form;

import io.jenkins.plugins.unifiedparameters.evaluation.CustomValuePolicy;
import io.jenkins.plugins.unifiedparameters.evaluation.ErrorCode;
import io.jenkins.plugins.unifiedparameters.evaluation.EvaluationException;
import io.jenkins.plugins.unifiedparameters.model.Option;
import io.jenkins.plugins.unifiedparameters.renderer.TrustedRendererRegistry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.sf.json.JSONArray;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;

public final class FormEvaluationService {

    private static final Set<String> FIELD_TYPES = Set.of(
            "string",
            "text",
            "integer",
            "decimal",
            "boolean",
            "choice",
            "multi-choice",
            "date",
            "time",
            "content",
            "object",
            "array");
    private static final Set<String> LAYOUT_TYPES = Set.of("section", "grid", "tabs", "collapsible", "conditional");
    private static final int MAX_DEPTH = 8;
    private static final int MAX_FIELDS = 256;

    private FormEvaluationService() {}

    public static List<String> validateDefinition(UnifiedFormParameterDefinition definition) {
        List<String> errors = new ArrayList<>();
        Set<String> names = new HashSet<>();
        Counter counter = new Counter();
        validateFields(definition.getFields(), "$", 0, names, counter, errors);
        if (counter.value > MAX_FIELDS) {
            errors.add("Unified Form cannot define more than " + MAX_FIELDS + " fields");
        }
        if (!Set.of("map", "json").contains(definition.getOutputMode())) {
            errors.add("Unified Form outputMode must be map or json");
        }
        if (!Set.of("json", "none").contains(definition.getEnvironmentExport())) {
            errors.add("Unified Form environmentExport must be json or none");
        }
        if (!TrustedRendererRegistry.isTrusted(definition.getRenderer(), TrustedRendererRegistry.FAMILY_FORM)) {
            errors.add("Unified Form renderer is not trusted: " + definition.getRenderer());
        }
        if ("json".equals(definition.getEnvironmentExport()) && containsSecret(definition.getFields())) {
            errors.add("Forms with secret fields cannot export the complete form as an environment variable");
        }
        Set<String> topLevelNames = new HashSet<>();
        for (FormField field : definition.getFields()) {
            if (field != null) {
                topLevelNames.add(field.getName());
            }
        }
        validateLayouts(definition.getLayouts(), topLevelNames, new HashSet<>(), 0, errors);
        return List.copyOf(errors);
    }

    public static Map<String, Object> evaluate(
            UnifiedFormParameterDefinition definition, Object rawValue, boolean present) {
        List<String> errors = validateDefinition(definition);
        if (!errors.isEmpty()) {
            throw new EvaluationException(
                    ErrorCode.CONFIGURATION_INVALID, definition.getName(), String.join("; ", errors));
        }
        JSONObject submitted;
        if (!present || rawValue == null || rawValue instanceof JSONNull) {
            submitted = new JSONObject();
        } else if (rawValue instanceof JSONObject object) {
            submitted = object;
        } else if (rawValue instanceof String encoded) {
            try {
                submitted = JSONObject.fromObject(encoded);
            } catch (RuntimeException exception) {
                throw invalid(definition, "$", "Form value is not a valid JSON object.");
            }
        } else {
            throw invalid(definition, "$", "Form value must be a JSON object.");
        }
        return evaluateObject(definition, definition.getFields(), submitted, "$", 0);
    }

    public static String toJson(Map<String, Object> value) {
        return JSONObject.fromObject(value).toString();
    }

    private static Map<String, Object> evaluateObject(
            UnifiedFormParameterDefinition definition,
            List<FormField> fields,
            JSONObject submitted,
            String path,
            int depth) {
        if (depth > MAX_DEPTH) {
            throw invalid(definition, path, "Form nesting exceeds " + MAX_DEPTH + " levels.");
        }
        Set<String> known = new HashSet<>();
        for (FormField field : fields) {
            known.add(field.getName());
        }
        for (Object key : submitted.keySet()) {
            if (!known.contains(String.valueOf(key))) {
                throw invalid(definition, path + "." + key, "Unknown form field.");
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (FormField field : fields) {
            String fieldPath = path + "." + field.getName();
            Object raw = submitted.containsKey(field.getName()) ? submitted.get(field.getName()) : null;
            if ((raw == null || raw instanceof JSONNull) && field.getDefaultValue() != null) {
                raw = parseDefault(definition, field, fieldPath);
            }
            Object canonical = evaluateField(definition, field, raw, fieldPath, depth + 1);
            result.put(field.getName(), canonical);
        }
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(result));
    }

    private static Object evaluateField(
            UnifiedFormParameterDefinition definition, FormField field, Object raw, String path, int depth) {
        if (raw == null || raw instanceof JSONNull) {
            if (field.isRequired()) {
                throw invalid(definition, path, "A value is required.");
            }
            return switch (field.getType()) {
                case "array", "multi-choice" -> List.of();
                case "object" -> evaluateObject(definition, field.getFields(), new JSONObject(), path, depth);
                case "boolean" -> Boolean.FALSE;
                default -> null;
            };
        }
        return switch (field.getType()) {
            case "string", "text", "content" -> validateString(definition, field, raw, path);
            case "integer" -> validateInteger(definition, field, raw, path);
            case "decimal" -> validateDecimal(definition, field, raw, path);
            case "boolean" -> {
                if (!(raw instanceof Boolean value)) {
                    throw invalid(definition, path, "Expected a boolean.");
                }
                yield value;
            }
            case "choice" -> validateChoice(definition, field, raw, path);
            case "multi-choice" -> validateMultiChoice(definition, field, raw, path);
            case "date" -> validateDate(definition, raw, path);
            case "time" -> validateTime(definition, raw, path);
            case "object" -> {
                if (!(raw instanceof JSONObject object)) {
                    throw invalid(definition, path, "Expected an object.");
                }
                yield evaluateObject(definition, field.getFields(), object, path, depth);
            }
            case "array" -> validateArray(definition, field, raw, path, depth);
            default -> throw invalid(definition, path, "Unsupported form field type: " + field.getType());
        };
    }

    private static String validateString(
            UnifiedFormParameterDefinition definition, FormField field, Object raw, String path) {
        if (!(raw instanceof String value)) {
            throw invalid(definition, path, "Expected a string.");
        }
        if (field.getMinLength() >= 0 && value.length() < field.getMinLength()) {
            throw invalid(definition, path, "String is shorter than the configured minimum.");
        }
        if (field.getMaxLength() >= 0 && value.length() > field.getMaxLength()) {
            throw invalid(definition, path, "String is longer than the configured maximum.");
        }
        if (field.getPattern() != null
                && !Pattern.compile("^(?:" + field.getPattern() + ")$")
                        .matcher(value)
                        .matches()) {
            throw invalid(definition, path, "String does not match the configured pattern.");
        }
        return value;
    }

    private static Long validateInteger(
            UnifiedFormParameterDefinition definition, FormField field, Object raw, String path) {
        try {
            BigDecimal number = new BigDecimal(String.valueOf(raw));
            long value = number.longValueExact();
            validateRange(definition, field, BigDecimal.valueOf(value), path);
            return value;
        } catch (ArithmeticException | NumberFormatException exception) {
            throw invalid(definition, path, "Expected an integer.");
        }
    }

    private static BigDecimal validateDecimal(
            UnifiedFormParameterDefinition definition, FormField field, Object raw, String path) {
        try {
            BigDecimal value = new BigDecimal(String.valueOf(raw)).stripTrailingZeros();
            validateRange(definition, field, value, path);
            return value;
        } catch (NumberFormatException exception) {
            throw invalid(definition, path, "Expected a decimal number.");
        }
    }

    private static void validateRange(
            UnifiedFormParameterDefinition definition, FormField field, BigDecimal value, String path) {
        if (field.getMinimum() != null && value.compareTo(BigDecimal.valueOf(field.getMinimum())) < 0) {
            throw invalid(definition, path, "Number is lower than the configured minimum.");
        }
        if (field.getMaximum() != null && value.compareTo(BigDecimal.valueOf(field.getMaximum())) > 0) {
            throw invalid(definition, path, "Number is higher than the configured maximum.");
        }
    }

    private static String validateChoice(
            UnifiedFormParameterDefinition definition, FormField field, Object raw, String path) {
        if (!(raw instanceof String value)) {
            throw invalid(definition, path, "Expected a choice string.");
        }
        Option option = findOption(field, value);
        if (option == null) {
            throw invalid(definition, path, "Value is not a configured choice.");
        }
        if (option.isDisabled()) {
            throw invalid(definition, path, "Disabled choices cannot be selected.");
        }
        return value;
    }

    private static List<String> validateMultiChoice(
            UnifiedFormParameterDefinition definition, FormField field, Object raw, String path) {
        if (!(raw instanceof JSONArray array)) {
            throw invalid(definition, path, "Expected an array of choice strings.");
        }
        Set<String> values = new HashSet<>();
        for (int index = 0; index < array.size(); index++) {
            Object item = array.get(index);
            String value = validateChoice(definition, field, item, path + "[" + index + "]");
            if (!values.add(value)) {
                throw invalid(definition, path + "[" + index + "]", "Duplicate choice.");
            }
        }
        List<String> canonical = new ArrayList<>(values);
        canonical.sort(String::compareTo);
        validateItems(definition, field, canonical.size(), path);
        return List.copyOf(canonical);
    }

    private static String validateDate(UnifiedFormParameterDefinition definition, Object raw, String path) {
        if (!(raw instanceof String value)) {
            throw invalid(definition, path, "Expected an ISO date string.");
        }
        try {
            return LocalDate.parse(value).toString();
        } catch (RuntimeException exception) {
            throw invalid(definition, path, "Expected an ISO date string.");
        }
    }

    private static String validateTime(UnifiedFormParameterDefinition definition, Object raw, String path) {
        if (!(raw instanceof String value)) {
            throw invalid(definition, path, "Expected an ISO time string.");
        }
        try {
            return LocalTime.parse(value).toString();
        } catch (RuntimeException exception) {
            throw invalid(definition, path, "Expected an ISO time string.");
        }
    }

    private static List<Object> validateArray(
            UnifiedFormParameterDefinition definition, FormField field, Object raw, String path, int depth) {
        if (!(raw instanceof JSONArray array)) {
            throw invalid(definition, path, "Expected an array.");
        }
        validateItems(definition, field, array.size(), path);
        List<Object> result = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            FormField item = new FormField("item", field.getItemType());
            item.setFields(field.getFields());
            item.setChoices(field.getChoices());
            result.add(evaluateField(definition, item, array.get(index), path + "[" + index + "]", depth));
        }
        return List.copyOf(result);
    }

    private static void validateItems(
            UnifiedFormParameterDefinition definition, FormField field, int count, String path) {
        if (count < field.getMinItems()) {
            throw invalid(definition, path, "Array has fewer items than the configured minimum.");
        }
        if (field.getMaxItems() >= 0 && count > field.getMaxItems()) {
            throw invalid(definition, path, "Array has more items than the configured maximum.");
        }
    }

    private static Object parseDefault(UnifiedFormParameterDefinition definition, FormField field, String path) {
        String value = field.getDefaultValue();
        try {
            return switch (field.getType()) {
                case "integer" -> Long.valueOf(value);
                case "decimal" -> new BigDecimal(value);
                case "boolean" -> {
                    if (!"true".equals(value) && !"false".equals(value)) {
                        throw new IllegalArgumentException();
                    }
                    yield Boolean.valueOf(value);
                }
                case "object" -> JSONObject.fromObject(value);
                case "array", "multi-choice" -> JSONArray.fromObject(value);
                default -> value;
            };
        } catch (RuntimeException exception) {
            throw invalid(definition, path, "Configured default cannot be parsed for this field type.");
        }
    }

    private static void validateFields(
            List<FormField> fields,
            String path,
            int depth,
            Set<String> globalNames,
            Counter counter,
            List<String> errors) {
        if (depth > MAX_DEPTH) {
            errors.add("Form field nesting exceeds " + MAX_DEPTH + " levels at " + path);
            return;
        }
        Set<String> localNames = new HashSet<>();
        for (FormField field : fields) {
            counter.value++;
            if (field == null) {
                errors.add("Null form field at " + path);
                continue;
            }
            String fieldPath = path + "." + field.getName();
            if (field.getName() == null || !field.getName().matches("[A-Za-z_][A-Za-z0-9_-]{0,127}")) {
                errors.add("Invalid field name at " + fieldPath);
            } else if (!localNames.add(field.getName())) {
                errors.add("Duplicate field name at " + fieldPath);
            }
            globalNames.add(fieldPath);
            if (!FIELD_TYPES.contains(field.getType())) {
                errors.add("Unsupported field type at " + fieldPath + ": " + field.getType());
            }
            if (field.isSecret() && !Set.of("string", "text").contains(field.getType())) {
                errors.add("Secret fields must use string or text at " + fieldPath);
            }
            if (field.getMinLength() < -1
                    || field.getMaxLength() < -1
                    || (field.getMaxLength() >= 0 && field.getMaxLength() < field.getMinLength())) {
                errors.add("Invalid string length range at " + fieldPath);
            }
            if (field.getPattern() != null) {
                try {
                    CustomValuePolicy.compile(field.getPattern());
                } catch (IllegalArgumentException exception) {
                    errors.add("Invalid regex at " + fieldPath + ": " + exception.getMessage());
                }
            }
            if (field.getMinimum() != null && field.getMaximum() != null && field.getMinimum() > field.getMaximum()) {
                errors.add("Invalid numeric range at " + fieldPath);
            }
            if (field.getMinItems() < 0
                    || field.getMaxItems() < -1
                    || (field.getMaxItems() >= 0 && field.getMaxItems() < field.getMinItems())) {
                errors.add("Invalid item-count range at " + fieldPath);
            }
            if (Set.of("choice", "multi-choice").contains(field.getType())) {
                validateOptions(field, fieldPath, errors);
            }
            if (field.getEnvironmentVariable() != null
                    && !field.getEnvironmentVariable().matches("[A-Za-z_][A-Za-z0-9_]*")) {
                errors.add("Invalid environment variable at " + fieldPath);
            }
            if ("object".equals(field.getType()) || "object".equals(field.getItemType())) {
                validateFields(field.getFields(), fieldPath, depth + 1, globalNames, counter, errors);
            }
        }
    }

    private static void validateOptions(FormField field, String path, List<String> errors) {
        if (field.getChoices().isEmpty()) {
            errors.add("Choice field has no options at " + path);
            return;
        }
        Set<String> values = new HashSet<>();
        for (Option option : field.getChoices()) {
            if (option == null || option.getValue() == null || option.getValue().isEmpty()) {
                errors.add("Choice field has an empty option at " + path);
            } else if (!values.add(option.getValue())) {
                errors.add("Choice field has a duplicate option at " + path + ": " + option.getValue());
            }
        }
    }

    private static void validateLayouts(
            List<FormLayout> layouts, Set<String> fieldNames, Set<String> layoutIds, int depth, List<String> errors) {
        if (depth > MAX_DEPTH) {
            errors.add("Form layout nesting exceeds " + MAX_DEPTH + " levels");
            return;
        }
        for (FormLayout layout : layouts) {
            if (layout == null) {
                errors.add("Null form layout");
                continue;
            }
            if (layout.getId() == null || !layout.getId().matches("[A-Za-z_][A-Za-z0-9_-]{0,127}")) {
                errors.add("Invalid form layout ID");
            } else if (!layoutIds.add(layout.getId())) {
                errors.add("Duplicate form layout ID: " + layout.getId());
            }
            if (!LAYOUT_TYPES.contains(layout.getType())) {
                errors.add("Unsupported form layout type: " + layout.getType());
            }
            if (layout.getColumns() < 1 || layout.getColumns() > 12) {
                errors.add("Form grid columns must be between 1 and 12");
            }
            for (String fieldName : layout.getFieldNames()) {
                if (!fieldNames.contains(fieldName)) {
                    errors.add("Layout references unknown field: " + fieldName);
                }
            }
            if (layout.getVisibleWhenField() != null && !fieldNames.contains(layout.getVisibleWhenField())) {
                errors.add("Layout condition references unknown field: " + layout.getVisibleWhenField());
            }
            validateLayouts(layout.getChildren(), fieldNames, layoutIds, depth + 1, errors);
        }
    }

    private static Option findOption(FormField field, String value) {
        for (Option option : field.getChoices()) {
            if (option != null && value.equals(option.getValue())) {
                return option;
            }
        }
        return null;
    }

    private static boolean containsSecret(List<FormField> fields) {
        for (FormField field : fields) {
            if (field != null && (field.isSecret() || containsSecret(field.getFields()))) {
                return true;
            }
        }
        return false;
    }

    private static EvaluationException invalid(UnifiedFormParameterDefinition definition, String path, String message) {
        return new EvaluationException(ErrorCode.INVALID_TYPE, definition.getName(), path + ": " + message);
    }

    private static final class Counter {
        private int value;
    }
}
