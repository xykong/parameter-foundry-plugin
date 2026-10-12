package io.jenkins.plugins.parameterfoundry.api;

import static org.junit.jupiter.api.Assertions.*;

import hudson.model.StringParameterValue;
import io.jenkins.plugins.parameterfoundry.definition.FoundryReferenceParameterDefinition;
import io.jenkins.plugins.parameterfoundry.evaluation.EvaluationException;
import io.jenkins.plugins.parameterfoundry.migration.MigrationSnippets;
import io.jenkins.plugins.parameterfoundry.provider.ProviderOperation;
import java.util.Map;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ReferenceTypedApiTest {
    @Test
    void typedValuesRemainStringsAndRejectSnapshots(JenkinsRule j) throws Exception {
        var project = j.createFreeStyleProject();
        var definition = new FoundryReferenceParameterDefinition("REFERENCE");
        definition.setHtml("<b>notice</b>");
        var schema = TypedParameterAdapterRegistry.schema(definition);
        assertTrue(schema.getBoolean("supported"));
        assertEquals("parameter-foundry", schema.getString("owner"));
        assertEquals("string", schema.getString("type"));
        for (String value : new String[] {"", " outside ", "a,b", "<b>literal input</b>"}) {
            var result = assertInstanceOf(
                    StringParameterValue.class,
                    TypedParameterAdapterRegistry.decode(
                            project, definition, true, value, Map.of(), null, ProviderOperation.BUILD));
            assertEquals(value, result.getValue());
        }
        assertEquals(
                "",
                TypedParameterAdapterRegistry.decode(
                                project, definition, false, null, Map.of(), null, ProviderOperation.BUILD)
                        .getValue());
        assertThrows(
                EvaluationException.class,
                () -> TypedParameterAdapterRegistry.decode(
                        project, definition, true, 7, Map.of(), null, ProviderOperation.BUILD));
        assertThrows(
                EvaluationException.class,
                () -> TypedParameterAdapterRegistry.decode(
                        project, definition, false, null, Map.of(), "foreign", ProviderOperation.BUILD));
    }

    @Test
    void migrationSnippetRebindsPresentationAndDescription(JenkinsRule j) throws Exception {
        var definition = new FoundryReferenceParameterDefinition("REFERENCE");
        definition.setHtml("<b>quote's \\ caption</b>");
        definition.setDescription("Literal reference");
        String snippet = MigrationSnippets.forTarget(definition);
        var shell = new groovy.lang.GroovyShell();
        Object arguments = shell.evaluate("def foundryReference(Map arguments) { arguments }; " + snippet);
        var rebound = DescribableModel.of(FoundryReferenceParameterDefinition.class)
                .instantiate((Map<String, Object>) arguments);
        assertEquals(definition.getName(), rebound.getName());
        assertEquals(definition.getHtml(), rebound.getHtml());
        assertEquals(definition.getDescription(), rebound.getDescription());
    }
}
