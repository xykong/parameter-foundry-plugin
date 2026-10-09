package io.jenkins.plugins.parameterfoundry.evaluation;

import io.jenkins.plugins.parameterfoundry.definition.AbstractFoundryParameterDefinition;
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition;
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterValue;
import io.jenkins.plugins.parameterfoundry.model.ChoiceMatrix;
import io.jenkins.plugins.parameterfoundry.model.Option;
import io.jenkins.plugins.parameterfoundry.validation.DefinitionValidationService;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/** Exact transport defaults are independent of the source control's selectable UI defaults. */
public final class ExtendedChoiceDefaultCompatibility {
    private ExtendedChoiceDefaultCompatibility() {}

    public static boolean enabled(AbstractFoundryParameterDefinition definition) {
        return definition instanceof FoundryChoiceParameterDefinition
                && definition.isMultiple()
                && "extended-choice".equals(definition.getStandardApiInputMode());
    }

    /** Empty legacy transport tokens are retained for export, not added to the selectable set. */
    public static List<String> evaluateSubmission(
            FoundryChoiceParameterDefinition definition,
            DecodedInput input,
            List<Option> options,
            ChoiceMatrix matrix) {
        DecodedInput canonicalInput = enabled(definition) && definition.isLegacyEmptyChoiceAllowed() && input.present()
                ? DecodedInput.present(input.values().stream()
                        .filter(value -> value == null || !value.isEmpty())
                        .toList())
                : input;
        return ParameterEvaluationService.evaluateMultiple(definition, canonicalInput, options, matrix);
    }

    public static FoundryChoiceParameterValue evaluate(FoundryChoiceParameterDefinition definition) {
        if (!enabled(definition)) {
            throw new EvaluationException(
                    ErrorCode.CONFIGURATION_INVALID,
                    definition.getName(),
                    "Extended Choice defaults require static multiple-choice compatibility.");
        }
        DefinitionValidationService.requireValid(definition);
        String literal = definition.getLegacyDefaultLiteral();
        if (literal == null && "explicit".equals(definition.getDefaultMode())) {
            literal = String.join(",", definition.getDefaultValues());
        }
        if (literal == null && "first".equals(definition.getDefaultMode())) {
            literal = String.join(",", ParameterEvaluationService.evaluateMultiple(definition, DecodedInput.absent()));
        }
        // The source default factory returns no ParameterValue for a blank effective default.
        if (literal == null || literal.isBlank()) {
            return null;
        }
        var offered = new HashSet<String>();
        definition.getSelectableOptions().forEach(option -> offered.add(option.getValue()));
        List<String> submitted = Arrays.stream(literal.split(","))
                .map(String::trim)
                .filter(offered::contains)
                .toList();
        List<String> canonical = ParameterEvaluationService.evaluateMultiple(
                definition, DecodedInput.present(submitted), definition.getSelectableOptions());
        return FoundryChoiceParameterValue.legacyDefault(
                definition.getName(),
                canonical,
                submitted,
                definition.getDescription(),
                definition.getEnvironmentExport(),
                definition.getDelimiter(),
                literal);
    }
}
