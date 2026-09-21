package io.jenkins.plugins.parameterfoundry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jenkins.plugins.parameterfoundry.migration.activechoices.ActiveChoicesMigrationAdapter;
import io.jenkins.plugins.parameterfoundry.provider.ParameterFoundryGlobalConfiguration;
import io.jenkins.plugins.parameterfoundry.renderer.TrustedRendererRegistry;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class FeatureSwitchIntegrationTest {

    @Test
    void firstPartyCapabilitiesCanBeDisabledWithoutRemovingPersistedCode(JenkinsRule jenkins) {
        ParameterFoundryGlobalConfiguration configuration = ParameterFoundryGlobalConfiguration.get();

        assertTrue(new ActiveChoicesMigrationAdapter().isEnabled());
        assertTrue(TrustedRendererRegistry.isTrusted(
                "accessible-choice-grid", TrustedRendererRegistry.FAMILY_SINGLE_CHOICE));

        configuration.setMigrationAdaptersEnabled(false);
        configuration.setScriptProviderEnabled(false);
        configuration.setGitRefsProviderEnabled(false);
        configuration.setAccessibleChoiceGridEnabled(false);

        assertFalse(new ActiveChoicesMigrationAdapter().isEnabled());
        assertFalse(ParameterFoundryGlobalConfiguration.scriptProviderFeatureEnabled());
        assertFalse(ParameterFoundryGlobalConfiguration.gitRefsProviderFeatureEnabled());
        assertFalse(TrustedRendererRegistry.isTrusted(
                "accessible-choice-grid", TrustedRendererRegistry.FAMILY_SINGLE_CHOICE));
    }
}
