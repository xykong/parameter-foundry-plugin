package io.jenkins.plugins.unifiedparameters.evaluation;

import io.jenkins.plugins.unifiedparameters.definition.AbstractUnifiedParameterDefinition;
import io.jenkins.plugins.unifiedparameters.definition.UnifiedChoiceParameterDefinition;
import io.jenkins.plugins.unifiedparameters.model.ChoiceMatrix;
import io.jenkins.plugins.unifiedparameters.model.MatrixCell;
import io.jenkins.plugins.unifiedparameters.model.Option;
import io.jenkins.plugins.unifiedparameters.validation.DefinitionValidationService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ParameterEvaluationService {

    private ParameterEvaluationService() {}

    public static String evaluateSingle(UnifiedChoiceParameterDefinition definition, DecodedInput input) {
        return evaluateSingle(definition, input, definition.getSelectableOptions());
    }

    public static String evaluateSingle(
            UnifiedChoiceParameterDefinition definition, DecodedInput input, List<Option> options) {
        DefinitionValidationService.requireValid(definition);
        if (!input.present()) {
            String defaultValue = resolveSingleDefault(definition, options);
            if (defaultValue != null) {
                validateKnownEnabled(definition, options, defaultValue);
            }
            return defaultValue;
        }
        if (input.values().isEmpty()
                || (input.values().size() == 1 && input.values().get(0).isEmpty())) {
            if (definition.isRequired()) {
                throw error(ErrorCode.REQUIRED, definition, "A value is required.");
            }
            return null;
        }
        if (input.values().size() != 1) {
            throw error(ErrorCode.MALFORMED_INPUT, definition, "Single choice accepts exactly one value.");
        }
        String value = input.values().get(0);
        validateKnownEnabled(definition, options, value);
        return value;
    }

    public static List<String> evaluateMultiple(AbstractUnifiedParameterDefinition definition, DecodedInput input) {
        return evaluateMultiple(definition, input, definition.getSelectableOptions());
    }

    public static List<String> evaluateMultiple(
            AbstractUnifiedParameterDefinition definition, DecodedInput input, List<Option> options) {
        return evaluateMultiple(definition, input, options, null);
    }

    /**
     * Evaluates a multiple selection against the resolved options and, when the parameter is a
     * table, against its row constraint. A matrix cell belongs to exactly one row, so
     * {@code at-most-one} and {@code exactly-one} are checked here, before the build is queued or
     * scheduled.
     */
    public static List<String> evaluateMultiple(
            AbstractUnifiedParameterDefinition definition,
            DecodedInput input,
            List<Option> options,
            ChoiceMatrix matrix) {
        DefinitionValidationService.requireValid(definition);
        List<String> values = input.present() ? input.values() : resolveMultipleDefault(definition, options);
        if (values.isEmpty()) {
            validateRowConstraint(definition, matrix, List.of());
            validateSelectionCount(definition, 0);
            return List.of();
        }

        Set<String> unique = new HashSet<>();
        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isEmpty()) {
                throw error(
                        ErrorCode.MALFORMED_INPUT, definition, "Empty value fields are not valid multi-choice values.");
            }
            validateKnownEnabled(definition, options, value);
            if (!unique.add(value)) {
                if ("reject".equals(definition.getDuplicatePolicy())) {
                    throw error(ErrorCode.DUPLICATE_VALUE, definition, "Duplicate value: " + value);
                }
                continue;
            }
            normalized.add(value);
        }
        normalized.sort(String::compareTo);
        validateRowConstraint(definition, matrix, normalized);
        validateSelectionCount(definition, normalized.size());
        return List.copyOf(normalized);
    }

    private static void validateRowConstraint(
            AbstractUnifiedParameterDefinition definition, ChoiceMatrix matrix, List<String> values) {
        if (matrix == null
                || "any".equals(matrix.getRowSelectionMode())
                || matrix.getCells().isEmpty()) {
            return;
        }
        Map<String, String> rowByValue = new LinkedHashMap<>();
        Set<String> rowsWithSelectableCell = new LinkedHashSet<>();
        for (MatrixCell cell : matrix.getCells()) {
            if (cell == null || cell.getValue() == null) {
                continue;
            }
            rowByValue.put(cell.getValue(), cell.getRow());
            if (!cell.isDisabled()) {
                rowsWithSelectableCell.add(cell.getRow());
            }
        }
        Map<String, Integer> selectedPerRow = new LinkedHashMap<>();
        for (String value : values) {
            String row = rowByValue.get(value);
            if (row != null) {
                selectedPerRow.merge(row, 1, Integer::sum);
            }
        }
        for (Map.Entry<String, Integer> entry : selectedPerRow.entrySet()) {
            if (entry.getValue() > 1) {
                throw error(
                        ErrorCode.MALFORMED_INPUT,
                        definition,
                        "Row " + ChoiceMatrix.caption(matrix.findRow(entry.getKey()))
                                + " accepts at most one selection.");
            }
        }
        if ("exactly-one".equals(matrix.getRowSelectionMode())) {
            for (String row : rowsWithSelectableCell) {
                if (!selectedPerRow.containsKey(row)) {
                    throw error(
                            ErrorCode.MALFORMED_INPUT,
                            definition,
                            "Row " + ChoiceMatrix.caption(matrix.findRow(row)) + " requires exactly one selection.");
                }
            }
        }
    }

    /**
     * Resolves the value a caller gets when the parameter is omitted. The result is a
     * {@link String} or {@code null} for single selection and a {@link List} for multiple
     * selection, so callers must branch on the definition rather than on a concrete class.
     */
    public static Object defaultFor(AbstractUnifiedParameterDefinition definition, List<Option> options) {
        if (definition.isMultiple()) {
            return evaluateMultiple(definition, DecodedInput.absent(), options);
        }
        if (definition instanceof UnifiedChoiceParameterDefinition choice) {
            return evaluateSingle(choice, DecodedInput.absent(), options);
        }
        throw error(
                ErrorCode.UNSUPPORTED_DEFINITION, definition, "Definition does not expose a single-choice default.");
    }

    private static String resolveSingleDefault(UnifiedChoiceParameterDefinition definition, List<Option> options) {
        return switch (definition.getDefaultMode()) {
            case "none" -> null;
            case "first" -> {
                Option first = definition.firstEnabledOption(options);
                yield first == null ? null : first.getValue();
            }
            case "explicit" -> definition.getDefaultValue();
            default ->
                throw error(
                        ErrorCode.CONFIGURATION_INVALID,
                        definition,
                        "Unsupported default mode: " + definition.getDefaultMode());
        };
    }

    private static List<String> resolveMultipleDefault(
            AbstractUnifiedParameterDefinition definition, List<Option> options) {
        List<String> defaults =
                switch (definition.getDefaultMode()) {
                    case "none" -> List.of();
                    case "first" -> {
                        Option first = definition.firstEnabledOption(options);
                        yield first == null ? List.of() : List.of(first.getValue());
                    }
                    case "explicit" -> definition.getDefaultValues();
                    default ->
                        throw error(
                                ErrorCode.CONFIGURATION_INVALID,
                                definition,
                                "Unsupported default mode: " + definition.getDefaultMode());
                };
        List<String> normalized = new ArrayList<>(new HashSet<>(defaults));
        normalized.sort(String::compareTo);
        return List.copyOf(normalized);
    }

    private static void validateKnownEnabled(
            AbstractUnifiedParameterDefinition definition, List<Option> options, String value) {
        Option option = definition.findOption(options, value);
        if (option == null) {
            if (definition instanceof UnifiedChoiceParameterDefinition single && single.isAllowCustomValue()) {
                CustomValuePolicy.validate(single, value);
                return;
            }
            throw error(ErrorCode.UNKNOWN_VALUE, definition, "Unsupported value: " + value);
        }
        if (option.isDisabled()) {
            throw error(ErrorCode.DISABLED_VALUE, definition, "Disabled value: " + value);
        }
    }

    private static void validateSelectionCount(AbstractUnifiedParameterDefinition definition, int count) {
        if (definition.isRequired() && count == 0) {
            throw error(ErrorCode.REQUIRED, definition, "At least one value is required.");
        }
        if (count < definition.getMinSelections()) {
            throw error(
                    ErrorCode.MIN_SELECTIONS,
                    definition,
                    "Select at least " + definition.getMinSelections() + " value(s).");
        }
        if (definition.getMaxSelections() >= 0 && count > definition.getMaxSelections()) {
            throw error(
                    ErrorCode.MAX_SELECTIONS,
                    definition,
                    "Select at most " + definition.getMaxSelections() + " value(s).");
        }
    }

    private static EvaluationException error(
            ErrorCode code, AbstractUnifiedParameterDefinition definition, String message) {
        return new EvaluationException(code, definition.getName(), message);
    }
}
