package io.jenkins.plugins.unifiedparameters.companion.renderer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jenkins.plugins.unifiedparameters.renderer.TrustedRendererRegistry;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class AccessibleChoiceGridIntegrationTest {

    @Test
    void installedCompanionIsDiscoverableAndSuppliesOnlyItsDeclaredAdjunct(JenkinsRule jenkins) {
        assertTrue(
                TrustedRendererRegistry.externalRendererIds(TrustedRendererRegistry.FAMILY_SINGLE_CHOICE)
                        .contains(AccessibleChoiceGridRenderer.ID));
        assertEquals(
                Set.of(AccessibleChoiceGridRenderer.ADJUNCT),
                Set.copyOf(TrustedRendererRegistry.trustedAdjuncts(
                        AccessibleChoiceGridRenderer.ID,
                        TrustedRendererRegistry.FAMILY_SINGLE_CHOICE)));
    }
}
