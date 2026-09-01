package io.jenkins.plugins.unifiedparameters.companion.renderer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jenkins.plugins.unifiedparameters.renderer.RendererSecurityContract;
import io.jenkins.plugins.unifiedparameters.renderer.TrustedRendererRegistry;
import org.junit.jupiter.api.Test;

class AccessibleChoiceGridRendererTest {

    @Test
    void exposesOnlyTrustedStaticChoiceRendering() {
        AccessibleChoiceGridRenderer renderer = new AccessibleChoiceGridRenderer();
        RendererSecurityContract contract = renderer.getSecurityContract();

        assertEquals(AccessibleChoiceGridRenderer.ID, renderer.getId());
        assertTrue(renderer.getSupportedFamilies().contains(TrustedRendererRegistry.FAMILY_SINGLE_CHOICE));
        assertTrue(renderer.getSupportedFamilies().contains(TrustedRendererRegistry.FAMILY_MULTI_CHOICE));
        assertTrue(contract.isTrustedV1());
        assertFalse(contract.usesInlineScript());
        assertFalse(contract.acceptsJobHtml());
        assertEquals(
                java.util.Set.of(AccessibleChoiceGridRenderer.ADJUNCT),
                contract.adjuncts());
    }
}
