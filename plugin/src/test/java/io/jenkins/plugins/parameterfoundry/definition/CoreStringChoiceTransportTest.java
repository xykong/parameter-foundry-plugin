package io.jenkins.plugins.parameterfoundry.definition;

import static org.junit.jupiter.api.Assertions.*;

import hudson.EnvVars;
import hudson.cli.CLICommand;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterValue;
import io.jenkins.plugins.parameterfoundry.model.Option;
import io.jenkins.plugins.parameterfoundry.validation.DefinitionValidationService;
import java.util.List;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.StaplerRequest2;

@WithJenkins
class CoreStringChoiceTransportTest {
    private FoundryChoiceParameterDefinition definition() {
        var d = new FoundryChoiceParameterDefinition("platforms");
        d.setSelectionMode("multiple");
        d.setChoices(List.of(new Option("he"), new Option("mi")));
        d.setDefaultMode("explicit");
        d.setDefaultValues(List.of("he"));
        d.setStandardApiInputMode("extended-choice");
        d.setEnvironmentExportOrder("submitted");
        d.setDuplicatePolicy("deduplicate");
        d.setRenderer("list");
        d.setLegacyDefaultLiteral("he");
        d.setValueTransport("core-string");
        return d;
    }

    @Test
    void cliFormDefaultAndPersistenceRetainActualCoreStringValues(JenkinsRule j) throws Exception {
        var d = definition();
        assertTrue(DefinitionValidationService.validateStoredDefinition(d).isValid());
        assertEquals(
                "he",
                assertInstanceOf(StringParameterValue.class, d.getDefaultParameterValue())
                        .getValue());
        for (var entry : java.util.Map.of(
                        "he",
                        "he",
                        "mi,he",
                        "mi,he",
                        "he,,mi",
                        "he,mi",
                        " mi ,he ",
                        "mi,he",
                        "",
                        "",
                        "he,he",
                        "he,he",
                        "other",
                        "")
                .entrySet()) {
            String text = entry.getKey();
            String expected = entry.getValue();
            var value = assertInstanceOf(StringParameterValue.class, d.createValue((CLICommand) null, text));
            assertEquals(expected, value.getValue());
            assertEquals(StringParameterValue.class, value.getClass());
            var restored = assertInstanceOf(
                    StringParameterValue.class, j.jenkins.XSTREAM2.fromXML(j.jenkins.XSTREAM2.toXML(value)));
            assertEquals(expected, restored.getValue());
            EnvVars environment = new EnvVars();
            restored.buildEnvironment(null, environment);
            assertEquals(expected, environment.get("platforms"));
            assertEquals(expected, environment.get("PLATFORMS"));
        }
        var form = JSONObject.fromObject(java.util.Map.of("name", "platforms", "value", List.of("mi", "he")));
        assertEquals(
                "mi,he",
                assertInstanceOf(StringParameterValue.class, d.createValue((StaplerRequest2) null, form))
                        .getValue());
        var copy = (FoundryChoiceParameterDefinition)
                d.copyWithDefaultValue(new StringParameterValue("platforms", "mi,he"));
        assertEquals("core-string", copy.getValueTransport());
        assertEquals(
                "mi,he",
                assertInstanceOf(StringParameterValue.class, copy.getDefaultParameterValue())
                        .getValue());
    }

    @Test
    void pipelineConsumesOriginalStringWithoutSourceRewrite(JenkinsRule j) throws Exception {
        var d = definition();
        WorkflowJob job = j.createProject(WorkflowJob.class, "core-string-pipeline");
        job.addProperty(new ParametersDefinitionProperty(d));
        job.setDefinition(new CpsFlowDefinition(
                "assert params.platforms instanceof String; assert params.platforms == 'he-custom,mi'; assert params.platforms.contains('he'); echo 'legacy-string-preserved'",
                true));
        var run = job.scheduleBuild2(
                        0,
                        new ParametersAction(d.createValue(
                                (StaplerRequest2) null,
                                JSONObject.fromObject(java.util.Map.of("name", "platforms", "value", "he-custom,mi")))))
                .get();
        j.assertBuildStatusSuccess(run);
        j.assertLogContains("legacy-string-preserved", run);
        assertEquals(
                StringParameterValue.class,
                run.getAction(ParametersAction.class).getParameter("platforms").getClass());
    }

    @Test
    void configurationRoundtripKeepsTransportAndGeneratedSnippet(JenkinsRule j) throws Exception {
        var d = definition();
        var job = j.createFreeStyleProject();
        job.addProperty(new ParametersDefinitionProperty(d));
        j.configRoundtrip(job);
        var restored = (FoundryChoiceParameterDefinition)
                job.getProperty(ParametersDefinitionProperty.class).getParameterDefinition("platforms");
        assertEquals("core-string", restored.getValueTransport());
        assertTrue(io.jenkins.plugins.parameterfoundry.migration.MigrationSnippets.forTarget(restored)
                .contains("valueTransport: 'core-string'"));
        assertInstanceOf(StringParameterValue.class, restored.getDefaultParameterValue());
    }

    @Test
    void disabledEnvironmentExportCannotBeBypassed(JenkinsRule j) throws Exception {
        var d = definition();
        d.setEnvironmentExport("none");
        assertFalse(DefinitionValidationService.validateStoredDefinition(d).isValid());
        assertThrows(hudson.AbortException.class, () -> d.createValue((CLICommand) null, "he"));
        assertNull(d.getDefaultParameterValue());
        d.setValueTransport("typed");
        var value = d.createValue((CLICommand) null, "he");
        EnvVars environment = new EnvVars();
        value.buildEnvironment(null, environment);
        assertFalse(environment.containsKey("platforms"));
        assertFalse(environment.containsKey("PLATFORMS"));
    }

    @Test
    void presentationGettersRejectInvalidTransportBeforeLoadingProvider(JenkinsRule j) throws Exception {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var provider = new io.jenkins.plugins.parameterfoundry.provider.SharedProviderDefinition("counting") {
            @Override
            public List<Option> load(io.jenkins.plugins.parameterfoundry.provider.ProviderLoadContext context) {
                calls.incrementAndGet();
                return List.of(new Option("he"));
            }

            @Override
            protected String providerFingerprint() {
                return "counting-fixture";
            }
        };
        io.jenkins.plugins.parameterfoundry.provider.ParameterFoundryGlobalConfiguration.get()
                .setProviders(List.of(provider));
        var d = definition();
        d.setProviderId("counting");
        var job = j.createFreeStyleProject();
        job.addProperty(new ParametersDefinitionProperty(d));
        assertTrue(io.jenkins.plugins.parameterfoundry.provider.ProviderRegistry.findProvider(job, "counting")
                .isPresent());
        assertFalse(d.isDefault("he"));
        assertTrue(d.getFallbackDefaultValues().isEmpty());
        assertFalse(d.isValid(new StringParameterValue("platforms", "he")));
        assertEquals(0, calls.get());
    }

    @Test
    void typedDefaultAndUnsupportedConfigurationsStayDistinct(JenkinsRule j) throws Exception {
        var d = definition();
        d.setValueTransport("typed");
        assertInstanceOf(FoundryChoiceParameterValue.class, d.createValue((CLICommand) null, "he,mi"));
        d.setValueTransport("core-string");
        d.setProviderId("unregistered-provider");
        var rejected = assertThrows(hudson.AbortException.class, () -> d.createValue((CLICommand) null, "he"));
        assertTrue(rejected.getMessage().contains("Core string transport"));
        assertFalse(d.isValid(new StringParameterValue("platforms", "he")));
        d.setProviderId(null);
        d.setStandardApiInputMode("typed");
        assertFalse(DefinitionValidationService.validateStoredDefinition(d).isValid());
        assertThrows(IllegalArgumentException.class, () -> d.setValueTransport("unknown"));
    }
}
