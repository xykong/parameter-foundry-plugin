package io.jenkins.plugins.parameterfoundry.api;

import static org.junit.jupiter.api.Assertions.*;

import hudson.EnvVars;
import hudson.cli.CLICommand;
import hudson.model.ParametersDefinitionProperty;
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition;
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterValue;
import io.jenkins.plugins.parameterfoundry.model.Option;
import io.jenkins.plugins.parameterfoundry.validation.DefinitionValidationService;
import java.util.List;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.StaplerRequest2;

@WithJenkins
class ExtendedChoiceDuplicateOptionsTest {
    private FoundryChoiceParameterDefinition definition(String renderer) {
        var d = new FoundryChoiceParameterDefinition("PLATFORMS");
        d.setSelectionMode("multiple");
        d.setRenderer(renderer);
        d.setStandardApiInputMode("extended-choice");
        d.setEnvironmentExportOrder("submitted");
        d.setDuplicatePolicy("deduplicate");
        d.setDefaultMode("explicit");
        d.setDefaultValues(List.of("he"));
        d.setLegacyDefaultLiteral("he");
        d.setChoices(List.of(new Option("he"), new Option("he"), new Option("mi")));
        return d;
    }

    @Test
    void duplicateEntriesRetainTransportLiteralsAndConfiguration(JenkinsRule j) throws Exception {
        for (String renderer : List.of("list", "checkbox")) {
            var d = definition(renderer);
            assertTrue(DefinitionValidationService.validateStoredDefinition(d).isValid());
            var value = (FoundryChoiceParameterValue) d.createValue((CLICommand) null, "mi,he,he");
            assertEquals(List.of("he", "mi"), value.getValues());
            assertEquals("mi,he,he", value.getLegacyExportLiteral());
            var env = new EnvVars();
            value.buildEnvironment(null, env);
            assertEquals("mi,he,he", env.get("PLATFORMS"));
            var form = JSONObject.fromObject("{name:'PLATFORMS'}");
            form.put("value", JSONArray.fromObject(List.of("he", "he", "mi")));
            var submitted = (FoundryChoiceParameterValue) d.createValue((StaplerRequest2) null, form);
            assertEquals("he,he,mi", submitted.getLegacyExportLiteral());
            var job = j.createFreeStyleProject("duplicates-" + renderer);
            job.addProperty(new ParametersDefinitionProperty(d));
            j.configRoundtrip(job);
            var restored = (FoundryChoiceParameterDefinition)
                    job.getProperty(ParametersDefinitionProperty.class).getParameterDefinition("PLATFORMS");
            assertEquals(
                    List.of("he", "he", "mi"),
                    restored.getChoices().stream().map(Option::getValue).toList());
            assertEquals(
                    "he", ((FoundryChoiceParameterValue) restored.getDefaultParameterValue()).getLegacyExportLiteral());
        }
    }

    @Test
    void ordinaryOrConflictingDuplicateOptionsRemainInvalid(JenkinsRule j) {
        var d = definition("list");
        d.setStandardApiInputMode("repeated");
        d.setLegacyDefaultLiteral(null);
        assertFalse(DefinitionValidationService.validateStoredDefinition(d).isValid());
        d = definition("list");
        var conflicting = new Option("he");
        conflicting.setLabel("A different label");
        d.setChoices(List.of(new Option("he"), conflicting));
        assertFalse(DefinitionValidationService.validateStoredDefinition(d).isValid());
        d = definition("list");
        var disabled = new Option("he");
        disabled.setDisabled(true);
        d.setChoices(List.of(new Option("mi"), disabled, disabled));
        assertFalse(DefinitionValidationService.validateStoredDefinition(d).isValid());
        d = definition("list");
        d.setSelectedDisplay("chips");
        assertFalse(DefinitionValidationService.validateStoredDefinition(d).isValid());
        d = definition("list");
        d.setDuplicatePolicy("reject");
        assertFalse(DefinitionValidationService.validateStoredDefinition(d).isValid());
        d = definition("list");
        d.setAllowCustomValue(true);
        assertFalse(DefinitionValidationService.validateStoredDefinition(d).isValid());
    }
}
