package io.jenkins.plugins.parameterfoundry.validation;

import hudson.model.ParameterDefinition;
import io.jenkins.plugins.parameterfoundry.definition.AbstractFoundryParameterDefinition;
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition;
import io.jenkins.plugins.parameterfoundry.evaluation.CustomValuePolicy;
import io.jenkins.plugins.parameterfoundry.evaluation.DependencyGraph;
import io.jenkins.plugins.parameterfoundry.evaluation.ErrorCode;
import io.jenkins.plugins.parameterfoundry.evaluation.EvaluationException;
import io.jenkins.plugins.parameterfoundry.form.FormEvaluationService;
import io.jenkins.plugins.parameterfoundry.form.FoundryFormParameterDefinition;
import io.jenkins.plugins.parameterfoundry.model.ChoiceMatrix;
import io.jenkins.plugins.parameterfoundry.model.Option;
import io.jenkins.plugins.parameterfoundry.renderer.TrustedRendererRegistry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DefinitionValidationService {

    private static final Set<String> DEFAULT_MODES = Set.of("none", "first", "explicit");
    private static final Set<String> SINGLE_RENDERERS = Set.of("select", "searchable", "radio", "list");
    private static final Set<String> MULTI_RENDERERS = Set.of("list", "checkbox", "table");
    private static final Set<String> MULTI_SELECTED_DISPLAYS = Set.of("none", "chips");
    private static final Set<String> MULTI_API_MODES = Set.of("repeated", "json-array", "delimited", "extended-choice");
    private static final Set<String> MULTI_EXPORT_MODES = Set.of("delimited", "json", "none");

    private DefinitionValidationService() {}

    public static DefinitionValidationResult validateStoredDefinition(ParameterDefinition definition) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (definition
                instanceof
                io.jenkins.plugins.parameterfoundry.companion.file.AbstractFoundryFileParameterDefinition file) {
            try {
                file.validateConfiguration();
            } catch (RuntimeException e) {
                errors.add(e.getMessage());
            }
            return new DefinitionValidationResult(errors);
        }
        if (definition instanceof FoundryFormParameterDefinition form) {
            if (form.getName() == null
                    || form.getName().isEmpty()
                    || AbstractFoundryParameterDefinition.PRESENCE_FIELD.equals(form.getName())) {
                errors.add("Form parameter name is required and cannot be reserved");
            }
            if (form.getModelVersion() != 1) {
                errors.add("Unsupported form model version: " + form.getModelVersion());
            }
            errors.addAll(FormEvaluationService.validateDefinition(form));
            return new DefinitionValidationResult(errors);
        }
        if (!(definition instanceof AbstractFoundryParameterDefinition foundry)) {
            return new DefinitionValidationResult(errors);
        }

        validateName(foundry, errors);
        if (foundry.getModelVersion() != 1) {
            errors.add("Unsupported model version: " + foundry.getModelVersion());
        }
        if (!DEFAULT_MODES.contains(foundry.getDefaultMode())) {
            errors.add("Unsupported default mode: " + foundry.getDefaultMode());
        }
        for (String dependency : foundry.getDependsOn()) {
            if (dependency == null || !dependency.matches("[A-Za-z_][A-Za-z0-9_.-]{0,127}")) {
                errors.add("Dependency names must use letters, digits, dot, underscore, or hyphen");
            }
        }
        if (!foundry.getDependsOn().isEmpty() && !foundry.isProviderBacked()) {
            errors.add("Reactive dependencies require a shared Provider");
        }
        validateCliDefaultPolicy(foundry, warnings);
        validateChoices(foundry, errors);

        if (foundry instanceof FoundryChoiceParameterDefinition choice) {
            if (choice.getLegacyDefaultLiteral() != null
                    && (!choice.isMultiple() || !"extended-choice".equals(choice.getStandardApiInputMode()))) {
                errors.add("Legacy default literal requires Extended Choice multiple-selection compatibility.");
            }
            if (!Set.of("single", "multiple").contains(choice.getSelectionMode())) {
                errors.add("Unsupported selection mode: " + choice.getSelectionMode());
            }
            if (choice.isMultiple()) {
                validateMultiple(choice, errors);
            } else {
                validateSingle(choice, errors);
            }
            if (!Set.of("canonical", "submitted").contains(choice.getEnvironmentExportOrder())) {
                errors.add("Unsupported environment export order: " + choice.getEnvironmentExportOrder());
            }
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
            if (AbstractFoundryParameterDefinition.PRESENCE_FIELD.equals(name)) {
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

    private static void validateName(AbstractFoundryParameterDefinition definition, List<String> errors) {
        String name = definition.getName();
        if (name == null || name.isEmpty()) {
            errors.add("Parameter name is required");
        } else if (AbstractFoundryParameterDefinition.PRESENCE_FIELD.equals(name)) {
            errors.add("Parameter name is reserved");
        }
    }

    private static void validateCliDefaultPolicy(AbstractFoundryParameterDefinition definition, List<String> warnings) {
        if (definition.isRequired()
                && !definition.getDependsOn().isEmpty()
                && "none".equals(definition.getDefaultMode())) {
            warnings.add(
                    "Required reactive parameter has no default; CLI and other omission-based callers must submit it explicitly.");
        }
    }

    private static void validateChoices(AbstractFoundryParameterDefinition definition, List<String> errors) {
        if (definition.isProviderBacked()) {
            if (!definition.getProviderId().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) {
                errors.add("Provider ID is invalid");
            }
            if (!definition.getChoices().isEmpty()) {
                errors.add("Provider-backed definitions cannot also declare Inline choices");
            }
            if (definition.getTable() != null) {
                errors.add("Provider-backed definitions cannot also declare a choice matrix");
            }
            return;
        }
        if (definition.getSelectableOptions().isEmpty()) {
            errors.add("At least one option is required");
            return;
        }
        Set<String> flatValues = new HashSet<>();
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
            if (!flatValues.add(value)) {
                errors.add("Duplicate option value: " + value);
            }
        }
        ChoiceMatrix matrix = definition.getTable();
        if (matrix != null) {
            for (var cell : matrix.getCells()) {
                if (cell == null || cell.getValue() == null) {
                    continue;
                }
                if (flatValues.contains(cell.getValue())) {
                    errors.add("Matrix cell value duplicates an option value: " + cell.getValue());
                }
            }
        }
    }

    private static void validateSingle(FoundryChoiceParameterDefinition definition, List<String> errors) {
        if ("list-git-branches".equals(definition.getStandardApiInputMode())) {
            if (!Set.of("default", "top", "none").contains(definition.getGitSelectionMode())) {
                errors.add("Git selection mode must be default, top or none.");
            }
            if (!definition.isProviderBacked() || !definition.getDependsOn().isEmpty()) {
                errors.add(
                        "List Git Branches compatibility requires a Provider-backed single choice without dependencies.");
            }
            if (definition.isAllowCustomValue() || definition.isRequired()) {
                errors.add(
                        "List Git Branches compatibility does not support custom-value controls or required validation.");
            }
        }
        if ("extended-choice".equals(definition.getStandardApiInputMode())) {
            errors.add("Extended Choice transport compatibility requires multiple selection.");
        }
        if (!SINGLE_RENDERERS.contains(definition.getRenderer())
                && !TrustedRendererRegistry.isTrusted(
                        definition.getRenderer(), TrustedRendererRegistry.FAMILY_SINGLE_CHOICE)) {
            errors.add("Renderer is not valid for single choice: " + definition.getRenderer());
        }
        String defaultValue = definition.getDefaultValue();
        if ("explicit".equals(definition.getDefaultMode())) {
            validateDefaultValue(definition, defaultValue, errors);
        } else if (defaultValue != null
                && !defaultValue.isEmpty()
                && !"list-git-branches".equals(definition.getStandardApiInputMode())) {
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

    private static void validateMultiple(AbstractFoundryParameterDefinition definition, List<String> errors) {
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
            if (definition.isProviderBacked()) {
                errors.add("A Provider-backed table uses the matrix the Provider returns; remove the inline table");
            }
        }
        if (!MULTI_API_MODES.contains(definition.getStandardApiInputMode())) {
            errors.add("Unsupported standard API input mode: " + definition.getStandardApiInputMode());
        }
        if ("extended-choice".equals(definition.getStandardApiInputMode())
                && (!(definition instanceof FoundryChoiceParameterDefinition)
                        || definition.isProviderBacked()
                        || definition.getTable() != null)) {
            errors.add("Extended Choice transport compatibility requires a static multiple choice without a table.");
        }
        if ("extended-choice".equals(definition.getStandardApiInputMode())) {
            if (!Set.of("delimited", "none").contains(definition.getEnvironmentExport())) {
                errors.add("Extended Choice compatibility requires delimited or disabled environment export.");
            }
            if (definition instanceof FoundryChoiceParameterDefinition choice
                    && !"submitted".equals(choice.getEnvironmentExportOrder())) {
                errors.add("Extended Choice compatibility requires submitted environment export order.");
            }
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
                for (Option option : definition.getSelectableOptions()) {
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
            ChoiceMatrix matrix, AbstractFoundryParameterDefinition definition, List<String> errors) {
        if (!Set.of("any", "at-most-one", "exactly-one").contains(matrix.getRowSelectionMode())) {
            errors.add("Unsupported matrix row selection mode: " + matrix.getRowSelectionMode());
        }
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
        Set<String> cellValues = new HashSet<>();
        for (var cell : matrix.getCells()) {
            if (!rows.contains(cell.getRow()) || !columns.contains(cell.getColumn())) {
                errors.add("Matrix cell references an unknown row or column: " + cell.getValue());
            }
            if (!coordinates.add(cell.getRow() + "\u0000" + cell.getColumn())) {
                errors.add("Duplicate matrix cell coordinate: " + cell.getRow() + "/" + cell.getColumn());
            }
            if (cell.getValue() == null || cell.getValue().isEmpty()) {
                errors.add("Matrix cell values must be non-empty");
            } else if (!cellValues.add(cell.getValue())) {
                errors.add("Duplicate matrix cell value: " + cell.getValue());
            }
        }
    }

    private static void validateDefaultValue(
            AbstractFoundryParameterDefinition definition, String value, List<String> errors) {
        if (value == null || value.isEmpty()) {
            errors.add("Explicit default values must be non-empty");
            return;
        }
        if (definition.isProviderBacked()) {
            return;
        }
        Option option = definition.findOption(value);
        if (option == null) {
            if (definition instanceof FoundryChoiceParameterDefinition single && single.isAllowCustomValue()) {
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
