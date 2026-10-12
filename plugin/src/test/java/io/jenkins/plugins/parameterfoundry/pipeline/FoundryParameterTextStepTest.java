package io.jenkins.plugins.parameterfoundry.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.AbortException;
import hudson.model.FileParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.PasswordParameterValue;
import hudson.model.Result;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition;
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterValue;
import io.jenkins.plugins.parameterfoundry.model.Option;
import java.nio.file.Files;
import java.util.List;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.StaplerRequest2;

@WithJenkins
class FoundryParameterTextStepTest {
    @Test
    void savedTextPreservesEmptyNullWhitespaceAndPersistedLiterals(JenkinsRule j) throws Exception {
        for (String raw : List.of("", " padded ", "red-custom", "blue,,red")) {
            var value = FoundryChoiceParameterValue.legacySubmission(
                    "CHOICES", List.of(), List.of(), "", "delimited", ",", raw);
            assertEquals(raw, FoundryParameterTextStep.savedText(value));
            var reloaded = (FoundryChoiceParameterValue)
                    jenkins.model.Jenkins.XSTREAM2.fromXML(jenkins.model.Jenkins.XSTREAM2.toXML(value));
            assertEquals(raw, FoundryParameterTextStep.savedText(reloaded));
        }
        assertNull(
                FoundryParameterTextStep.savedText(FoundryChoiceParameterValue.single("TEXT", null, null, null, null)));
        assertEquals(" raw ", FoundryParameterTextStep.savedText(new StringParameterValue("TEXT", " raw ")));
        assertEquals(
                " default,raw ",
                FoundryParameterTextStep.savedText(FoundryChoiceParameterValue.legacyDefault(
                        "CHOICES", List.of("red"), List.of("red"), null, "delimited", ",", " default,raw ")));
    }

    @Test
    void rejectsMissingSensitiveFileUnknownAndLostHistoricalText(JenkinsRule j) throws Exception {
        assertThrows(AbortException.class, () -> FoundryParameterTextStep.savedText(null));
        var rejection = assertThrows(
                AbortException.class,
                () -> FoundryParameterTextStep.savedText(new PasswordParameterValue("SECRET", "do-not-expose")));
        assertFalse(rejection.getMessage().contains("do-not-expose"));
        var file = Files.createTempFile("parameter-text-test-", ".txt");
        try {
            assertThrows(
                    AbortException.class,
                    () -> FoundryParameterTextStep.savedText(
                            new FileParameterValue("FILE", file.toFile(), "file.txt")));
        } finally {
            Files.deleteIfExists(file);
        }
        assertThrows(
                AbortException.class,
                () -> FoundryParameterTextStep.savedText(
                        FoundryChoiceParameterValue.multiple("OLD", List.of("red"), null, "delimited", ",", null)));
        assertThrows(AbortException.class, () -> FoundryParameterTextStep.savedText(new UnreviewedStringValue()));
        assertThrows(IllegalArgumentException.class, () -> new FoundryParameterTextStep(" "));
        assertEquals(" exact name ", new FoundryParameterTextStep(" exact name ").getName());
    }

    @Test
    void sandboxedPipelineReadsDecoderOriginalRatherThanCanonicalOrEnvironment(JenkinsRule j) throws Exception {
        j.jenkins.setNumExecutors(0);
        var definition = legacyChoice();
        var value = (FoundryChoiceParameterValue) definition.createValue(
                (StaplerRequest2) null,
                JSONObject.fromObject(java.util.Map.of("name", "CHOICES", "value", "red-custom")));
        assertEquals(List.of(), value.getValue());
        WorkflowJob job = j.createProject(WorkflowJob.class, "raw-text");
        job.addProperty(new ParametersDefinitionProperty(definition));
        job.setDefinition(new CpsFlowDefinition("""
                assert params.CHOICES == []
                def text = foundryParameterText(name: 'CHOICES')
                assert text == 'red-custom'
                assert text.contains('red')
                withEnv(['CHOICES=blue']) {
                  assert env.CHOICES == 'blue'
                  assert foundryParameterText('CHOICES') == text
                }
                echo 'original-text-preserved'
                """, true));
        var build = job.scheduleBuild2(0, new ParametersAction(value)).get();
        j.assertBuildStatusSuccess(build);
        j.assertLogContains("original-text-preserved", build);
        assertTrue(org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval.get()
                .getPendingSignatures()
                .isEmpty());
        build.reload();
        // Reusing the reloaded saved value follows the Rebuild/downstream parameter contract.
        var rebuilt = job.scheduleBuild2(
                        0,
                        new ParametersAction(
                                build.getAction(ParametersAction.class).getParameters()))
                .get();
        j.assertBuildStatusSuccess(rebuilt);
        j.assertLogContains("original-text-preserved", rebuilt);
    }

    @Test
    void declarativeWhenUsesCurrentBuildTextWithoutNode(JenkinsRule j) throws Exception {
        j.jenkins.setNumExecutors(0);
        WorkflowJob job = j.createProject(WorkflowJob.class, "declarative-text");
        job.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("TEXT", "red-custom")));
        job.setDefinition(new CpsFlowDefinition("""
                pipeline {
                  agent none
                  stages {
                    stage('Legacy predicate') {
                      when { expression { foundryParameterText('TEXT').contains('red') } }
                      steps { echo 'predicate-preserved' }
                    }
                  }
                }
                """, true));
        var build = job.scheduleBuild2(0, new ParametersAction(new StringParameterValue("TEXT", "red-custom")))
                .get();
        j.assertBuildStatusSuccess(build);
        j.assertLogContains("predicate-preserved", build);
    }

    @Test
    void stepDoesNotBypassParametersActionSecurityFiltering(JenkinsRule j) throws Exception {
        WorkflowJob job = j.createProject(WorkflowJob.class, "filtered-text");
        job.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("TEXT", "safe")));
        job.setDefinition(new CpsFlowDefinition("foundryParameterText('UNDECLARED')", true));
        var build = job.scheduleBuild2(0, new ParametersAction(new StringParameterValue("UNDECLARED", "hidden")))
                .get();
        j.assertBuildStatus(Result.FAILURE, build);
        j.assertLogContains("no accessible saved parameter", build);
        assertFalse(JenkinsRule.getLog(build).contains("hidden"));
    }

    @Test
    void explicitFallbackHandlesParamsDefaultMergeWithoutReplacingSavedValues(JenkinsRule j) throws Exception {
        j.jenkins.setNumExecutors(0);
        var definition = legacyChoice();
        definition.setLegacyDefaultLiteral("red,blue");
        WorkflowJob job = j.createProject(WorkflowJob.class, "missing-default-text");
        job.addProperty(new ParametersDefinitionProperty(definition, new StringParameterDefinition("NULL", "")));
        job.setDefinition(new CpsFlowDefinition("""
                assert params.containsKey('CHOICES')
                assert foundryParameterText(name: 'CHOICES', fallbackIfMissing: 'red,blue') == 'red,blue'
                assert foundryParameterText(name: 'ABSENT', fallbackIfMissing: '') == ''
                echo 'explicit-default-preserved'
                """, true));
        var missing = job.scheduleBuild2(0, new ParametersAction(List.of())).get();
        j.assertBuildStatusSuccess(missing);
        assertTrue(missing.getAction(ParametersAction.class).getParameters().isEmpty());
        j.assertLogContains("explicit-default-preserved", missing);

        job.setDefinition(new CpsFlowDefinition("""
                assert foundryParameterText(name: 'CHOICES', fallbackIfMissing: 'red,blue') == ''
                def nullable = foundryParameterText(name: 'NULL', fallbackIfMissing: 'fallback')
                assert nullable == null
                """, true));
        var saved = job.scheduleBuild2(
                        0,
                        new ParametersAction(
                                new StringParameterValue("CHOICES", ""),
                                FoundryChoiceParameterValue.single("NULL", null, null, null, null)))
                .get();
        j.assertBuildStatusSuccess(saved);
        assertTrue(org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval.get()
                .getPendingSignatures()
                .isEmpty());
    }

    @Test
    void fallbackDoesNotSuppressSensitiveOrUnsupportedSavedValueErrors(JenkinsRule j) throws Exception {
        j.jenkins.setNumExecutors(0);
        WorkflowJob job = j.createProject(WorkflowJob.class, "fallback-rejections");
        job.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("TEXT", "")));
        job.setDefinition(
                new CpsFlowDefinition("foundryParameterText(name: 'TEXT', fallbackIfMissing: 'safe-literal')", true));
        var sensitive = job.scheduleBuild2(0, new ParametersAction(new PasswordParameterValue("TEXT", "do-not-expose")))
                .get();
        j.assertBuildStatus(Result.FAILURE, sensitive);
        j.assertLogContains("Sensitive parameter text", sensitive);
        assertFalse(JenkinsRule.getLog(sensitive).contains("do-not-expose"));
        var unsupported = job.scheduleBuild2(
                        0,
                        new ParametersAction(FoundryChoiceParameterValue.multiple(
                                "TEXT", List.of("red"), null, "delimited", ",", null)))
                .get();
        j.assertBuildStatus(Result.FAILURE, unsupported);
        j.assertLogContains("no original text", unsupported);
    }

    @Test
    void descriptorBindsExactNameAndServesHelp(JenkinsRule j) throws Exception {
        var original = new FoundryParameterTextStep(" exact name ");
        var rebound = org.jenkinsci.plugins.structs.describable.DescribableModel.of(FoundryParameterTextStep.class)
                .instantiate(java.util.Map.of("name", original.getName(), "fallbackIfMissing", ""));
        assertEquals(original.getName(), rebound.getName());
        assertEquals("", rebound.getFallbackIfMissing());
        var help = j.createWebClient()
                .goTo("descriptor/io.jenkins.plugins.parameterfoundry.pipeline.FoundryParameterTextStep/help/name");
        assertTrue(help.asNormalizedText().contains("current build"));
        assertTrue(j.createWebClient()
                .goTo(
                        "descriptor/io.jenkins.plugins.parameterfoundry.pipeline.FoundryParameterTextStep/help/fallbackIfMissing")
                .asNormalizedText()
                .contains("Sensitive"));
    }

    private static FoundryChoiceParameterDefinition legacyChoice() {
        var definition = new FoundryChoiceParameterDefinition("CHOICES");
        definition.setSelectionMode("multiple");
        definition.setChoices(List.of(new Option("red"), new Option("blue")));
        definition.setStandardApiInputMode("extended-choice");
        definition.setEnvironmentExportOrder("submitted");
        return definition;
    }

    private static final class UnreviewedStringValue extends StringParameterValue {
        private static final long serialVersionUID = 1L;

        UnreviewedStringValue() {
            super("CUSTOM", "do-not-read");
        }

        @Override
        public String getValue() {
            throw new AssertionError("An unknown value getter must never execute.");
        }
    }
}
