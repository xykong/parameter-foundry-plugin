package io.jenkins.plugins.unifiedparameters.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jenkins.plugins.unifiedparameters.definition.UnifiedChoiceParameterDefinition;
import io.jenkins.plugins.unifiedparameters.definition.UnifiedMultiChoiceParameterDefinition;
import io.jenkins.plugins.unifiedparameters.evaluation.DecodedInput;
import io.jenkins.plugins.unifiedparameters.evaluation.ParameterEvaluationService;
import io.jenkins.plugins.unifiedparameters.form.FormLayout;
import io.jenkins.plugins.unifiedparameters.model.Option;
import java.lang.reflect.Field;
import java.util.List;
import jenkins.model.Jenkins;
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

    @Test
    void disabledCustomValueLimitDoesNotInvalidateAChoiceDefinition() {
        UnifiedChoiceParameterDefinition definition = new UnifiedChoiceParameterDefinition("CHANNEL");
        definition.setChoices(List.of(new Option("staging")));
        definition.setCustomValueMaxLength(0);

        DefinitionValidationResult result = DefinitionValidationService.validateStoredDefinition(definition);

        assertTrue(result.isValid());
    }

    @Test
    void enabledCustomValueStillRequiresABoundedLimit() {
        UnifiedChoiceParameterDefinition definition = new UnifiedChoiceParameterDefinition("CHANNEL");
        definition.setChoices(List.of(new Option("staging")));
        definition.setAllowCustomValue(true);
        definition.setCustomValueMaxLength(0);

        DefinitionValidationResult result = DefinitionValidationService.validateStoredDefinition(definition);

        assertFalse(result.isValid());
        assertTrue(result.summarize().contains("customValueMaxLength"));
    }

    @Test
    void requiredReactiveWithoutDefaultIsValidButWarnsForOmissionBasedCallers() {
        UnifiedChoiceParameterDefinition definition = new UnifiedChoiceParameterDefinition("ARTIFACT");
        definition.setProviderId("artifacts");
        definition.setDependsOn(List.of("ENVIRONMENT"));
        definition.setRequired(true);

        DefinitionValidationResult result = DefinitionValidationService.validateStoredDefinition(definition);

        assertTrue(result.isValid());
        assertTrue(result.hasWarnings());
        assertTrue(result.getWarnings().stream().anyMatch(warning -> warning.contains("must submit it explicitly")));
    }

    @Test
    void unifiedChoiceSupportsMultipleSelectionMode() {
        UnifiedChoiceParameterDefinition definition = new UnifiedChoiceParameterDefinition("TARGETS");
        definition.setSelectionMode("multiple");
        definition.setRenderer("checkbox");
        definition.setChoices(List.of(new Option("android"), new Option("ios")));

        DefinitionValidationResult result = DefinitionValidationService.validateStoredDefinition(definition);

        assertTrue(result.isValid());
        assertTrue(definition.isMultiple());
    }

    @Test
    void unsupportedSelectionModeIsConfigurationInvalid() {
        UnifiedChoiceParameterDefinition definition = new UnifiedChoiceParameterDefinition("TARGETS");
        definition.setSelectionMode("both");
        definition.setChoices(List.of(new Option("android")));

        DefinitionValidationResult result = DefinitionValidationService.validateStoredDefinition(definition);

        assertFalse(result.isValid());
        assertTrue(result.summarize().contains("selection mode"));
    }

    @Test
    void missingOptionalCollectionsFromLoadedConfigDoNotCrashValidation() throws Exception {
        UnifiedMultiChoiceParameterDefinition definition = new UnifiedMultiChoiceParameterDefinition("TARGETS");
        definition.setChoices(List.of(new Option("staging")));

        Field dependsOn = definition.getClass().getSuperclass().getDeclaredField("dependsOn");
        dependsOn.setAccessible(true);
        dependsOn.set(definition, null);

        DefinitionValidationResult result =
                assertDoesNotThrow(() -> DefinitionValidationService.validateStoredDefinition(definition));

        assertTrue(result.isValid());
        assertTrue(definition.getDependsOn().isEmpty());
        assertDoesNotThrow(definition::getDependsOnText);
    }

    @Test
    void blankOptionalLayoutConditionIsTreatedAsAbsentAfterLoad() throws Exception {
        FormLayout layout = new FormLayout("main", "grid");
        Field visibleWhenField = FormLayout.class.getDeclaredField("visibleWhenField");
        visibleWhenField.setAccessible(true);
        visibleWhenField.set(layout, "");

        assertTrue(layout.getVisibleWhenField() == null);
    }

    @Test
    void absentMaximumSelectionsStillMeansUnlimitedAfterDeserialization() {
        UnifiedChoiceParameterDefinition reloaded =
                (UnifiedChoiceParameterDefinition) Jenkins.XSTREAM2.fromXML(multipleChoiceXmlWithoutSelectionBounds());

        assertEquals("multiple", reloaded.getSelectionMode());
        assertEquals(-1, reloaded.getMaxSelections());
        assertEquals(
                List.of("android", "ios"),
                ParameterEvaluationService.evaluateMultiple(reloaded, DecodedInput.present(List.of("android", "ios"))));
    }

    @Test
    void storedMaximumSelectionsSurvivesDeserialization() {
        UnifiedChoiceParameterDefinition definition = new UnifiedChoiceParameterDefinition("TARGETS");
        definition.setSelectionMode("multiple");
        definition.setChoices(List.of(new Option("android"), new Option("ios")));
        definition.setMaxSelections(1);

        UnifiedChoiceParameterDefinition reloaded =
                (UnifiedChoiceParameterDefinition) Jenkins.XSTREAM2.fromXML(Jenkins.XSTREAM2.toXML(definition));

        assertEquals(1, reloaded.getMaxSelections());
    }

    @Test
    void matrixOnlyDefinitionIsValidWithoutFlatOptions() {
        UnifiedMultiChoiceParameterDefinition definition = new UnifiedMultiChoiceParameterDefinition("TARGETS");
        definition.setRenderer("table");
        definition.setTable(matrix(List.of(cell("android", "a", "android-a", false))));

        DefinitionValidationResult result = DefinitionValidationService.validateStoredDefinition(definition);

        assertTrue(result.isValid(), result.summarize());
    }

    @Test
    void duplicateMatrixCellValueIsConfigurationInvalid() {
        UnifiedMultiChoiceParameterDefinition definition = new UnifiedMultiChoiceParameterDefinition("TARGETS");
        definition.setRenderer("table");
        definition.setTable(
                matrix(List.of(cell("android", "a", "shared", false), cell("android", "b", "shared", false))));

        DefinitionValidationResult result = DefinitionValidationService.validateStoredDefinition(definition);

        assertFalse(result.isValid());
        assertTrue(result.summarize().contains("Duplicate matrix cell value"));
    }

    @Test
    void matrixCellValueCollidingWithFlatOptionIsConfigurationInvalid() {
        UnifiedMultiChoiceParameterDefinition definition = new UnifiedMultiChoiceParameterDefinition("TARGETS");
        definition.setRenderer("table");
        definition.setChoices(List.of(new Option("shared")));
        definition.setTable(matrix(List.of(cell("android", "a", "shared", false))));

        DefinitionValidationResult result = DefinitionValidationService.validateStoredDefinition(definition);

        assertFalse(result.isValid());
        assertTrue(result.summarize().contains("duplicates an option value"));
    }

    @Test
    void declaredMatrixRequiresRowsAndColumns() {
        UnifiedMultiChoiceParameterDefinition definition = new UnifiedMultiChoiceParameterDefinition("TARGETS");
        definition.setRenderer("table");
        io.jenkins.plugins.unifiedparameters.model.ChoiceMatrix matrix =
                new io.jenkins.plugins.unifiedparameters.model.ChoiceMatrix();
        matrix.setCells(List.of(cell("android", "a", "android-a", false)));
        definition.setTable(matrix);

        DefinitionValidationResult result = DefinitionValidationService.validateStoredDefinition(definition);

        assertFalse(result.isValid());
        assertTrue(result.summarize().contains("at least one row and column"));
    }

    private static io.jenkins.plugins.unifiedparameters.model.ChoiceMatrix matrix(
            List<io.jenkins.plugins.unifiedparameters.model.MatrixCell> cells) {
        io.jenkins.plugins.unifiedparameters.model.ChoiceMatrix matrix =
                new io.jenkins.plugins.unifiedparameters.model.ChoiceMatrix();
        matrix.setRows(List.of(
                new io.jenkins.plugins.unifiedparameters.model.AxisOption("android", "Android"),
                new io.jenkins.plugins.unifiedparameters.model.AxisOption("ios", "iOS")));
        matrix.setColumns(List.of(
                new io.jenkins.plugins.unifiedparameters.model.AxisOption("a", "A"),
                new io.jenkins.plugins.unifiedparameters.model.AxisOption("b", "B")));
        matrix.setCells(cells);
        return matrix;
    }

    private static io.jenkins.plugins.unifiedparameters.model.MatrixCell cell(
            String row, String column, String value, boolean disabled) {
        io.jenkins.plugins.unifiedparameters.model.MatrixCell cell =
                new io.jenkins.plugins.unifiedparameters.model.MatrixCell(row, column, value);
        cell.setDisabled(disabled);
        return cell;
    }

    private static String multipleChoiceXmlWithoutSelectionBounds() {
        return """
                <io.jenkins.plugins.unifiedparameters.definition.UnifiedChoiceParameterDefinition>
                  <name>TARGETS</name>
                  <selectionMode>multiple</selectionMode>
                  <choices>
                    <io.jenkins.plugins.unifiedparameters.model.Option>
                      <value>android</value>
                    </io.jenkins.plugins.unifiedparameters.model.Option>
                    <io.jenkins.plugins.unifiedparameters.model.Option>
                      <value>ios</value>
                    </io.jenkins.plugins.unifiedparameters.model.Option>
                  </choices>
                  <renderer>checkbox</renderer>
                  <defaultMode>none</defaultMode>
                  <modelVersion>1</modelVersion>
                </io.jenkins.plugins.unifiedparameters.definition.UnifiedChoiceParameterDefinition>
                """;
    }
}
