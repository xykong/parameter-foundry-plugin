package io.jenkins.plugins.parameterfoundry.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.EnvVars;
import io.jenkins.plugins.parameterfoundry.model.Option;
import io.jenkins.plugins.parameterfoundry.validation.DefinitionValidationService;
import java.util.List;
import org.junit.jupiter.api.Test;

class EnvironmentExportOrderTest {

    @Test
    void theCanonicalOrderIsTheDefaultAndStaysSorted() {
        FoundryChoiceParameterDefinition definition = multipleChoice();

        assertEquals("canonical", definition.getEnvironmentExportOrder());
        assertEquals("android,web", exported(definition, List.of("web", "android"), "canonical"));
    }

    @Test
    void theSubmittedOrderKeepsTheSourceString() {
        FoundryChoiceParameterDefinition definition = multipleChoice();

        assertEquals("web,android", exported(definition, List.of("web", "android"), "submitted"));
    }

    @Test
    void theJsonExportFollowsTheSelectedOrder() {
        FoundryChoiceParameterValue canonical = FoundryChoiceParameterValue.multiple(
                "TARGETS", List.of("android", "web"), List.of("web", "android"), null, "json", ",", "canonical", null);
        FoundryChoiceParameterValue submitted = FoundryChoiceParameterValue.multiple(
                "TARGETS", List.of("android", "web"), List.of("web", "android"), null, "json", ",", "submitted", null);

        assertEquals("[\"android\",\"web\"]", environment(canonical));
        assertEquals("[\"web\",\"android\"]", environment(submitted));
        // The canonical value itself never depends on the export order.
        assertEquals(canonical.getValues(), submitted.getValues());
        assertEquals(canonical, submitted);
    }

    @Test
    void validationRejectsAnUnknownOrder() {
        FoundryChoiceParameterDefinition definition = multipleChoice();

        assertThrows(IllegalArgumentException.class, () -> definition.setEnvironmentExportOrder("random"));
        assertTrue(
                DefinitionValidationService.validateStoredDefinition(definition).isValid());
    }

    private static String exported(
            FoundryChoiceParameterDefinition definition, List<String> submitted, String exportOrder) {
        definition.setEnvironmentExportOrder(exportOrder);
        FoundryChoiceParameterValue value = FoundryChoiceParameterValue.multiple(
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

    private static String environment(FoundryChoiceParameterValue value) {
        EnvVars environment = new EnvVars();
        value.buildEnvironment(null, environment);
        return environment.get(value.getName());
    }

    private static FoundryChoiceParameterDefinition multipleChoice() {
        FoundryChoiceParameterDefinition definition = new FoundryChoiceParameterDefinition("TARGETS");
        definition.setSelectionMode("multiple");
        definition.setRenderer("checkbox");
        definition.setChoices(List.of(new Option("android"), new Option("web")));
        return definition;
    }
}
