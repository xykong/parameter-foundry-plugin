package io.jenkins.plugins.parameterfoundry.provider;

import static org.junit.jupiter.api.Assertions.*;

import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition;
import io.jenkins.plugins.parameterfoundry.evaluation.ActiveChoicesSingleCompatibility;
import io.jenkins.plugins.parameterfoundry.evaluation.EvaluationException;
import io.jenkins.plugins.parameterfoundry.evaluation.ParameterEvaluationService;
import io.jenkins.plugins.parameterfoundry.model.ChoiceSourceMetadata;
import io.jenkins.plugins.parameterfoundry.model.Option;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.sf.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ActiveChoicesChoiceResponseTest {
    private FoundryChoiceParameterDefinition definition() {
        var definition = new FoundryChoiceParameterDefinition("SYNTHETIC");
        definition.setStandardApiInputMode("active-choices");
        definition.setDefaultMode("first");
        definition.setProviderId("source-fixture");
        return definition;
    }

    @Test
    void matchesSourceBrowserAndApiDefaultsWithRealOptionMetadata(JenkinsRule j) throws Exception {
        try (var stream = getClass()
                .getResourceAsStream(
                        "/io/jenkins/plugins/parameterfoundry/active-choices-response-metadata-2.8.10.json")) {
            var fixture = JSONObject.fromObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            assertEquals(11, fixture.getJSONArray("cases").size());
            for (Object item : fixture.getJSONArray("cases")) {
                var row = (JSONObject) item;
                for (boolean cascade : List.of(false, true)) {
                    var options = ActiveChoicesChoiceResponse.options(
                            row.getString("responseBody").getBytes(StandardCharsets.UTF_8), cascade, "fixture");
                    var expected = row.getJSONArray(cascade ? "cascadeUiOptions" : "ordinaryUiOptions");
                    assertEquals(expected.size(), options.size(), row.getString("caseId"));
                    String selected = null;
                    for (int index = 0; index < options.size(); index++) {
                        var choice = options.get(index);
                        var expectedChoice = expected.getJSONObject(index);
                        assertEquals(expectedChoice.getString("value"), choice.getValue(), row.getString("caseId"));
                        assertEquals(expectedChoice.getString("label"), choice.getLabel(), row.getString("caseId"));
                        assertEquals(expectedChoice.getBoolean("disabled"), choice.isDisabled());
                        if (expectedChoice.getBoolean("selected")) selected = expectedChoice.getString("value");
                    }
                    var definition = definition();
                    assertEquals(
                            selected,
                            ParameterEvaluationService.defaultFor(definition, options),
                            row.getString("caseId"));
                    if (!cascade) {
                        assertEquals(
                                row.getString("ordinaryApiDefault"),
                                ActiveChoicesSingleCompatibility.defaultValue(definition, options, null)
                                        .getValue(),
                                row.getString("caseId"));
                    } else {
                        definition.setDependsOn(List.of("DEP"));
                        definition.setActiveChoicesApiDefaultMode("literal");
                        definition.setActiveChoicesApiDefaultLiteral("fallback");
                        assertEquals(
                                "fallback",
                                ActiveChoicesSingleCompatibility.defaultValue(definition, options, null)
                                        .getValue());
                    }
                }
            }
        }
    }

    @Test
    void cascadeJsonCoercionPreservesRichLabelsAndIndependentApiSelection(JenkinsRule j) {
        var bytes = "{\"data\":{\"alpha\":[],\"beta\":[\"Beta:selected\"],\"gamma\":{\"x\":1}}}"
                .getBytes(StandardCharsets.UTF_8);
        var ordinary = ActiveChoicesChoiceResponse.options(bytes, false, "fixture");
        var cascade = ActiveChoicesChoiceResponse.options(bytes, true, "fixture");
        assertEquals("[]", ordinary.get(0).getLabel());
        assertEquals("", cascade.get(0).getLabel());
        assertEquals("Beta", cascade.get(1).getLabel());
        assertTrue(cascade.get(1).getSourceChoiceMetadata().selected());
        assertFalse(cascade.get(1).getSourceChoiceMetadata().apiSelected());
        assertTrue(cascade.get(1).getSourceChoiceMetadata().renderFailure());
        assertFalse(ordinary.get(1).getSourceChoiceMetadata().renderFailure());
        assertEquals("[object Object]", cascade.get(2).getLabel());
        var shadowed = ActiveChoicesChoiceResponse.options(
                "{\"data\":{\"alpha\":[{\"toString\":null}]}}".getBytes(StandardCharsets.UTF_8), true, "fixture");
        assertTrue(shadowed.get(0).getSourceChoiceMetadata().renderFailure());
        assertEquals("beta", ParameterEvaluationService.defaultFor(definition(), cascade));
        assertEquals(
                "alpha",
                ActiveChoicesSingleCompatibility.defaultValue(definition(), cascade, null)
                        .getValue());
    }

    @Test
    void sourceMetadataParticipatesInDigestsAndSurvivesSerialization(JenkinsRule j) {
        var first = Option.sourceChoice("same", "", false, new ChoiceSourceMetadata(false, "raw", "escaped"));
        var selected = Option.sourceChoice("same", "", false, new ChoiceSourceMetadata(true, "raw", "escaped"));
        var otherDefault = Option.sourceChoice("same", "", false, new ChoiceSourceMetadata(false, "other", "escaped"));
        assertEquals("", first.getLabel());
        assertNotEquals(Digests.options(List.of(first)), Digests.options(List.of(selected)));
        assertNotEquals(Digests.options(List.of(first)), Digests.options(List.of(otherDefault)));
        String xml = jenkins.model.Jenkins.XSTREAM2.toXML(first);
        assertTrue(xml.contains("sourceChoiceMetadata"), xml);
        var restored = (Option) jenkins.model.Jenkins.XSTREAM2.fromXML(xml);
        assertAll(
                () -> assertEquals(first.getValue(), restored.getValue(), "value"),
                () -> assertEquals(first.getLabel(), restored.getLabel(), "literal label"),
                () -> assertEquals(
                        first.getSourceChoiceMetadata(), restored.getSourceChoiceMetadata(), "source metadata"),
                () -> assertEquals(first.getTags(), restored.getTags(), "tags"),
                () -> assertEquals(
                        first, restored, xml + "\nRESTORED\n" + jenkins.model.Jenkins.XSTREAM2.toXML(restored)));
        assertEquals(Digests.options(List.of(first)), Digests.options(List.of(restored)));
        assertEquals("", restored.getLabel());
        var ordinary = new Option("ordinary");
        ordinary.setLabel("");
        assertEquals("ordinary", ordinary.getLabel());
    }

    @Test
    void invalidConfigurationAndNonLegacyTypedValuesStillFailClosed(JenkinsRule j) {
        var options = ActiveChoicesChoiceResponse.options(
                "{\"data\":[\"gamma\",\"alpha:selected:disabled\"]}".getBytes(StandardCharsets.UTF_8),
                false,
                "fixture");
        var definition = definition();
        assertEquals("alpha", ParameterEvaluationService.defaultFor(definition, options));
        definition.setRequired(true);
        assertThrows(EvaluationException.class, () -> ParameterEvaluationService.defaultFor(definition, options));
        definition.setRequired(false);
        definition.setStandardApiInputMode("repeated");
        assertEquals("gamma", ParameterEvaluationService.defaultFor(definition, options));
    }
}
