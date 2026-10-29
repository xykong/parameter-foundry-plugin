package io.jenkins.plugins.parameterfoundry.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jenkins.plugins.parameterfoundry.evaluation.ErrorCode;
import io.jenkins.plugins.parameterfoundry.evaluation.EvaluationException;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ScriptProviderDefinitionTest {

    @Test
    void coreStoresOnlyGovernedEngineAndSourceReferences() {
        ScriptProviderDefinition definition =
                new ScriptProviderDefinition("catalog", "sandbox-groovy", "scm://catalog/options.groovy");

        assertEquals("sandbox-groovy", definition.getEngineId());
        assertEquals("scm://catalog/options.groovy", definition.getSourceReference());
        assertTrue(definition.configurationDigest().startsWith("sha256:"));
    }

    @Test
    void inlineScriptTextAndInvalidReferencesAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ScriptProviderDefinition("catalog", "sandbox groovy", "return ['unsafe']"));
    }

    @Test
    void unavailableCompanionFailsClosed(JenkinsRule jenkins) throws Exception {
        ScriptProviderDefinition definition =
                new ScriptProviderDefinition("catalog", "missing-engine", "scm://catalog/options");
        ProviderRequest request = new ProviderRequest(
                jenkins.jenkins.getItem("missing") instanceof hudson.model.Job<?, ?> job
                        ? job
                        : jenkins.createFreeStyleProject("script-provider"),
                definition.getId(),
                definition.configurationDigest(),
                ProviderScope.GLOBAL,
                "global",
                java.util.Map.of(),
                ProviderOperation.BUILD,
                java.time.Instant.now().plusSeconds(5),
                "SYSTEM");

        EvaluationException exception = assertThrows(
                EvaluationException.class,
                () -> definition.load(new ProviderLoadContext(request, java.util.Optional.empty())));

        assertEquals(ErrorCode.PROVIDER_UNAVAILABLE, exception.getCode());
    }
}
