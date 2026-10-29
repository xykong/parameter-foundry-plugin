package io.jenkins.plugins.parameterfoundry.api;

import static org.junit.jupiter.api.Assertions.*;

import hudson.EnvVars;
import hudson.cli.CLICommand;
import hudson.model.StringParameterValue;
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition;
import io.jenkins.plugins.parameterfoundry.evaluation.EvaluationException;
import io.jenkins.plugins.parameterfoundry.model.Option;
import io.jenkins.plugins.parameterfoundry.provider.ProviderOperation;
import io.jenkins.plugins.parameterfoundry.validation.DefinitionValidationService;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.StaplerRequest2;

@WithJenkins
class ActiveChoicesSingleCompatibilityTest {
    private FoundryChoiceParameterDefinition definition() {
        var d = new FoundryChoiceParameterDefinition("NODE");
        d.setChoices(List.of(new Option("built-in"), new Option("agent-z"), new Option("agent-a")));
        d.setStandardApiInputMode("active-choices");
        d.setDefaultMode("none");
        d.setRenderer("radio");
        return d;
    }

    private StaplerRequest2 request(Object raw) {
        String[] fields = null;
        if (raw != null) {
            var array = JSONArray.fromObject(raw);
            fields = new String[array.size()];
            for (int i = 0; i < fields.length; i++) fields[i] = array.getString(i);
        }
        final String[] submittedFields = fields;
        return (StaplerRequest2) Proxy.newProxyInstance(
                StaplerRequest2.class.getClassLoader(),
                new Class<?>[] {StaplerRequest2.class},
                (proxy, method, args) -> method.getName().equals("getParameterValues") ? submittedFields : null);
    }

    @Test
    void matchesActualSourceFactoryFixture(JenkinsRule j) throws Exception {
        try (var stream = getClass()
                .getResourceAsStream("/io/jenkins/plugins/parameterfoundry/active-choices-single-2.8.10.json")) {
            var fixture = JSONObject.fromObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            assertEquals(44, fixture.getJSONArray("cases").size());
            for (Object item : fixture.getJSONArray("cases")) {
                var row = (JSONObject) item;
                var d = definition();
                if (row.getString("type").equals("PT_SINGLE_SELECT")) {
                    d.setRenderer("select");
                    d.setDefaultMode("first");
                }
                var expected = row.getJSONObject("source");
                Object input = row.get("input");
                if (input instanceof net.sf.json.JSONNull) input = null;
                hudson.model.ParameterValue value = null;
                Throwable failure = null;
                try {
                    value = switch (row.getString("channel")) {
                        case "DEFAULT" -> d.getDefaultParameterValue();
                        case "CLI" -> d.createValue((CLICommand) null, (String) input);
                        case "STANDARD" -> d.createValue(request(input));
                        default -> d.createValue(request(null), JSONObject.fromObject(input));
                    };
                } catch (Throwable error) {
                    failure = error;
                }
                if (expected.has("error")) {
                    assertNotNull(failure, row.toString());
                    assertEquals(expected.getString("error"), failure.getClass().getName());
                } else {
                    assertNull(failure, row.toString());
                    assertNotNull(value);
                    assertInstanceOf(StringParameterValue.class, value);
                    var env = new EnvVars();
                    value.buildEnvironment(null, env);
                    assertEquals(expected.getString("literal"), env.get("NODE"), row.toString());
                    assertEquals(expected.getString("value"), value.getValue());
                }
            }
        }
    }

    @Test
    void literalDefaultAndOldStringHistoryRemainExact(JenkinsRule j) throws Exception {
        var d = definition();
        d.setActiveChoicesApiDefaultMode("literal");
        d.setActiveChoicesApiDefaultLiteral("  ");
        assertEquals("  ", d.getDefaultParameterValue().getValue());
        assertFalse(d.isDefault("built-in"));
        var history = new StringParameterValue("NODE", " agent-a || agent-z ");
        var copied = (FoundryChoiceParameterDefinition) d.copyWithDefaultValue(history);
        assertTrue(copied.isLegacyRebuildInput());
        assertEquals(history.getValue(), copied.getLegacyRebuildValue());
        assertTrue(d.isValid(history));
        assertEquals(history, d.createValue((CLICommand) null, history.getValue()));
        var restored = (FoundryChoiceParameterDefinition)
                jenkins.model.Jenkins.XSTREAM2.fromXML(jenkins.model.Jenkins.XSTREAM2.toXML(copied));
        assertFalse(restored.isLegacyRebuildInput());
        assertEquals(history.getValue(), restored.getDefaultParameterValue().getValue());
    }

    @Test
    void inlineEmptyChoicesRemainSelectableLiteralStrings(JenkinsRule j) throws Exception {
        var job = j.createFreeStyleProject();
        for (var values : List.of(List.of("", "flags"), List.of("flags", "", "other"), List.of("flags", ""))) {
            var d = definition();
            d.setRenderer("select");
            d.setDefaultMode("first");
            d.setChoices(values.stream().map(Option::new).toList());
            assertTrue(DefinitionValidationService.validateStoredDefinition(d).isValid());
            assertEquals(values.get(0), d.getDefaultParameterValue().getValue());
            assertEquals(values.get(0), d.getUiDefaultValue());
            assertEquals("", d.createValue((CLICommand) null, "").getValue());
            assertEquals("", d.createValue(request(List.of(""))).getValue());
            assertEquals(
                    "",
                    d.createValue(request(null), JSONObject.fromObject("{\"name\":\"NODE\",\"value\":\"\"}"))
                            .getValue());
            assertEquals(
                    "",
                    TypedParameterAdapterRegistry.decode(job, d, true, "", Map.of(), null, ProviderOperation.VALIDATE)
                            .getValue());
            assertNull(
                    TypedParameterAdapterRegistry.decode(job, d, true, null, Map.of(), null, ProviderOperation.VALIDATE)
                            .getValue());
            var restored = (FoundryChoiceParameterDefinition)
                    jenkins.model.Jenkins.XSTREAM2.fromXML(jenkins.model.Jenkins.XSTREAM2.toXML(d));
            assertEquals(
                    values, restored.getChoices().stream().map(Option::getValue).toList());
            assertEquals("", restored.createValue((CLICommand) null, "").getValue());
            d.setStandardApiInputMode("repeated");
            assertFalse(DefinitionValidationService.validateStoredDefinition(d).isValid());
        }
        var duplicate = definition();
        duplicate.setChoices(List.of(new Option(""), new Option("")));
        assertFalse(
                DefinitionValidationService.validateStoredDefinition(duplicate).isValid());
    }

    @Test
    void typedExplicitAndOrdinaryModesKeepValidation(JenkinsRule j) throws Exception {
        var d = definition();
        var job = j.createFreeStyleProject();
        assertThrows(
                EvaluationException.class,
                () -> TypedParameterAdapterRegistry.decode(
                        job, d, true, "unlisted", Map.of(), null, ProviderOperation.VALIDATE));
        assertEquals(
                "built-in",
                TypedParameterAdapterRegistry.decode(job, d, false, null, Map.of(), null, ProviderOperation.BUILD)
                        .getValue());
        d.setRequired(true);
        assertFalse(DefinitionValidationService.validateStoredDefinition(d).isValid());
        d.setRequired(false);
        d.setSelectionMode("multiple");
        assertFalse(DefinitionValidationService.validateStoredDefinition(d).isValid());
        d.setSelectionMode("single");
        d.setStandardApiInputMode("repeated");
        assertFalse(d.isValid(new StringParameterValue("NODE", "unlisted")));
    }
}
