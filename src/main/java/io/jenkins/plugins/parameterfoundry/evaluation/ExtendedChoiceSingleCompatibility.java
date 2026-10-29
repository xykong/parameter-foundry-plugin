package io.jenkins.plugins.parameterfoundry.evaluation;

import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition;
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterValue;
import io.jenkins.plugins.parameterfoundry.validation.DefinitionValidationService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/** Single source controls accept joined supported fields and literal form strings. */
public final class ExtendedChoiceSingleCompatibility {
    private ExtendedChoiceSingleCompatibility() {}

    public static boolean enabled(FoundryChoiceParameterDefinition definition) {
        return !definition.isMultiple() && "extended-choice".equals(definition.getStandardApiInputMode());
    }

    public static FoundryChoiceParameterValue defaultValue(FoundryChoiceParameterDefinition definition) {
        requireCompatible(definition);
        String literal = definition.getLegacyDefaultLiteral();
        if (literal == null && "explicit".equals(definition.getDefaultMode())) literal = definition.getDefaultValue();
        return literal == null || literal.isBlank() ? null : value(definition, literal);
    }

    public static FoundryChoiceParameterValue fields(FoundryChoiceParameterDefinition definition, String[] fields) {
        requireCompatible(definition);
        if (fields == null || fields.length == 0) return defaultValue(definition);
        var offered = new HashSet<String>();
        definition.getSelectableOptions().forEach(option -> offered.add(option.getValue()));
        if (definition.isLegacyEmptyChoiceAllowed()) offered.add("");
        var accepted = new ArrayList<String>();
        for (String field : fields) if (offered.contains(field.trim())) accepted.add(field.trim());
        return value(definition, String.join(definition.getDelimiter(), accepted));
    }

    public static FoundryChoiceParameterValue cli(FoundryChoiceParameterDefinition definition, String raw) {
        return fields(definition, raw == null ? null : raw.split(","));
    }

    public static FoundryChoiceParameterValue form(FoundryChoiceParameterDefinition definition, JSONObject submitted) {
        requireCompatible(definition);
        Object raw = submitted.get("value");
        String literal = "";
        if (raw instanceof String text) literal = text;
        else if (raw instanceof JSONArray array) {
            List<String> fields = new ArrayList<>();
            for (Object item : array) fields.add(String.valueOf(item));
            literal = String.join(definition.getDelimiter(), fields);
        }
        return value(definition, literal);
    }

    private static void requireCompatible(FoundryChoiceParameterDefinition definition) {
        if (!enabled(definition))
            throw new EvaluationException(
                    ErrorCode.CONFIGURATION_INVALID,
                    definition.getName(),
                    "Extended Choice single compatibility is not enabled.");
        DefinitionValidationService.requireValid(definition);
    }

    private static FoundryChoiceParameterValue value(FoundryChoiceParameterDefinition definition, String literal) {
        return FoundryChoiceParameterValue.single(
                definition.getName(), literal, definition.getDescription(), definition.getEnvironmentExport(), null);
    }
}
