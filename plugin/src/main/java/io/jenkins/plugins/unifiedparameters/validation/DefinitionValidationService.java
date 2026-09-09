package io.jenkins.plugins.unifiedparameters.validation;

import hudson.model.ParameterDefinition;
import io.jenkins.plugins.unifiedparameters.definition.AbstractUnifiedParameterDefinition;
import io.jenkins.plugins.unifiedparameters.definition.UnifiedChoiceParameterDefinition;
import io.jenkins.plugins.unifiedparameters.definition.UnifiedMultiChoiceParameterDefinition;
import io.jenkins.plugins.unifiedparameters.evaluation.CustomValuePolicy;
import io.jenkins.plugins.unifiedparameters.evaluation.DependencyGraph;
import io.jenkins.plugins.unifiedparameters.evaluation.ErrorCode;
import io.jenkins.plugins.unifiedparameters.evaluation.EvaluationException;
import io.jenkins.plugins.unifiedparameters.form.FormEvaluationService;
import io.jenkins.plugins.unifiedparameters.form.UnifiedFormParameterDefinition;
import io.jenkins.plugins.unifiedparameters.model.ChoiceMatrix;
import io.jenkins.plugins.unifiedparameters.model.Option;
import io.jenkins.plugins.unifiedparameters.renderer.TrustedRendererRegistry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DefinitionValidationService {

    private static final Set<String> DEFAULT_MODES = Set.of("none", "first", "explicit");
    private static final Set<String> SINGLE_RENDERERS = Set.of("select", "searchable", "radio");
    private static final Set<String> MULTI_RENDERERS = Set.of("list", "checkbox", "table");
    private static final Set<String> MULTI_SELECTED_DISPLAYS = Set.of("none", "chips");
    private static final Set<String> MULTI_API_MODES = Set.of("repeated", "json-array", "delimited");
    private static final Set<String> MULTI_EXPORT_MODES = Set.of("delimited", "json", "none");

    private DefinitionValidationService() {}

    public static DefinitionValidationResult validateStoredDefinition(ParameterDefinition definition) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (definition instanceof UnifiedFormParameterDefinition form) {
            if (form.getName() == null
                    || form.getName().isEmpty()
                    || AbstractUnifiedParameterDefinition.PRESENCE_FIELD.equals(form.getName())) {
                errors.add("Form parameter name is required and cannot be reserved");
            }
            if (form.getModelVersion() != 1) {
                errors.add("Unsupported form model version: " + form.getModelVersion());
            }
            errors.addAll(FormEvaluationService.validateDefinition(form));
            return new DefinitionValidationResult(errors);
        }
        if (!(definition instanceof AbstractUnifiedParameterDefinition unified)) {
            return new DefinitionValidationResult(errors);
        }

        validateName(unified, errors);
        if (unified.getModelVersion() != 1) {
            errors.add("Unsupported model version: " + unified.getModelVersion());
        }
        if (!DEFAULT_MODES.contains(unified.getDefaultMode())) {
            errors.add("Unsupported default mode: " + unified.getDefaultMode());
        }
        for (String dependency : unified.getDependsOn()) {
            if (dependency == null || !dependency.matches("[A-Za-z_][A-Za-z0-9_.-]{0,127}")) {
                errors.add("Dependency names must use letters, digits, dot, underscore, or hyphen");
            }
        }
        if (!unified.getDependsOn().isEmpty() && !unified.isProviderBacked()) {
            errors.add("Reactive dependencies require a shared Provider");
        }
        validateCliDefaultPolicy(unified, warnings);
        validateChoices(unified, errors);

        if (unified instanceof UnifiedChoiceParameterDefinition single) {
            validateSingle(single, errors);
        } else if (unified instanceof UnifiedMultiChoiceParameterDefinition multiple) {
            validateMultiple(multiple, errors);
        }
        return new DefinitionValidationResult(errors, warnings);
    }

    public static DefinitionValidationResult validateJobDefinitions(List<ParameterDefinition> definitions) {
        List<String> errors = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (ParameterDefinition definition : definitions) {
            if (definition == null) {
                errors.add("Null parameter definition");
                continue;
            }
            String name = definition.getName();
            if (!names.add(name)) {
                errors.add("Duplicate parameter name: " + name);
            }
            if (AbstractUnifiedParameterDefinition.PRESENCE_FIELD.equals(name)) {
                errors.add("Reserved parameter name: " + name);
            }
            errors.addAll(validateStoredDefinition(definition).getErrors());
        }
        errors.addAll(DependencyGraph.validate(definitions));
        return new DefinitionValidationResult(errors);
    }

    public static void requireValid(ParameterDefinition definition) {
        DefinitionValidationResult result = validateStoredDefinition(definition);
        if (!result.isValid()) {
            throw new EvaluationException(ErrorCode.CONFIGURATION_INVALID, definition.getName(), result.summarize());
        }
    }

    private static void validateName(AbstractUnifiedParameterDefinition definition, List<String> errors) {
        String name = definition.getName();
        if (name == null || name.isEmpty()) {
            errors.add("Parameter name is required");
        } else if (AbstractUnifiedParameterDefinition.PRESENCE_FIELD.equals(name)) {
            errors.add("Parameter name is reserved");
        }
    }

    private static void validateCliDefaultPolicy(AbstractUnifiedParameterDefinition definition, List<String> warnings) {
        if (definition.isRequired()
                && !definition.getDependsOn().isEmpty()
                && "none".equals(definition.getDefaultMode())) {
            warnings.add(
                    "Required reactive parameter has no default; CLI and other omission-based callers must submit it explicitly.");
        }
    }

    private static void validateChoices(AbstractUnifiedParameterDefinition definition, List<String> errors) {
        if (definition.isProviderBacked()) {
            if (!definition.getProviderId().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) {
                errors.add("Provider ID is invalid");
            }
            if (!definition.getChoices().isEmpty()) {
                errors.add("Provider-backed definitions cannot also declare Inline choices");
            }
            return;
        }
        if (definition.getChoices().isEmpty()) {
            errors.add("At least one option is required");
            return;
        }
        Set<String> values = new HashSet<>();
        for (Option option : definition.getChoices()) {
            if (option == null) {
                errors.add("Null option");
                continue;
            }
            String value = option.getValue();
            if (value == null || value.isEmpty()) {
                errors.add("Option values must be non-empty");
                continue;
            }
            if (containsProhibitedControl(value)) {
                errors.add("Option contains a prohibited control character: " + value);
            }
            if (!values.add(value)) {
                errors.add("Duplicate option value: " + value);
            }
        }
    }

    private static void validateSingle(UnifiedChoiceParameterDefinition definition, List<String> errors) {
        if (!SINGLE_RENDERERS.contains(definition.getRenderer())
                && !TrustedRendererRegistry.isTrusted(
                        definition.getRenderer(), TrustedRendererRegistry.FAMILY_SINGLE_CHOICE)) {
            errors.add("Renderer is not valid for single choice: " + definition.getRenderer());
        }
        String defaultValue = definition.getDefaultValue();
        if ("explicit".equals(definition.getDefaultMode())) {
            validateDefaultValue(definition, defaultValue, errors);
        } else if (defaultValue != null && !defaultValue.isEmpty()) {
            errors.add("defaultValue requires defaultMode=explicit");
        }
        if (!Set.of("string", "none").contains(definition.getEnvironmentExport())) {
            errors.add("Unsupported single-choice export mode: " + definition.getEnvironmentExport());
        }
        if (definition.isAllowCustomValue()) {
            if (definition.getCustomValueMaxLength() < 1 || definition.getCustomValueMaxLength() > 4096) {
                errors.add("customValueMaxLength must be between 1 and 4096");
            }
            try {
                if (definition.getCustomValuePattern() != null) {
                    CustomValuePolicy.compile(definition.getCustomValuePattern());
                }
            } catch (IllegalArgumentException exception) {
                errors.add(exception.getMessage());
            }
        } else if (definition.getCustomValuePattern() != null) {
            errors.add("Custom-value validation requires allowCustomValue=true");
        }
    }

    private static void validateMultiple(UnifiedMultiChoiceParameterDefinition definition, List<String> errors) {
        if (!MULTI_RENDERERS.contains(definition.getRenderer())
                && !TrustedRendererRegistry.isTrusted(
                        definition.getRenderer(), TrustedRendererRegistry.FAMILY_MULTI_CHOICE)) {
            errors.add("Renderer is not valid for multiple choice: " + definition.getRenderer());
        }
        if (!MULTI_SELECTED_DISPLAYS.contains(definition.getSelectedDisplay())) {
            errors.add("Selected display is not valid for multiple choice: " + definition.getSelectedDisplay());
        }
        ChoiceMatrix table = definition.getTable();
        if (table != null) {
            validateMatrix(table, definition, errors);
        }
        if (!MULTI_API_MODES.contains(definition.getStandardApiInputMode())) {
            errors.add("Unsupported standard API input mode: " + definition.getStandardApiInputMode());
        }
        if (!MULTI_EXPORT_MODES.contains(definition.getEnvironmentExport())) {
            errors.add("Unsupported multi-choice export mode: " + definition.getEnvironmentExport());
        }
        if (definition.getMinSelections() < 0) {
            errors.add("minSelections cannot be negative");
        }
        if (definition.getMaxSelections() >= 0 && definition.getMaxSelections() < definition.getMinSelections()) {
            errors.add("maxSelections cannot be lower than minSelections");
        }
        if (!Set.of("reject", "deduplicate").contains(definition.getDuplicatePolicy())) {
            errors.add("Unsupported duplicate policy: " + definition.getDuplicatePolicy());
        }
        if ("explicit".equals(definition.getDefaultMode())) {
            for (String value : definition.getDefaultValues()) {
                validateDefaultValue(definition, value, errors);
            }
        } else if (!definition.getDefaultValues().isEmpty()) {
            errors.add("defaultValues requires defaultMode=explicit");
        }
        if ("delimited".equals(definition.getEnvironmentExport())
                || "delimited".equals(definition.getStandardApiInputMode())) {
            String delimiter = definition.getDelimiter();
            if (delimiter == null || delimiter.isEmpty()) {
                errors.add("A non-empty delimiter is required");
            } else {
                for (Option option : definition.getChoices()) {
                    if (option != null
                            && option.getValue() != null
                            && option.getValue().contains(delimiter)) {
                        errors.add("Option contains the configured delimiter: " + option.getValue());
                    }
                }
            }
        }
    }

    private static void validateMatrix(
            ChoiceMatrix matrix, UnifiedMultiChoiceParameterDefinition definition, List<String> errors) {
        if (matrix.getRows().isEmpty() || matrix.getColumns().isEmpty()) {
            errors.add("Choice matrix requires at least one row and column");
            return;
        }
        Set<String> rows = new HashSet<>();
        matrix.getRows().forEach(row -> {
            if (!rows.add(row.getValue())) {
                errors.add("Duplicate matrix row value: " + row.getValue());
            }
        });
        Set<String> columns = new HashSet<>();
        matrix.getColumns().forEach(column -> {
            if (!columns.add(column.getValue())) {
                errors.add("Duplicate matrix column value: " + column.getValue());
            }
        });
        Set<String> coordinates = new HashSet<>();
        for (var cell : matrix.getCells()) {
            if (!rows.contains(cell.getRow()) || !columns.contains(cell.getColumn())) {
                errors.add("Matrix cell references an unknown row or column: " + cell.getValue());
            }
            if (!coordinates.add(cell.getRow() + "\u0000" + cell.getColumn())) {
                errors.add("Duplicate matrix cell coordinate: " + cell.getRow() + "/" + cell.getColumn());
            }
        }
    }

    private static void validateDefaultValue(
            AbstractUnifiedParameterDefinition definition, String value, List<String> errors) {
        if (value == null || value.isEmpty()) {
            errors.add("Explicit default values must be non-empty");
            return;
        }
        if (definition.isProviderBacked()) {
            return;
        }
        Option option = definition.findOption(value);
        if (option == null) {
            if (definition instanceof UnifiedChoiceParameterDefinition single && single.isAllowCustomValue()) {
                try {
                    CustomValuePolicy.validate(single, value);
                } catch (EvaluationException exception) {
                    errors.add(exception.getMessage());
                }
            } else {
                errors.add("Default value is not an option: " + value);
            }
        } else if (option.isDisabled()) {
            errors.add("Disabled option cannot be a default: " + value);
        }
    }

    private static boolean containsProhibitedControl(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == 0 || character == '\r' || character == '\n') {
                return true;
            }
        }
        return false;
    }
}
