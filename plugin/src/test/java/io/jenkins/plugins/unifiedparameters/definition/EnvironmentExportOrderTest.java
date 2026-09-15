package io.jenkins.plugins.unifiedparameters.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.EnvVars;
import io.jenkins.plugins.unifiedparameters.model.Option;
import io.jenkins.plugins.unifiedparameters.validation.DefinitionValidationService;
import java.util.List;
import org.junit.jupiter.api.Test;

class EnvironmentExportOrderTest {

    @Test
    void theCanonicalOrderIsTheDefaultAndStaysSorted() {
        UnifiedChoiceParameterDefinition definition = multipleChoice();

        assertEquals("canonical", definition.getEnvironmentExportOrder());
        assertEquals("android,web", exported(definition, List.of("web", "android"), "canonical"));
    }

    @Test
    void theSubmittedOrderKeepsTheSourceString() {
        UnifiedChoiceParameterDefinition definition = multipleChoice();

        assertEquals("web,android", exported(definition, List.of("web", "android"), "submitted"));
    }

    @Test
    void theJsonExportFollowsTheSelectedOrder() {
        UnifiedChoiceParameterValue canonical = UnifiedChoiceParameterValue.multiple(
                "TARGETS", List.of("android", "web"), List.of("web", "android"), null, "json", ",", "canonical", null);
        UnifiedChoiceParameterValue submitted = UnifiedChoiceParameterValue.multiple(
                "TARGETS", List.of("android", "web"), List.of("web", "android"), null, "json", ",", "submitted", null);

        assertEquals("[\"android\",\"web\"]", environment(canonical));
        assertEquals("[\"web\",\"android\"]", environment(submitted));
        // The canonical value itself never depends on the export order.
        assertEquals(canonical.getValues(), submitted.getValues());
        assertEquals(canonical, submitted);
    }

    @Test
    void validationRejectsAnUnknownOrder() {
        UnifiedChoiceParameterDefinition definition = multipleChoice();

        assertThrows(IllegalArgumentException.class, () -> definition.setEnvironmentExportOrder("random"));
        assertTrue(
                DefinitionValidationService.validateStoredDefinition(definition).isValid());
    }

    private static String exported(
            UnifiedChoiceParameterDefinition definition, List<String> submitted, String exportOrder) {
        definition.setEnvironmentExportOrder(exportOrder);
        UnifiedChoiceParameterValue value = UnifiedChoiceParameterValue.multiple(
                definition.getName(),
                List.of("android", "web"),
                submitted,
                null,
                "delimited",
                ",",
                definition.getEnvironmentExportOrder(),
                null);
        return environment(value);
    }

    private static String environment(UnifiedChoiceParameterValue value) {
        EnvVars environment = new EnvVars();
        value.buildEnvironment(null, environment);
        return environment.get(value.getName());
    }

    private static UnifiedChoiceParameterDefinition multipleChoice() {
        UnifiedChoiceParameterDefinition definition = new UnifiedChoiceParameterDefinition("TARGETS");
        definition.setSelectionMode("multiple");
        definition.setRenderer("checkbox");
        definition.setChoices(List.of(new Option("android"), new Option("web")));
        return definition;
    }
}
