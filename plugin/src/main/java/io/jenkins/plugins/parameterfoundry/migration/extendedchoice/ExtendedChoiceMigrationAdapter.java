package io.jenkins.plugins.parameterfoundry.migration.extendedchoice;

import hudson.Extension;
import hudson.Util;
import hudson.model.ParameterDefinition;
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition;
import io.jenkins.plugins.parameterfoundry.migration.MigrationClassification;
import io.jenkins.plugins.parameterfoundry.migration.MigrationPlan;
import io.jenkins.plugins.parameterfoundry.migration.MigrationText;
import io.jenkins.plugins.parameterfoundry.migration.SourceMigrationAdapter;
import io.jenkins.plugins.parameterfoundry.model.Option;
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
                    MigrationText.of("MigrationReasonExtendedChoiceDynamic"),
                    null,
                    List.of(MigrationText.of("MigrationDecisionExtendedChoiceGoverned")),
                    dynamicSkeleton(definition, type),
                    verificationFixture(definition));
        }
        boolean multiple = MULTI_SELECT.equals(type) || CHECK_BOX.equals(type);
        boolean single = SINGLE_SELECT.equals(type) || RADIO.equals(type);
        if (!single && !multiple) {
            return new MigrationPlan(
                    MigrationClassification.D,
                    MigrationText.of("MigrationReasonExtendedChoiceNotStatic"),
                    null,
                    List.of(MigrationText.of("MigrationDecisionExtendedChoiceRedesign")));
        }

        String delimiter = Util.fixEmptyAndTrim(readString(definition, "getMultiSelectDelimiter"));
        if (delimiter == null) {
            delimiter = ",";
        }
        // Source option configuration and UI default selection use commas independently of
        // the delimiter used to export a submitted multiple value.
        List<Option> options = parseOptions(readString(definition, "getValue"));
        List<String> defaults = parseValues(readString(definition, "getDefaultValue"), ",");
        List<String> optionValues = options.stream().map(Option::getValue).toList();
        List<String> supportedDefaults =
                defaults.stream().filter(optionValues::contains).toList();
        boolean hasEmptyOption = hasEmptyValue(readString(definition, "getValue"), ",");
        boolean hasUnsupportedDefault = supportedDefaults.size() != defaults.size();

        if (multiple) {
            FoundryChoiceParameterDefinition target = new FoundryChoiceParameterDefinition(definition.getName());
            target.setSelectionMode("multiple");
            copyCommon(definition, target, options);
            target.setRenderer(CHECK_BOX.equals(type) ? "checkbox" : "list");
            target.setDefaultMode(supportedDefaults.isEmpty() ? "none" : "explicit");
            target.setDefaultValues(supportedDefaults);
            target.setLegacyDefaultLiteral(readString(definition, "getDefaultValue"));
            target.setDelimiter(delimiter);
            target.setStandardApiInputMode("extended-choice");
            target.setDuplicatePolicy("deduplicate");
            // The source control exported the submitted order, so the migrated Job keeps it while
            // the canonical value stays the sorted set.
            target.setEnvironmentExportOrder("submitted");
            java.util.Map<MigrationText, MigrationText> facts = new java.util.LinkedHashMap<>();
            facts.put(
                    MigrationText.of("MigrationFactTargetExportOrder"),
                    MigrationText.of("MigrationValueExportOrderSubmitted"));
            List<MigrationText> decisions = new ArrayList<>();
            decisions.add(MigrationText.of("MigrationDecisionExtendedChoiceOrder"));
            if (defaults.isEmpty()) {
                decisions.add(MigrationText.of("MigrationDecisionExtendedChoiceNullDefault"));
            }
            if (hasEmptyOption) {
                decisions.add(MigrationText.of("MigrationDecisionExtendedChoiceEmptyOption"));
            }
            if (hasUnsupportedDefault) {
                decisions.add(MigrationText.of("MigrationDecisionExtendedChoiceStaleDefault"));
            }
            return new MigrationPlan(
                    MigrationClassification.B,
                    MigrationText.of("MigrationReasonExtendedChoiceMulti"),
                    target,
                    decisions,
                    null,
                    null,
                    facts);
        }

        FoundryChoiceParameterDefinition target = new FoundryChoiceParameterDefinition(definition.getName());
        copyCommon(definition, target, options);
        target.setRenderer(RADIO.equals(type) ? "radio" : "select");
        target.setStandardApiInputMode("extended-choice");
        target.setLegacyDefaultLiteral(readString(definition, "getDefaultValue"));
        target.setDelimiter(delimiter);
        target.setDefaultMode(supportedDefaults.isEmpty() ? (RADIO.equals(type) ? "none" : "first") : "explicit");
        if (!supportedDefaults.isEmpty()) {
            target.setDefaultValue(optionValues.stream()
                    .filter(supportedDefaults::contains)
                    .reduce((a, b) -> b)
                    .orElse(null));
        }
        List<MigrationText> decisions = new ArrayList<>();
        if (hasEmptyOption) {
            decisions.add(MigrationText.of("MigrationDecisionExtendedChoiceEmptyOption"));
        }
        if (hasUnsupportedDefault) {
            decisions.add(MigrationText.of("MigrationDecisionExtendedChoiceStaleDefault"));
        }
        return new MigrationPlan(
                decisions.isEmpty() ? MigrationClassification.A : MigrationClassification.B,
                decisions.isEmpty()
                        ? MigrationText.of("MigrationReasonExtendedChoiceSingleDirect")
                        : MigrationText.of("MigrationReasonExtendedChoiceSingleDecided"),
                target,
                decisions);
    }

    /** The static option list of a single/multi select or checkbox source, without executing code. */
    @Override
    public List<String> currentSourceValues(ParameterDefinition definition) {
        if (!supports(definition) || hasUnsafeOrDynamicConfiguration(definition, readString(definition, "getType"))) {
            return List.of();
        }
        return parseValues(readString(definition, "getValue"), ",");
    }

    /** The source default, which must stay selectable after the migration. */
    @Override
    public String currentSourceDefault(ParameterDefinition definition) {
        return supports(definition) ? Util.fixEmptyAndTrim(readString(definition, "getDefaultValue")) : "";
    }

    private static void copyCommon(
            ParameterDefinition source,
            io.jenkins.plugins.parameterfoundry.definition.AbstractFoundryParameterDefinition target,
            List<Option> options) {
        target.setDescription(source.getDescription());
        target.setChoices(options);
    }

    private static List<Option> parseOptions(String raw) {
        List<Option> options = new ArrayList<>();
        for (String value : parseValues(raw, ",")) {
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
        return "foundryChoice(\n"
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

    private static MigrationText verificationFixture(ParameterDefinition source) {
        return MigrationText.of("MigrationCaseExtendedChoiceDependencies", source.getName());
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
