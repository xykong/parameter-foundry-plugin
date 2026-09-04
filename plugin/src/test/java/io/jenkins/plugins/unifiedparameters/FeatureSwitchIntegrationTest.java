package io.jenkins.plugins.unifiedparameters;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jenkins.plugins.unifiedparameters.migration.activechoices.ActiveChoicesMigrationAdapter;
import io.jenkins.plugins.unifiedparameters.provider.UnifiedParametersGlobalConfiguration;
import io.jenkins.plugins.unifiedparameters.renderer.TrustedRendererRegistry;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class FeatureSwitchIntegrationTest {

    @Test
    void firstPartyCapabilitiesCanBeDisabledWithoutRemovingPersistedCode(JenkinsRule jenkins) {
        UnifiedParametersGlobalConfiguration configuration = UnifiedParametersGlobalConfiguration.get();

        assertTrue(new ActiveChoicesMigrationAdapter().isEnabled());
        assertTrue(TrustedRendererRegistry.isTrusted(
                "accessible-choice-grid", TrustedRendererRegistry.FAMILY_SINGLE_CHOICE));

        configuration.setMigrationAdaptersEnabled(false);
        configuration.setScriptProviderEnabled(false);
        configuration.setGitRefsProviderEnabled(false);
        configuration.setAccessibleChoiceGridEnabled(false);

        assertFalse(new ActiveChoicesMigrationAdapter().isEnabled());
        assertFalse(UnifiedParametersGlobalConfiguration.scriptProviderFeatureEnabled());
        assertFalse(UnifiedParametersGlobalConfiguration.gitRefsProviderFeatureEnabled());
        assertFalse(TrustedRendererRegistry.isTrusted(
                "accessible-choice-grid", TrustedRendererRegistry.FAMILY_SINGLE_CHOICE));
    }
}
