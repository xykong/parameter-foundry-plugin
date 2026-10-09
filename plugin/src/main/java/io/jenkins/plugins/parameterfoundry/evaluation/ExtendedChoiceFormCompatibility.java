package io.jenkins.plugins.parameterfoundry.evaluation;

import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition;
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterValue;
import io.jenkins.plugins.parameterfoundry.validation.DefinitionValidationService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/** Source form JSON preserves literals instead of using the CLI's filtering rules. */
public final class ExtendedChoiceFormCompatibility {
    private ExtendedChoiceFormCompatibility() {}

    public static FoundryChoiceParameterValue evaluate(
            FoundryChoiceParameterDefinition definition, JSONObject submitted) {
        DefinitionValidationService.requireValid(definition);
        if (!ExtendedChoiceDefaultCompatibility.enabled(definition)) {
            throw new EvaluationException(
                    ErrorCode.CONFIGURATION_INVALID,
                    definition.getName(),
                    "Extended Choice form compatibility requires a static multiple choice.");
        }
        Object raw = submitted.get("value");
        String literal;
        List<String> fields;
        if (raw instanceof String text) {
            literal = text;
            fields = Arrays.asList(text.split(Pattern.quote(definition.getDelimiter()), -1));
        } else if (raw instanceof JSONArray array) {
            fields = new ArrayList<>();
            for (Object value : array) fields.add(String.valueOf(value));
            literal = String.join(definition.getDelimiter(), fields);
        } else {
            literal = "";
            fields = List.of();
        }
        var offered = new HashSet<String>();
        definition.getSelectableOptions().forEach(option -> offered.add(option.getValue()));
        List<String> supported =
                fields.stream().map(String::trim).filter(offered::contains).toList();
        List<String> canonical = ParameterEvaluationService.evaluateMultiple(
                definition, DecodedInput.present(supported), definition.getSelectableOptions());
        return FoundryChoiceParameterValue.legacySubmission(
                definition.getName(),
                canonical,
                supported,
                definition.getDescription(),
                definition.getEnvironmentExport(),
                definition.getDelimiter(),
                literal);
    }
}
