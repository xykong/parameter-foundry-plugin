package io.jenkins.plugins.unifiedparameters.migration.extendedchoice;

import com.cwctravel.hudson.plugins.extended_choice_parameter.ExtendedChoiceParameterDefinition;
import hudson.Extension;
import hudson.Util;
import hudson.model.ParameterDefinition;
import io.jenkins.plugins.unifiedparameters.definition.UnifiedChoiceParameterDefinition;
import io.jenkins.plugins.unifiedparameters.definition.UnifiedMultiChoiceParameterDefinition;
import io.jenkins.plugins.unifiedparameters.migration.MigrationClassification;
import io.jenkins.plugins.unifiedparameters.migration.MigrationPlan;
import io.jenkins.plugins.unifiedparameters.migration.SourceMigrationAdapter;
import io.jenkins.plugins.unifiedparameters.model.Option;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Extension
public final class ExtendedChoiceMigrationAdapter extends SourceMigrationAdapter {

    @Override
    public String getSourcePluginId() {
        return "extended-choice-parameter";
    }

    @Override
    public boolean supports(ParameterDefinition definition) {
        return definition instanceof ExtendedChoiceParameterDefinition;
    }

    @Override
    public MigrationPlan inspect(ParameterDefinition definition) {
        if (!(definition instanceof ExtendedChoiceParameterDefinition source)) {
            throw new IllegalArgumentException("Unsupported source definition");
        }
        if (hasUnsafeOrDynamicConfiguration(source)) {
            return new MigrationPlan(
                    MigrationClassification.C,
                    "Dynamic, file, Groovy, JavaScript, or JSON behavior requires a governed redesign.",
                    null,
                    List.of("Choose a governed Provider or Unified Form target."),
                    dynamicSkeleton(source),
                    verificationFixture(source));
        }
        String type = source.getType();
        boolean multiple = ExtendedChoiceParameterDefinition.PARAMETER_TYPE_MULTI_SELECT.equals(type)
                || ExtendedChoiceParameterDefinition.PARAMETER_TYPE_CHECK_BOX.equals(type);
        boolean single = ExtendedChoiceParameterDefinition.PARAMETER_TYPE_SINGLE_SELECT.equals(type)
                || ExtendedChoiceParameterDefinition.PARAMETER_TYPE_RADIO.equals(type);
        if (!single && !multiple) {
            return new MigrationPlan(
                    MigrationClassification.D,
                    "The source type is not a static choice family.",
                    null,
                    List.of("Redesign the parameter using a supported Unified Parameters family."));
        }

        String delimiter = Util.fixEmptyAndTrim(source.getMultiSelectDelimiter());
        if (delimiter == null) {
            delimiter = ",";
        }
        List<Option> options = parseOptions(source.getValue(), delimiter);
        List<String> defaults = parseValues(source.getDefaultValue(), delimiter);
        List<String> optionValues = options.stream().map(Option::getValue).toList();
        List<String> supportedDefaults =
                defaults.stream().filter(optionValues::contains).toList();
        boolean hasEmptyOption = hasEmptyValue(source.getValue(), delimiter);
        boolean hasUnsupportedDefault = supportedDefaults.size() != defaults.size();

        if (multiple) {
            UnifiedMultiChoiceParameterDefinition target = new UnifiedMultiChoiceParameterDefinition(source.getName());
            copyCommon(source, target, options);
            target.setRenderer(
                    ExtendedChoiceParameterDefinition.PARAMETER_TYPE_CHECK_BOX.equals(type) ? "checkbox" : "list");
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

        UnifiedChoiceParameterDefinition target = new UnifiedChoiceParameterDefinition(source.getName());
        copyCommon(source, target, options);
        target.setRenderer(ExtendedChoiceParameterDefinition.PARAMETER_TYPE_RADIO.equals(type) ? "radio" : "select");
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
            ExtendedChoiceParameterDefinition source,
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

    private static boolean hasUnsafeOrDynamicConfiguration(ExtendedChoiceParameterDefinition source) {
        return Util.fixEmpty(source.getPropertyFile()) != null
                || Util.fixEmpty(source.getPropertyKey()) != null
                || Util.fixEmpty(source.getGroovyScript()) != null
                || Util.fixEmpty(source.getGroovyScriptFile()) != null
                || Util.fixEmpty(source.getDefaultPropertyFile()) != null
                || Util.fixEmpty(source.getDefaultPropertyKey()) != null
                || Util.fixEmpty(source.getDefaultGroovyScript()) != null
                || Util.fixEmpty(source.getDefaultGroovyScriptFile()) != null
                || Util.fixEmpty(source.getJavascript()) != null
                || Util.fixEmpty(source.getJavascriptFile()) != null
                || ExtendedChoiceParameterDefinition.PARAMETER_TYPE_JSON.equals(source.getType())
                || ExtendedChoiceParameterDefinition.PARAMETER_TYPE_MULTI_LEVEL_MULTI_SELECT.equals(source.getType())
                || ExtendedChoiceParameterDefinition.PARAMETER_TYPE_MULTI_LEVEL_SINGLE_SELECT.equals(source.getType());
    }

    private static String dynamicSkeleton(ExtendedChoiceParameterDefinition source) {
        boolean multiple = ExtendedChoiceParameterDefinition.PARAMETER_TYPE_MULTI_SELECT.equals(source.getType())
                || ExtendedChoiceParameterDefinition.PARAMETER_TYPE_CHECK_BOX.equals(source.getType())
                || ExtendedChoiceParameterDefinition.PARAMETER_TYPE_MULTI_LEVEL_MULTI_SELECT.equals(source.getType());
        String symbol = multiple ? "unifiedMultiChoice" : "unifiedChoice";
        return symbol + "(\n"
                + "    name: '"
                + source.getName().replace("'", "\\'")
                + "',\n"
                + "    providerId: 'TODO-governed-provider',\n"
                + "    dependsOn: ['TODO-dependency'],\n"
                + "    renderer: '"
                + (multiple ? "chips" : "searchable")
                + "'\n"
                + ")";
    }

    private static String verificationFixture(ExtendedChoiceParameterDefinition source) {
        return "Resolve "
                + source.getName()
                + " for each canonical dependency tuple; compare defaults, valid values, empty/error states, "
                + "Typed API build, browser build, and Rebuild behavior.";
    }
}
