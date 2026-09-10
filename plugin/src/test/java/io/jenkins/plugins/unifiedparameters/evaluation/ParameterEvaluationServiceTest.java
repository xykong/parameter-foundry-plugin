package io.jenkins.plugins.unifiedparameters.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.jenkins.plugins.unifiedparameters.definition.UnifiedChoiceParameterDefinition;
import io.jenkins.plugins.unifiedparameters.definition.UnifiedMultiChoiceParameterDefinition;
import io.jenkins.plugins.unifiedparameters.model.Option;
import java.util.List;
import org.junit.jupiter.api.Test;

class ParameterEvaluationServiceTest {

    @Test
    void multipleChoiceUsesStableMachineValueOrder() {
        UnifiedMultiChoiceParameterDefinition definition = multipleDefinition();

        List<String> result = ParameterEvaluationService.evaluateMultiple(
                definition, DecodedInput.present(List.of("web", "android")));

        assertEquals(List.of("android", "web"), result);
    }

    @Test
    void unifiedChoiceInMultipleModeUsesStableMachineValueOrder() {
        UnifiedChoiceParameterDefinition definition = new UnifiedChoiceParameterDefinition("TARGETS");
        definition.setSelectionMode("multiple");
        definition.setChoices(List.of(new Option("web"), new Option("android")));

        List<String> result = ParameterEvaluationService.evaluateMultiple(
                definition, DecodedInput.present(List.of("web", "android")));

        assertEquals(List.of("android", "web"), result);
    }

    @Test
    void multipleChoiceRejectsDuplicatesByDefault() {
        UnifiedMultiChoiceParameterDefinition definition = multipleDefinition();

        EvaluationException exception = assertThrows(
                EvaluationException.class,
                () -> ParameterEvaluationService.evaluateMultiple(
                        definition, DecodedInput.present(List.of("android", "android"))));

        assertEquals(ErrorCode.DUPLICATE_VALUE, exception.getCode());
    }

    @Test
    void absentAndExplicitEmptyHaveDifferentSemantics() {
        UnifiedMultiChoiceParameterDefinition definition = multipleDefinition();
        definition.setDefaultMode("explicit");
        definition.setDefaultValues(List.of("web"));

        assertEquals(List.of("web"), ParameterEvaluationService.evaluateMultiple(definition, DecodedInput.absent()));
        assertEquals(
                List.of(), ParameterEvaluationService.evaluateMultiple(definition, DecodedInput.present(List.of())));
    }

    @Test
    void singleChoiceRejectsUnknownValue() {
        UnifiedChoiceParameterDefinition definition = new UnifiedChoiceParameterDefinition("CHANNEL");
        definition.setChoices(List.of(new Option("staging"), new Option("production")));

        EvaluationException exception = assertThrows(
                EvaluationException.class,
                () -> ParameterEvaluationService.evaluateSingle(definition, DecodedInput.present(List.of("unknown"))));

        assertEquals(ErrorCode.UNKNOWN_VALUE, exception.getCode());
    }

    @Test
    void firstDefaultSkipsDisabledOptions() {
        UnifiedChoiceParameterDefinition definition = new UnifiedChoiceParameterDefinition("CHANNEL");
        Option disabled = new Option("legacy");
        disabled.setDisabled(true);
        definition.setChoices(List.of(disabled, new Option("staging")));
        definition.setDefaultMode("first");

        assertEquals("staging", ParameterEvaluationService.evaluateSingle(definition, DecodedInput.absent()));
    }

    @Test
    void disabledValueIsRejected() {
        UnifiedChoiceParameterDefinition definition = new UnifiedChoiceParameterDefinition("CHANNEL");
        Option disabled = new Option("production");
        disabled.setDisabled(true);
        definition.setChoices(List.of(new Option("staging"), disabled));

        EvaluationException exception = assertThrows(
                EvaluationException.class,
                () -> ParameterEvaluationService.evaluateSingle(
                        definition, DecodedInput.present(List.of("production"))));

        assertEquals(ErrorCode.DISABLED_VALUE, exception.getCode());
    }

    @Test
    void selectionRangeIsValidated() {
        UnifiedMultiChoiceParameterDefinition definition = multipleDefinition();
        definition.setMinSelections(2);
        definition.setMaxSelections(2);

        assertEquals(
                ErrorCode.MIN_SELECTIONS,
                assertThrows(
                                EvaluationException.class,
                                () -> ParameterEvaluationService.evaluateMultiple(
                                        definition, DecodedInput.present(List.of("android"))))
                        .getCode());
        assertEquals(
                ErrorCode.MAX_SELECTIONS,
                assertThrows(
                                EvaluationException.class,
                                () -> ParameterEvaluationService.evaluateMultiple(
                                        definition, DecodedInput.present(List.of("android", "ios", "web"))))
                        .getCode());
    }

    @Test
    void deduplicatePolicyUsesStableCanonicalValue() {
        UnifiedMultiChoiceParameterDefinition definition = multipleDefinition();
        definition.setDuplicatePolicy("deduplicate");

        assertEquals(
                List.of("android", "web"),
                ParameterEvaluationService.evaluateMultiple(
                        definition, DecodedInput.present(List.of("web", "android", "web"))));
    }

    @Test
    void customSingleValueIsDisabledByDefaultAndUsesSharedRegexLengthPolicy() {
        UnifiedChoiceParameterDefinition definition = new UnifiedChoiceParameterDefinition("RELEASE");
        definition.setChoices(List.of(new Option("stable")));

        EvaluationException disabled = assertThrows(
                EvaluationException.class,
                () -> ParameterEvaluationService.evaluateSingle(
                        definition, DecodedInput.present(List.of("release-42"))));
        assertEquals(ErrorCode.UNKNOWN_VALUE, disabled.getCode());

        definition.setAllowCustomValue(true);
        definition.setCustomValuePattern("release-[0-9]+");
        definition.setCustomValueMaxLength(16);
        assertEquals(
                "release-42",
                ParameterEvaluationService.evaluateSingle(definition, DecodedInput.present(List.of("release-42"))));

        EvaluationException pattern = assertThrows(
                EvaluationException.class,
                () -> ParameterEvaluationService.evaluateSingle(
                        definition, DecodedInput.present(List.of("feature-42"))));
        assertEquals(ErrorCode.CUSTOM_VALUE_INVALID, pattern.getCode());

        EvaluationException length = assertThrows(
                EvaluationException.class,
                () -> ParameterEvaluationService.evaluateSingle(
                        definition, DecodedInput.present(List.of("release-1234567890"))));
        assertEquals(ErrorCode.CUSTOM_VALUE_INVALID, length.getCode());
    }

    private static UnifiedMultiChoiceParameterDefinition multipleDefinition() {
        UnifiedMultiChoiceParameterDefinition definition = new UnifiedMultiChoiceParameterDefinition("TARGETS");
        definition.setChoices(List.of(new Option("web"), new Option("android"), new Option("ios")));
        return definition;
    }
}
