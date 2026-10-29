package io.jenkins.plugins.parameterfoundry.api;

import static org.junit.jupiter.api.Assertions.*;

import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterValue;
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition;
import io.jenkins.plugins.parameterfoundry.model.Option;
import io.jenkins.plugins.parameterfoundry.provider.ProviderOperation;
import java.util.List;
import java.util.Map;
import net.sf.json.JSONArray;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class CoreStringChoiceApiTest {
    @Test
    void ownApiUsesConfiguredOutputAfterSharedEvaluation(JenkinsRule j) throws Exception {
        var d = new FoundryChoiceParameterDefinition("platforms");
        d.setSelectionMode("multiple");
        d.setChoices(List.of(new Option("he"), new Option("mi")));
        d.setDefaultMode("explicit");
        d.setDefaultValues(List.of("he"));
        d.setStandardApiInputMode("extended-choice");
        d.setEnvironmentExportOrder("submitted");
        d.setDuplicatePolicy("deduplicate");
        d.setLegacyDefaultLiteral("he");
        d.setValueTransport("core-string");
        var job = j.createFreeStyleProject();
        job.addProperty(new ParametersDefinitionProperty(d));
        var actual = TypedParameterAdapterRegistry.decode(
                job, d, true, JSONArray.fromObject(List.of("mi", "he")), Map.of(), null, ProviderOperation.VALIDATE);
        assertEquals(
                "mi,he", assertInstanceOf(StringParameterValue.class, actual).getValue());
        var omitted =
                TypedParameterAdapterRegistry.decode(job, d, false, null, Map.of(), null, ProviderOperation.VALIDATE);
        assertEquals("he", assertInstanceOf(StringParameterValue.class, omitted).getValue());
        assertEquals("array", TypedParameterAdapterRegistry.schema(d).getString("type"));
        assertEquals("string", TypedParameterAdapterRegistry.schema(d).getString("buildValueType"));
        assertEquals("core-string", TypedParameterAdapterRegistry.schema(d).getString("valueTransport"));
        d.setProviderId("unregistered-provider");
        var error = assertThrows(
                io.jenkins.plugins.parameterfoundry.evaluation.EvaluationException.class,
                () -> TypedParameterAdapterRegistry.decode(
                        job, d, true, JSONArray.fromObject(List.of("he")), Map.of(), null, ProviderOperation.VALIDATE));
        assertTrue(error.getMessage().contains("Core string transport"));
    }
}
