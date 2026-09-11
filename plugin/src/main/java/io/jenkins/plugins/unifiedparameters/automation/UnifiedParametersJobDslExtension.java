package io.jenkins.plugins.unifiedparameters.automation;

import hudson.Extension;
import io.jenkins.plugins.unifiedparameters.definition.UnifiedChoiceParameterDefinition;
import io.jenkins.plugins.unifiedparameters.form.FormField;
import io.jenkins.plugins.unifiedparameters.form.FormLayout;
import io.jenkins.plugins.unifiedparameters.form.UnifiedFormParameterDefinition;
import io.jenkins.plugins.unifiedparameters.model.Option;
import java.util.ArrayList;
import java.util.List;
import javaposse.jobdsl.dsl.Context;
import javaposse.jobdsl.dsl.helpers.BuildParametersContext;
import javaposse.jobdsl.plugin.ContextExtensionPoint;
import javaposse.jobdsl.plugin.DslExtensionMethod;

@Extension(optional = true)
public final class UnifiedParametersJobDslExtension extends ContextExtensionPoint {

    @DslExtensionMethod(context = BuildParametersContext.class)
    public Object unifiedChoice(String name, Runnable closure) {
        ChoiceContext context = new ChoiceContext();
        executeInContext(closure, context);
        return context.create(name);
    }

    @DslExtensionMethod(context = BuildParametersContext.class)
    public Object unifiedForm(String name, Runnable closure) {
        FormContext context = new FormContext();
        executeInContext(closure, context);
        return context.create(name);
    }

    public static class ChoiceContext implements Context {
        private final List<Option> options = new ArrayList<>();
        private final List<String> defaultValues = new ArrayList<>();
        private String providerId;
        private String description;
        private String selectionMode = "single";
        private String renderer = "select";
        private String selectedDisplay = "none";
        private String defaultMode = "none";
        private String defaultValue;
        private boolean required;
        private boolean allowCustomValue;
        private String customValuePattern;
        private int customValueMaxLength = 256;
        private int minimumSelections;
        private int maximumSelections = -1;
        private String duplicatePolicy = "reject";
        private String standardApiInputMode = "repeated";
        private String delimiter = ",";
        private final List<String> dependsOn = new ArrayList<>();

        public void option(String value) {
            options.add(new Option(value));
        }

        public void option(String value, String label) {
            Option option = new Option(value);
            option.setLabel(label);
            options.add(option);
        }

        public void option(
                String value, String label, String description, String group, boolean disabled, String disabledReason) {
            Option option = new Option(value);
            option.setLabel(label);
            option.setDescription(description);
            option.setGroup(group);
            option.setDisabled(disabled);
            option.setDisabledReason(disabledReason);
            options.add(option);
        }

        public void provider(String providerId) {
            this.providerId = providerId;
        }

        public void dependsOn(String... parameterNames) {
            this.dependsOn.addAll(List.of(parameterNames));
        }

        public void description(String description) {
            this.description = description;
        }

        public void selectionMode(String selectionMode) {
            this.selectionMode = selectionMode;
        }

        public void renderer(String renderer) {
            this.renderer = renderer;
        }

        public void selectedDisplay(String selectedDisplay) {
            this.selectedDisplay = selectedDisplay;
        }

        public void defaultMode(String defaultMode) {
            this.defaultMode = defaultMode;
        }

        public void defaultValue(String defaultValue) {
            this.defaultMode = "explicit";
            if ("multiple".equals(selectionMode)) {
                this.defaultValues.add(defaultValue);
            } else {
                this.defaultValue = defaultValue;
            }
        }

        public void required(boolean required) {
            this.required = required;
        }

        public void customValue(String pattern, int maximumLength) {
            this.allowCustomValue = true;
            this.customValuePattern = pattern;
            this.customValueMaxLength = maximumLength;
        }

        public void selectionRange(int minimum, int maximum) {
            this.minimumSelections = minimum;
            this.maximumSelections = maximum;
        }

        public void duplicatePolicy(String duplicatePolicy) {
            this.duplicatePolicy = duplicatePolicy;
        }

        public void standardApi(String mode, String delimiter) {
            this.standardApiInputMode = mode;
            this.delimiter = delimiter;
        }

        UnifiedChoiceParameterDefinition create(String name) {
            UnifiedChoiceParameterDefinition definition = new UnifiedChoiceParameterDefinition(name);
            definition.setDescription(description);
            definition.setChoices(options);
            definition.setProviderId(providerId);
            definition.setDependsOn(dependsOn);
            definition.setSelectionMode(selectionMode);
            String effectiveRenderer =
                    "multiple".equals(selectionMode) && "select".equals(renderer) ? "checkbox" : renderer;
            definition.setRenderer(effectiveRenderer);
            definition.setSelectedDisplay(selectedDisplay);
            definition.setDefaultMode(defaultMode);
            definition.setDefaultValue(defaultValue);
            definition.setDefaultValues(defaultValues);
            definition.setRequired(required);
            definition.setAllowCustomValue(allowCustomValue);
            definition.setCustomValuePattern(customValuePattern);
            definition.setCustomValueMaxLength(customValueMaxLength);
            definition.setMinSelections(minimumSelections);
            definition.setMaxSelections(maximumSelections);
            definition.setDuplicatePolicy(duplicatePolicy);
            definition.setStandardApiInputMode(standardApiInputMode);
            definition.setDelimiter(delimiter);
            return definition;
        }
    }

    public static final class FormContext implements Context {
        private final List<FormField> fields = new ArrayList<>();
        private final List<FormLayout> layouts = new ArrayList<>();
        private String description;
        private String outputMode = "map";
        private String environmentExport = "json";

        public void field(String name, String type, Runnable closure) {
            FormFieldContext context = new FormFieldContext();
            executeInContext(closure, context);
            fields.add(context.create(name, type));
        }

        public void layout(String id, String type, Runnable closure) {
            FormLayoutContext context = new FormLayoutContext();
            executeInContext(closure, context);
            layouts.add(context.create(id, type));
        }

        public void description(String description) {
            this.description = description;
        }

        public void output(String outputMode, String environmentExport) {
            this.outputMode = outputMode;
            this.environmentExport = environmentExport;
        }

        UnifiedFormParameterDefinition create(String name) {
            UnifiedFormParameterDefinition definition = new UnifiedFormParameterDefinition(name);
            definition.setDescription(description);
            definition.setFields(fields);
            definition.setLayouts(layouts);
            definition.setOutputMode(outputMode);
            definition.setEnvironmentExport(environmentExport);
            return definition;
        }
    }

    public static final class FormFieldContext implements Context {
        private final List<Option> choices = new ArrayList<>();
        private String label;
        private String description;
        private boolean required;
        private boolean secret;
        private String defaultValue;
        private int minLength = -1;
        private int maxLength = -1;
        private String pattern;
        private Double minimum;
        private Double maximum;
        private int minItems;
        private int maxItems = -1;
        private String itemType = "string";
        private String environmentVariable;

        public void label(String label) {
            this.label = label;
        }

        public void description(String description) {
            this.description = description;
        }

        public void required(boolean required) {
            this.required = required;
        }

        public void secret(boolean secret) {
            this.secret = secret;
        }

        public void defaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
        }

        public void stringRange(int minimum, int maximum, String pattern) {
            this.minLength = minimum;
            this.maxLength = maximum;
            this.pattern = pattern;
        }

        public void numericRange(double minimum, double maximum) {
            this.minimum = minimum;
            this.maximum = maximum;
        }

        public void itemRange(int minimum, int maximum) {
            this.minItems = minimum;
            this.maxItems = maximum;
        }

        public void itemType(String itemType) {
            this.itemType = itemType;
        }

        public void choice(String value) {
            choices.add(new Option(value));
        }

        public void choice(String value, String label) {
            Option option = new Option(value);
            option.setLabel(label);
            choices.add(option);
        }

        public void environmentVariable(String environmentVariable) {
            this.environmentVariable = environmentVariable;
        }

        FormField create(String name, String type) {
            FormField field = new FormField(name, type);
            field.setLabel(label);
            field.setDescription(description);
            field.setRequired(required);
            field.setSecret(secret);
            field.setDefaultValue(defaultValue);
            field.setMinLength(minLength);
            field.setMaxLength(maxLength);
            field.setPattern(pattern);
            field.setMinimum(minimum);
            field.setMaximum(maximum);
            field.setMinItems(minItems);
            field.setMaxItems(maxItems);
            field.setItemType(itemType);
            field.setChoices(choices);
            field.setEnvironmentVariable(environmentVariable);
            return field;
        }
    }

    public static final class FormLayoutContext implements Context {
        private final List<String> fieldNames = new ArrayList<>();
        private String title;
        private int columns = 1;
        private boolean collapsed;
        private String visibleWhenField;
        private String visibleWhenValue;

        public void title(String title) {
            this.title = title;
        }

        public void columns(int columns) {
            this.columns = columns;
        }

        public void collapsed(boolean collapsed) {
            this.collapsed = collapsed;
        }

        public void fields(String... fields) {
            this.fieldNames.addAll(List.of(fields));
        }

        public void visibleWhen(String field, String value) {
            this.visibleWhenField = field;
            this.visibleWhenValue = value;
        }

        FormLayout create(String id, String type) {
            FormLayout layout = new FormLayout(id, type);
            layout.setTitle(title);
            layout.setColumns(columns);
            layout.setCollapsed(collapsed);
            layout.setFieldNames(fieldNames);
            layout.setVisibleWhenField(visibleWhenField);
            layout.setVisibleWhenValue(visibleWhenValue);
            return layout;
        }
    }
}
