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
class ActiveChoicesMultipleCompatibilityTest {
    private FoundryChoiceParameterDefinition definition() {
        var definition = new FoundryChoiceParameterDefinition("REGIONS");
        definition.setChoices(List.of(new Option("fallback"), new Option("other")));
        definition.setSelectionMode("multiple");
        definition.setRenderer("list");
        definition.setVisibleItemCount(1);
        definition.setDefaultMode("none");
        definition.setStandardApiInputMode("active-choices");
        definition.setEnvironmentExport("string");
        return definition;
    }

    private StaplerRequest2 request(String[] fields) {
        return (StaplerRequest2) Proxy.newProxyInstance(
                StaplerRequest2.class.getClassLoader(),
                new Class<?>[] {StaplerRequest2.class},
                (proxy, method, args) -> method.getName().equals("getParameterValues") ? fields : null);
    }

    @Test
    void matchesNativeMultipleLiteralValues(JenkinsRule j) throws Exception {
        try (var stream = getClass()
                .getResourceAsStream("/io/jenkins/plugins/parameterfoundry/active-choices-multiple-2.8.10.json")) {
            var fixture = JSONObject.fromObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            assertEquals(18, fixture.getJSONArray("cases").size());
            var definition = definition();
            assertTrue(DefinitionValidationService.validateStoredDefinition(definition)
                    .isValid());
            assertTrue(definition.isActiveChoicesInput());
            assertFalse(definition.isActiveChoicesSingleInput());
            assertEquals(
                    fixture.getString("apiDefault"),
                    definition.getDefaultParameterValue().getValue());
            assertFalse(definition.isDefault("fallback"));
            for (Object item : fixture.getJSONArray("cases")) {
                var row = (JSONObject) item;
                Object input = row.get("input");
                if (input instanceof net.sf.json.JSONNull) input = null;
                var value = row.getString("channel").equals("CLI")
                        ? definition.createValue((CLICommand) null, (String) input)
                        : definition.createValue(
                                request(null), JSONObject.fromObject(input).element("name", "REGIONS"));
                assertInstanceOf(StringParameterValue.class, value);
                assertEquals(row.getString("expected"), value.getValue(), row.toString());
                var environment = new EnvVars();
                value.buildEnvironment(null, environment);
                assertEquals(row.getString("expected"), environment.get("REGIONS"));
            }
            assertEquals("fallback", definition.createValue(request(null)).getValue());
            assertEquals("", definition.createValue(request(new String[] {""})).getValue());
            assertThrows(IllegalArgumentException.class, () -> definition.createValue(request(new String[0])));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> definition.createValue(request(new String[] {"fallback", "other"})));
        }
    }

    @Test
    void typedListsRemainValidatedAndHistoryRetainsRawStrings(JenkinsRule j) throws Exception {
        var definition = definition();
        var project = j.createFreeStyleProject();
        assertEquals(
                "fallback",
                TypedParameterAdapterRegistry.decode(
                                project, definition, false, null, Map.of(), null, ProviderOperation.BUILD)
                        .getValue());
        var selected = TypedParameterAdapterRegistry.decode(
                project,
                definition,
                true,
                JSONArray.fromObject(List.of("other", "fallback")),
                Map.of(),
                null,
                ProviderOperation.VALIDATE);
        assertInstanceOf(StringParameterValue.class, selected);
        assertEquals("fallback,other", selected.getValue());
        assertThrows(
                EvaluationException.class,
                () -> TypedParameterAdapterRegistry.decode(
                        project,
                        definition,
                        true,
                        JSONArray.fromObject(List.of("unknown")),
                        Map.of(),
                        null,
                        ProviderOperation.VALIDATE));
        var history = new StringParameterValue("REGIONS", " other , fallback ");
        var copied = (FoundryChoiceParameterDefinition) definition.copyWithDefaultValue(history);
        assertTrue(copied.isLegacyRebuildInput());
        assertEquals(history.getValue(), copied.getLegacyRebuildValue());
        assertEquals(history.getValue(), copied.getDefaultParameterValue().getValue());
        var restored = (FoundryChoiceParameterDefinition)
                jenkins.model.Jenkins.XSTREAM2.fromXML(jenkins.model.Jenkins.XSTREAM2.toXML(copied));
        assertFalse(restored.isLegacyRebuildInput());
        assertEquals(history.getValue(), restored.getDefaultParameterValue().getValue());
    }

    @Test
    void ordinaryMultipleAndUnsupportedSourceShapesStayRestricted(JenkinsRule j) {
        var definition = definition();
        definition.setStandardApiInputMode("repeated");
        assertFalse(
                DefinitionValidationService.validateStoredDefinition(definition).isValid());
        definition.setStandardApiInputMode("active-choices");
        definition.setChoices(List.of(new Option(""), new Option("other")));
        assertFalse(
                DefinitionValidationService.validateStoredDefinition(definition).isValid());
        definition.setChoices(List.of(new Option("fallback")));
        definition.setRequired(true);
        assertFalse(
                DefinitionValidationService.validateStoredDefinition(definition).isValid());
    }

    @Test
    void generatedSnippetsRebindStaticAndReactiveDefaults(JenkinsRule j) throws Exception {
        for (boolean reactive : List.of(false, true)) {
            var original = definition();
            original.setDescription("Quoted ' description\nnext line");
            if (reactive) {
                original.setProviderId("region-options");
                original.setChoices(List.of());
                original.setDependsOn(List.of("MODE"));
                original.setActiveChoicesApiDefaultMode("literal");
                original.setActiveChoicesApiDefaultLiteral(" raw default ");
                original.setActiveChoicesFilterLength(5);
                original.setActiveChoicesFilterMode("regex");
                original.setActiveChoicesRefreshMode("retain-valid");
            }
            String snippet = io.jenkins.plugins.parameterfoundry.migration.MigrationSnippets.forTarget(original);
            String mapLiteral = "[" + snippet.substring("foundryChoice(".length(), snippet.lastIndexOf(')')) + "]";
            @SuppressWarnings("unchecked")
            var arguments = (Map<String, Object>) new groovy.lang.GroovyShell().evaluate(mapLiteral);
            var rebound = org.jenkinsci.plugins.structs.describable.DescribableModel.of(
                            FoundryChoiceParameterDefinition.class)
                    .instantiate(arguments);
            assertTrue(DefinitionValidationService.validateStoredDefinition(rebound)
                    .isValid());
            assertEquals(original.getDescription(), rebound.getDescription());
            assertEquals(original.getProviderId(), rebound.getProviderId());
            assertEquals(original.getDependsOn(), rebound.getDependsOn());
            assertEquals(original.getActiveChoicesFilterLength(), rebound.getActiveChoicesFilterLength());
            assertEquals(original.getActiveChoicesFilterMode(), rebound.getActiveChoicesFilterMode());
            assertEquals(original.getActiveChoicesRefreshMode(), rebound.getActiveChoicesRefreshMode());
            assertEquals(original.getVisibleItemCount(), rebound.getVisibleItemCount());
            assertEquals(
                    original.getDefaultParameterValue().getValue(),
                    rebound.getDefaultParameterValue().getValue());
            var restored = (FoundryChoiceParameterDefinition)
                    jenkins.model.Jenkins.XSTREAM2.fromXML(jenkins.model.Jenkins.XSTREAM2.toXML(rebound));
            assertTrue(DefinitionValidationService.validateStoredDefinition(restored)
                    .isValid());
            assertEquals(
                    rebound.getDefaultParameterValue().getValue(),
                    restored.getDefaultParameterValue().getValue());
        }
    }

    @Test
    void uiPoliciesPersistAndInvalidateProviderSnapshots(JenkinsRule j) throws Exception {
        var definition = definition();
        definition.setActiveChoicesFilterMode("regex");
        definition.setActiveChoicesRefreshMode("retain-valid");
        definition.setActiveChoicesFilterLength(5);
        var restored = (FoundryChoiceParameterDefinition)
                jenkins.model.Jenkins.XSTREAM2.fromXML(jenkins.model.Jenkins.XSTREAM2.toXML(definition));
        assertEquals("regex", restored.getActiveChoicesFilterMode());
        assertEquals("retain-valid", restored.getActiveChoicesRefreshMode());
        assertEquals(5, restored.getActiveChoicesFilterLength());
        var ordinary = definition();
        ordinary.setStandardApiInputMode("repeated");
        ordinary.setEnvironmentExport("delimited");
        ordinary.setActiveChoicesFilterMode("regex");
        assertFalse(
                DefinitionValidationService.validateStoredDefinition(ordinary).isValid());
        restored.setActiveChoicesRefreshMode("unknown");
        assertFalse(
                DefinitionValidationService.validateStoredDefinition(restored).isValid());
        definition.setProviderId("regions");
        definition.setChoices(List.of());
        definition.setActiveChoicesApiDefaultMode("literal");
        definition.setActiveChoicesApiDefaultLiteral("fallback");
        var provider = new io.jenkins.plugins.parameterfoundry.provider.StaticProviderDefinition("regions");
        provider.setOptions(List.of(new Option("fallback"), new Option("other")));
        io.jenkins.plugins.parameterfoundry.provider.ParameterFoundryGlobalConfiguration.get()
                .setProviders(List.of(provider));
        var project = j.createFreeStyleProject();
        project.addProperty(new hudson.model.ParametersDefinitionProperty(definition));
        var resolution = io.jenkins.plugins.parameterfoundry.provider.ProviderResolutionService.resolve(
                project, definition, ProviderOperation.RESOLVE, Map.of());
        var snapshot = io.jenkins.plugins.parameterfoundry.snapshot.ProviderSnapshotStore.get()
                .create(
                        project,
                        definition,
                        Map.of(),
                        resolution,
                        io.jenkins.plugins.parameterfoundry.snapshot.SnapshotAudience.BUILD_VALUE);
        definition.setActiveChoicesRefreshMode("default");
        assertThrows(
                EvaluationException.class,
                () -> TypedParameterAdapterRegistry.decode(
                        project,
                        definition,
                        true,
                        JSONArray.fromObject(List.of("fallback")),
                        Map.of(),
                        snapshot.snapshotId(),
                        ProviderOperation.BUILD));
    }
}
