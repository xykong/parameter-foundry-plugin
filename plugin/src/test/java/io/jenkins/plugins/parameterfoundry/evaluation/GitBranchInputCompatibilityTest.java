package io.jenkins.plugins.parameterfoundry.evaluation;

import static org.junit.jupiter.api.Assertions.*;

import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition;
import java.util.List;
import net.sf.json.JSONObject;
import org.junit.jupiter.api.Test;

class GitBranchInputCompatibilityTest {

    @Test
    void cliPreservesUnknownAndWhitespaceWhileEmptyRequestsDefault() {
        assertEquals(DecodedInput.absent(), GitBranchInputCompatibility.fromCli(null));
        assertEquals(DecodedInput.absent(), GitBranchInputCompatibility.fromCli(""));
        for (String value : List.of(" ", " revision-not-in-options ", "refs/heads/topic", "a,b")) {
            assertEquals(DecodedInput.present(List.of(value)), GitBranchInputCompatibility.fromCli(value));
        }
    }

    @Test
    void standardRequestUsesOnlyFirstFieldWithoutTrimming() {
        assertEquals(DecodedInput.absent(), GitBranchInputCompatibility.fromStandardFields(null));
        assertEquals(DecodedInput.absent(), GitBranchInputCompatibility.fromStandardFields(new String[] {"", " \t"}));
        assertEquals(
                DecodedInput.absent(),
                GitBranchInputCompatibility.fromStandardFields(new String[] {" ", " topic ", "ignored"}));
        assertEquals(
                DecodedInput.present(List.of(" topic ")),
                GitBranchInputCompatibility.fromStandardFields(new String[] {" topic ", "ignored"}));
    }

    @Test
    void formArraysBecomeOneCommaJoinedScalarAndStringsStayLiteral() {
        var definition = new FoundryChoiceParameterDefinition("REF");
        definition.setDefaultValue("fallback");
        assertEquals(
                DecodedInput.present(List.of("a,b,a")),
                GitBranchInputCompatibility.fromForm(definition, JSONObject.fromObject("{value:['a','b','a']}")));
        assertEquals(
                DecodedInput.present(List.of(" topic ")),
                GitBranchInputCompatibility.fromForm(definition, JSONObject.fromObject("{value:' topic '}")));
        assertEquals(
                DecodedInput.present(List.of("fallback")),
                GitBranchInputCompatibility.fromForm(definition, JSONObject.fromObject("{value:['']}")));
    }

    @Test
    void formFallbackUsesStoredLiteralAndNeverResolvesTop() {
        var definition = new FoundryChoiceParameterDefinition("REF");
        definition.setDefaultValue("");
        definition.setDefaultMode("first");
        for (String json : List.of("{}", "{value:''}", "{value:[]}", "{value:null}", "{value:true}", "{value:42}")) {
            var input = GitBranchInputCompatibility.fromForm(definition, JSONObject.fromObject(json));
            assertEquals(DecodedInput.present(List.of("")), input);
            assertFalse(GitBranchInputCompatibility.requiresOptions(definition, input));
        }
        definition.setDefaultValue("release/prod");
        assertEquals(
                DecodedInput.present(List.of("release/prod")),
                GitBranchInputCompatibility.fromForm(definition, new JSONObject()));
        definition.setDefaultValue(null);
        assertEquals(
                DecodedInput.present(List.of("null")),
                GitBranchInputCompatibility.fromForm(definition, new JSONObject()));
    }

    @Test
    void onlyOmittedTopDefaultNeedsOptions() {
        var definition = new FoundryChoiceParameterDefinition("REF");
        for (String mode : List.of("none", "explicit", "first")) {
            definition.setDefaultMode(mode);
            assertEquals(
                    "first".equals(mode),
                    GitBranchInputCompatibility.requiresOptions(definition, DecodedInput.absent()));
            for (String literal : List.of("", " ", "unknown")) {
                assertFalse(GitBranchInputCompatibility.requiresOptions(
                        definition, DecodedInput.present(List.of(literal))));
            }
        }
    }
}
