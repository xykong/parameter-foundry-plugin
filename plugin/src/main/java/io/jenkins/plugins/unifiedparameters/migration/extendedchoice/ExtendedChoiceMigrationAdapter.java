package io.jenkins.plugins.unifiedparameters.migration.extendedchoice;

import hudson.Extension;
import hudson.Util;
import hudson.model.ParameterDefinition;
import io.jenkins.plugins.unifiedparameters.definition.UnifiedChoiceParameterDefinition;
import io.jenkins.plugins.unifiedparameters.migration.MigrationClassification;
import io.jenkins.plugins.unifiedparameters.migration.MigrationPlan;
import io.jenkins.plugins.unifiedparameters.migration.SourceMigrationAdapter;
import io.jenkins.plugins.unifiedparameters.model.Option;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Extension
public final class ExtendedChoiceMigrationAdapter extends SourceMigrationAdapter {

    private static final String SOURCE_CLASS =
            "com.cwctravel.hudson.plugins.extended_choice_parameter.ExtendedChoiceParameterDefinition";
    private static final String SINGLE_SELECT = "PT_SINGLE_SELECT";
    private static final String RADIO = "PT_RADIO";
    private static final String MULTI_SELECT = "PT_MULTI_SELECT";
    private static final String CHECK_BOX = "PT_CHECKBOX";
    private static final String JSON = "PT_JSON";
    private static final String MULTI_LEVEL_MULTI_SELECT = "PT_MULTI_LEVEL_MULTI_SELECT";
    private static final String MULTI_LEVEL_SINGLE_SELECT = "PT_MULTI_LEVEL_SINGLE_SELECT";

    @Override
    public String getSourcePluginId() {
        return "extended-choice-parameter";
    }

    @Override
    public boolean supports(ParameterDefinition definition) {
        return definition != null && SOURCE_CLASS.equals(definition.getClass().getName());
    }

    @Override
    public MigrationPlan inspect(ParameterDefinition definition) {
        if (!supports(definition)) {
            throw new IllegalArgumentException("Unsupported source definition");
        }
        String type = readString(definition, "getType");
        if (hasUnsafeOrDynamicConfiguration(definition, type)) {
            return new MigrationPlan(
                    MigrationClassification.C,
                    "Dynamic, file, Groovy, JavaScript, or JSON behavior requires a governed redesign.",
                    null,
                    List.of("Choose a governed Provider or Unified Form target."),
                    dynamicSkeleton(definition, type),
                    verificationFixture(definition));
        }
        boolean multiple = MULTI_SELECT.equals(type) || CHECK_BOX.equals(type);
        boolean single = SINGLE_SELECT.equals(type) || RADIO.equals(type);
        if (!single && !multiple) {
            return new MigrationPlan(
                    MigrationClassification.D,
                    "The source type is not a static choice family.",
                    null,
                    List.of("Redesign the parameter using a supported Unified Parameters family."));
        }

        String delimiter = Util.fixEmptyAndTrim(readString(definition, "getMultiSelectDelimiter"));
        if (delimiter == null) {
            delimiter = ",";
        }
        List<Option> options = parseOptions(readString(definition, "getValue"), delimiter);
        List<String> defaults = parseValues(readString(definition, "getDefaultValue"), delimiter);
        List<String> optionValues = options.stream().map(Option::getValue).toList();
        List<String> supportedDefaults =
                defaults.stream().filter(optionValues::contains).toList();
        boolean hasEmptyOption = hasEmptyValue(readString(definition, "getValue"), delimiter);
        boolean hasUnsupportedDefault = supportedDefaults.size() != defaults.size();

        if (multiple) {
            UnifiedChoiceParameterDefinition target = new UnifiedChoiceParameterDefinition(definition.getName());
            target.setSelectionMode("multiple");
            copyCommon(definition, target, options);
            target.setRenderer(CHECK_BOX.equals(type) ? "checkbox" : "list");
            target.setDefaultMode(supportedDefaults.isEmpty() ? "none" : "explicit");
            target.setDefaultValues(supportedDefaults);
            target.setDelimiter(delimiter);
            target.setStandardApiInputMode("delimited");
            List<String> decisions = new ArrayList<>();
            decisions.add("Confirm that selection order is not part of the business requirement.");
            if (defaults.isEmpty()) {
                decisions.add("Confirm that the legacy null/no-default state may use the canonical empty list.");
            }
            if (hasEmptyOption) {
                decisions.add("Replace the empty source option with optional None behavior or an explicit sentinel.");
            }
            if (hasUnsupportedDefault) {
                decisions.add(
                        "Move non-selectable default text to the description, add it as a real option, or remove it.");
            }
            return new MigrationPlan(
                    MigrationClassification.B,
                    "Extended Choice multi-value output, null defaults, empty options, or stale defaults require explicit semantic decisions.",
                    target,
                    decisions);
        }

        UnifiedChoiceParameterDefinition target = new UnifiedChoiceParameterDefinition(definition.getName());
        copyCommon(definition, target, options);
        target.setRenderer(RADIO.equals(type) ? "radio" : "select");
        target.setDefaultMode(supportedDefaults.isEmpty() ? "none" : "explicit");
        if (!supportedDefaults.isEmpty()) {
            target.setDefaultValue(supportedDefaults.get(0));
        }
        List<String> decisions = new ArrayList<>();
        if (hasEmptyOption) {
            decisions.add("Replace the empty source option with optional None behavior or an explicit sentinel.");
        }
        if (hasUnsupportedDefault) {
            decisions.add(
                    "Move non-selectable default text to the description, add it as a real option, or remove it.");
        }
        return new MigrationPlan(
                decisions.isEmpty() ? MigrationClassification.A : MigrationClassification.B,
                decisions.isEmpty()
                        ? "Static single-choice configuration has a deterministic direct mapping."
                        : "Static choices map directly after empty-option or stale-default semantics are decided.",
                target,
                decisions);
    }

    private static void copyCommon(
            ParameterDefinition source,
            io.jenkins.plugins.unifiedparameters.definition.AbstractUnifiedParameterDefinition target,
            List<Option> options) {
        target.setDescription(source.getDescription());
        target.setChoices(options);
    }

    private static List<Option> parseOptions(String raw, String delimiter) {
        List<Option> options = new ArrayList<>();
        for (String value : parseValues(raw, delimiter)) {
            Option option = new Option(value);
            option.setLabel(value);
            options.add(option);
        }
        return List.copyOf(options);
    }

    private static List<String> parseValues(String raw, String delimiter) {
        String value = Util.fixEmpty(raw);
        if (value == null) {
            return List.of();
        }
        return Arrays.stream(value.split(Pattern.quote(delimiter), -1))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    private static boolean hasEmptyValue(String raw, String delimiter) {
        String value = Util.fixEmpty(raw);
        if (value == null) {
            return false;
        }
        return Arrays.stream(value.split(Pattern.quote(delimiter), -1))
                .map(String::trim)
                .anyMatch(String::isEmpty);
    }

    private static boolean hasUnsafeOrDynamicConfiguration(ParameterDefinition source, String type) {
        return hasValue(source, "getPropertyFile")
                || hasValue(source, "getPropertyKey")
                || hasValue(source, "getGroovyScript")
                || hasValue(source, "getGroovyScriptFile")
                || hasValue(source, "getDefaultPropertyFile")
                || hasValue(source, "getDefaultPropertyKey")
                || hasValue(source, "getDefaultGroovyScript")
                || hasValue(source, "getDefaultGroovyScriptFile")
                || hasValue(source, "getJavascript")
                || hasValue(source, "getJavascriptFile")
                || JSON.equals(type)
                || MULTI_LEVEL_MULTI_SELECT.equals(type)
                || MULTI_LEVEL_SINGLE_SELECT.equals(type);
    }

    private static String dynamicSkeleton(ParameterDefinition source, String type) {
        boolean multiple = MULTI_SELECT.equals(type) || CHECK_BOX.equals(type) || MULTI_LEVEL_MULTI_SELECT.equals(type);
        return "unifiedChoice(\n"
                + "    name: '"
                + source.getName().replace("'", "\\'")
                + "',\n"
                + (multiple ? "    selectionMode: 'multiple',\n" : "")
                + "    providerId: 'TODO-governed-provider',\n"
                + "    dependsOn: ['TODO-dependency'],\n"
                + "    renderer: '"
                + (multiple ? "chips" : "searchable")
                + "'\n"
                + ")";
    }

    private static String verificationFixture(ParameterDefinition source) {
        return "Resolve "
                + source.getName()
                + " for each canonical dependency tuple; compare defaults, valid values, empty/error states, "
                + "Typed API build, browser build, and Rebuild behavior.";
    }

    private static boolean hasValue(Object source, String methodName) {
        return Util.fixEmpty(readString(source, methodName)) != null;
    }

    private static String readString(Object source, String methodName) {
        try {
            Method method = source.getClass().getMethod(methodName);
            Object value = method.invoke(source);
            return value == null ? "" : String.valueOf(value);
        } catch (NoSuchMethodException exception) {
            return "";
        } catch (IllegalAccessException | InvocationTargetException exception) {
            return "";
        }
    }
}
