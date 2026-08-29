package io.jenkins.plugins.unifiedparameters.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jenkins.plugins.unifiedparameters.definition.UnifiedMultiChoiceParameterDefinition;
import io.jenkins.plugins.unifiedparameters.model.Option;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefinitionValidationServiceTest {

    @Test
    void duplicateMachineValuesAreConfigurationInvalid() {
        UnifiedMultiChoiceParameterDefinition definition = new UnifiedMultiChoiceParameterDefinition("TARGETS");
        definition.setChoices(List.of(new Option("android"), new Option("android")));

        DefinitionValidationResult result = DefinitionValidationService.validateStoredDefinition(definition);

        assertFalse(result.isValid());
        assertTrue(result.summarize().contains("Duplicate option value"));
    }

    @Test
    void reservedPresenceNameIsInvalidAtJobLevel() {
        UnifiedMultiChoiceParameterDefinition definition =
                new UnifiedMultiChoiceParameterDefinition("__unified_parameters_presence");
        definition.setChoices(List.of(new Option("android")));

        DefinitionValidationResult result = DefinitionValidationService.validateJobDefinitions(List.of(definition));

        assertFalse(result.isValid());
        assertTrue(result.summarize().contains("Reserved"));
    }
}
