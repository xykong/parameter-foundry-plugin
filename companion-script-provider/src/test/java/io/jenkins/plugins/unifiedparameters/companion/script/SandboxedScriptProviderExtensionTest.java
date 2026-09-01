package io.jenkins.plugins.unifiedparameters.companion.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.Descriptor;
import io.jenkins.plugins.unifiedparameters.evaluation.ErrorCode;
import io.jenkins.plugins.unifiedparameters.provider.ProviderException;
import io.jenkins.plugins.unifiedparameters.provider.ProviderLoadContext;
import io.jenkins.plugins.unifiedparameters.provider.ProviderOperation;
import io.jenkins.plugins.unifiedparameters.provider.ProviderRequest;
import io.jenkins.plugins.unifiedparameters.provider.ProviderScope;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.SecureGroovyScript;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class SandboxedScriptProviderExtensionTest {

    @Test
    void evaluatesOnlyCatalogScriptsAndNormalizesBoundedOptions(JenkinsRule jenkins) throws Exception {
        ScriptProviderCatalog.get()
                .setEntries(List.of(new ScriptCatalogEntry(
                        "releases",
                        new SecureGroovyScript(
                                "return ['release-1', [value: 'release-2', label: 'Release 2', disabled: true]]",
                                true))));
        ProviderLoadContext context = context(jenkins, "release-provider");

        var options = new SandboxedScriptProviderExtension().evaluate(context, "catalog:releases");

        assertEquals(
                List.of("release-1", "release-2"),
                options.stream().map(option -> option.getValue()).toList());
        assertEquals("Release 2", options.get(1).getLabel());
        assertTrue(options.get(1).isDisabled());
    }

    @Test
    void rejectsNonSandboxedCatalogEntries(JenkinsRule jenkins) throws Exception {
        SecureGroovyScript script = new SecureGroovyScript("return []", false);

        assertThrows(Descriptor.FormException.class, () -> new ScriptCatalogEntry("unsafe", script));
    }

    @Test
    void rejectsUnknownReferencesAndUnsupportedOutput(JenkinsRule jenkins) throws Exception {
        ScriptProviderCatalog.get()
                .setEntries(
                        List.of(new ScriptCatalogEntry("scalar", new SecureGroovyScript("return 'not-a-list'", true))));
        SandboxedScriptProviderExtension extension = new SandboxedScriptProviderExtension();
        ProviderLoadContext context = context(jenkins, "provider");

        ProviderException missing =
                assertThrows(ProviderException.class, () -> extension.evaluate(context, "catalog:missing"));
        assertEquals(ErrorCode.PROVIDER_UNAVAILABLE, missing.getCode());

        ProviderException scalar =
                assertThrows(ProviderException.class, () -> extension.evaluate(context, "catalog:scalar"));
        assertEquals(ErrorCode.PROVIDER_UNAVAILABLE, scalar.getCode());
    }

    private static ProviderLoadContext context(JenkinsRule jenkins, String providerId) throws Exception {
        var job = jenkins.createFreeStyleProject(providerId);
        return new ProviderLoadContext(
                new ProviderRequest(
                        job,
                        providerId,
                        "sha256:test",
                        ProviderScope.GLOBAL,
                        "global",
                        Map.of("ENVIRONMENT", "test"),
                        ProviderOperation.BUILD,
                        Instant.now().plusSeconds(10),
                        "admin"),
                java.util.Optional.empty());
    }
}
