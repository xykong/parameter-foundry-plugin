package io.jenkins.plugins.parameterfoundry.definition;

import static org.junit.jupiter.api.Assertions.*;

import hudson.model.StringParameterValue;
import io.jenkins.plugins.parameterfoundry.model.Option;
import java.util.List;
import java.util.Map;
import net.sf.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.HttpResponses.HttpResponseException;
import org.kohsuke.stapler.StaplerRequest2;

@WithJenkins
class DelimitedRebuildCompatibilityTest {
    private static FoundryChoiceParameterDefinition definition(String delimiter) {
        var definition = new FoundryChoiceParameterDefinition("TARGETS");
        definition.setSelectionMode("multiple");
        definition.setStandardApiInputMode("delimited");
        definition.setDelimiter(delimiter);
        definition.setChoices(List.of(new Option("alpha"), new Option("beta"), new Option("gamma")));
        return definition;
    }

    private static FoundryChoiceParameterValue submit(FoundryChoiceParameterDefinition definition, Object value)
            throws Exception {
        return assertInstanceOf(
                FoundryChoiceParameterValue.class,
                definition.createValue((StaplerRequest2) null, JSONObject.fromObject(Map.of("value", value))));
    }

    @Test
    void historicalScalarAndNativeArraysRetainTheirDistinctTransportContracts(JenkinsRule j) throws Exception {
        var definition = definition(",");
        var historical = new StringParameterValue("TARGETS", "alpha,beta,gamma");
        assertEquals(
                List.of("alpha", "beta", "gamma"),
                submit(definition, historical.getValue()).getValues());
        assertEquals(List.of("gamma"), submit(definition, "gamma").getValues());
        assertEquals(List.of(), submit(definition, "").getValues());
        assertEquals(List.of(), submit(definition, List.of()).getValues());
        assertEquals(
                List.of("alpha,beta", "gamma"),
                io.jenkins.plugins.parameterfoundry.evaluation.InputDecoders.fromSubmittedJson(
                                definition, JSONObject.fromObject(Map.of("value", List.of("alpha,beta", "gamma"))))
                        .values());
        assertThrows(HttpResponseException.class, () -> submit(definition, "alpha,unknown"));
        assertThrows(HttpResponseException.class, () -> submit(definition, List.of("alpha", "unknown")));
        assertThrows(HttpResponseException.class, () -> submit(definition, "alpha,,beta"));
        assertEquals(
                List.of("alpha", "gamma"),
                submit(definition("|"), "alpha|gamma").getValues());
        assertEquals(
                List.of("alpha", "gamma"),
                submit(definition(".*"), "alpha.*gamma").getValues());
        definition.setStandardApiInputMode("repeated");
        definition.setEnvironmentExport("json");
        definition.setChoices(List.of(new Option("alpha,beta"), new Option("gamma")));
        assertEquals(List.of("alpha,beta"), submit(definition, "alpha,beta").getValues());
        definition.setSelectionMode("single");
        definition.setDelimiter("|");
        definition.setStandardApiInputMode("delimited");
        assertEquals(
                List.of("alpha,beta"),
                io.jenkins.plugins.parameterfoundry.evaluation.InputDecoders.fromSubmittedJson(
                                definition, JSONObject.fromObject(Map.of("value", "alpha,beta")))
                        .values());
        assertEquals(0, j.jenkins.getQueue().getItems().length);
    }
}
