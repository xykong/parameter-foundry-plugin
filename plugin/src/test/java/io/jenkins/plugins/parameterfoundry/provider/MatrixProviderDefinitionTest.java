package io.jenkins.plugins.parameterfoundry.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jenkins.plugins.parameterfoundry.evaluation.DecodedInput;
import io.jenkins.plugins.parameterfoundry.evaluation.EvaluationException;
import io.jenkins.plugins.parameterfoundry.evaluation.ParameterEvaluationService;
import io.jenkins.plugins.parameterfoundry.model.AxisOption;
import io.jenkins.plugins.parameterfoundry.model.ChoiceMatrix;
import io.jenkins.plugins.parameterfoundry.model.Option;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MatrixProviderDefinitionTest {

    @Test
    void crossesTheTwoAxesIntoSelectableCells() {
        MatrixProviderDefinition provider = matrix("registries-on-agents");
        provider.setRowAxisLabel("DOCKER_REGISTRIES");
        provider.setColumnAxisLabel("BUILD_NODE");
        provider.setRowSelectionMode("exactly-one");

        ChoiceMatrix matrix = provider.loadMatrix(context());

        assertEquals(
                List.of("registry.example.com", "harbor.example.net"),
                matrix.getRows().stream().map(AxisOption::getValue).toList());
        assertEquals(
                List.of("built-in", "agent-a.example.net"),
                matrix.getColumns().stream().map(AxisOption::getValue).toList());
        assertEquals(4, matrix.getCells().size());
        assertEquals("DOCKER_REGISTRIES", matrix.getRowAxisLabel());
        assertEquals("BUILD_NODE", matrix.getColumnAxisLabel());
        assertEquals("exactly-one", matrix.getRowSelectionMode());
        assertEquals(
                List.of(
                        "registry.example.com@built-in",
                        "registry.example.com@agent-a.example.net",
                        "harbor.example.net@built-in",
                        "harbor.example.net@agent-a.example.net"),
                provider.load(context()).stream().map(Option::getValue).toList());
        assertEquals(
                "registry.example.com / agent-a.example.net",
                matrix.labelFor(matrix.getCells().get(1)));
    }

    @Test
    void rejectsACellTemplateWithoutRowOrColumn() {
        MatrixProviderDefinition provider = matrix("duplicate-cells");
        provider.setCellValueTemplate("docker login");

        assertThrows(ProviderException.class, () -> provider.loadMatrix(context()));
    }

    @Test
    void rejectsAMissingAxisAndASelfReference() {
        MatrixProviderDefinition provider = matrix("missing-axis");
        provider.setRows(List.of());

        assertThrows(ProviderException.class, () -> provider.loadMatrix(context()));

        MatrixProviderDefinition selfReference = new MatrixProviderDefinition("self");
        selfReference.setRowsProviderId("self");
        selfReference.setColumns(List.of(new AxisOption("a", "A")));

        assertThrows(ProviderException.class, () -> selfReference.loadMatrix(selfContext("self")));
    }

    @Test
    void parsesTheInlineAxisText() {
        MatrixProviderDefinition provider = new MatrixProviderDefinition("text-axis");
        provider.setRowsText("alpha|Alpha\nbeta\n");
        provider.setColumnsText("one|First");

        assertEquals(
                List.of("alpha", "beta"),
                provider.getRows().stream().map(AxisOption::getValue).toList());
        assertEquals("Alpha", provider.getRows().get(0).getLabel());
        assertEquals("beta", provider.getRows().get(1).getLabel());
        assertEquals("one|First", provider.getColumnsText());
    }

    @Test
    void theFingerprintTracksTheAxisAndTheConstraint() {
        MatrixProviderDefinition any = matrix("fingerprint");
        MatrixProviderDefinition single = matrix("fingerprint");
        single.setRowSelectionMode("at-most-one");

        assertNotEquals(any.configurationDigest(), single.configurationDigest());
    }

    @Test
    void rowConstraintRejectsASecondCellAndAMissingRow() {
        MatrixProviderDefinition provider = matrix("registries-on-agents");
        provider.setRowSelectionMode("at-most-one");
        ChoiceMatrix matrix = provider.loadMatrix(context());
        List<Option> options = matrix.selectableOptions();
        io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition definition =
                new io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition("DOCKER_LOGINS");
        definition.setSelectionMode("multiple");
        definition.setRenderer("table");
        definition.setProviderId("registries-on-agents");
        definition.setDefaultMode("none");

        assertEquals(
                List.of("harbor.example.net@built-in"),
                ParameterEvaluationService.evaluateMultiple(
                        definition, DecodedInput.present(List.of("harbor.example.net@built-in")), options, matrix));

        EvaluationException sameRow = assertThrows(
                EvaluationException.class,
                () -> ParameterEvaluationService.evaluateMultiple(
                        definition,
                        DecodedInput.present(
                                List.of("harbor.example.net@built-in", "harbor.example.net@agent-a.example.net")),
                        options,
                        matrix));
        assertTrue(sameRow.getMessage().contains("at most one"), sameRow.getMessage());

        provider.setRowSelectionMode("exactly-one");
        ChoiceMatrix exactlyOne = provider.loadMatrix(context());
        EvaluationException missingRow = assertThrows(
                EvaluationException.class,
                () -> ParameterEvaluationService.evaluateMultiple(
                        definition, DecodedInput.present(List.of("harbor.example.net@built-in")), options, exactlyOne));
        assertTrue(missingRow.getMessage().contains("requires exactly one"), missingRow.getMessage());
    }

    @Test
    void aMinimumSelectionDoesNotBlockTheDefaultSoTheFormCanRender() {
        MatrixProviderDefinition provider = matrix("registries-on-agents");
        provider.setRowSelectionMode("at-most-one");
        List<Option> options = provider.loadMatrix(context()).selectableOptions();
        io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition definition =
                new io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition("DOCKER_LOGINS");
        definition.setSelectionMode("multiple");
        definition.setRenderer("table");
        definition.setProviderId("registries-on-agents");
        definition.setDefaultMode("none");
        definition.setMinSelections(1);

        assertEquals(List.of(), ParameterEvaluationService.defaultFor(definition, options));

        EvaluationException required = assertThrows(
                EvaluationException.class,
                () -> ParameterEvaluationService.evaluateMultiple(
                        definition, DecodedInput.present(List.of()), options, provider.loadMatrix(context())));
        assertTrue(required.getMessage().contains("at least 1 value"), required.getMessage());
    }

    private static MatrixProviderDefinition matrix(String id) {
        MatrixProviderDefinition provider = new MatrixProviderDefinition(id);
        provider.setRows(List.of(
                new AxisOption("registry.example.com", "registry.example.com"),
                new AxisOption("harbor.example.net", "harbor.example.net")));
        provider.setColumns(List.of(
                new AxisOption("built-in", "built-in"), new AxisOption("agent-a.example.net", "agent-a.example.net")));
        return provider;
    }

    private static ProviderLoadContext context() {
        return selfContext("registries-on-agents");
    }

    private static ProviderLoadContext selfContext(String providerId) {
        ProviderRequest request = new ProviderRequest(
                null,
                providerId,
                "digest",
                ProviderScope.GLOBAL,
                "jenkins",
                Map.of(),
                ProviderOperation.RESOLVE,
                Instant.now().plusSeconds(60),
                "tester");
        return new ProviderLoadContext(request, Optional.empty());
    }
}
