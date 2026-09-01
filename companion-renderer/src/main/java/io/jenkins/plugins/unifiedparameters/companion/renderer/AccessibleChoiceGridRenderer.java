package io.jenkins.plugins.unifiedparameters.companion.renderer;

import hudson.Extension;
import io.jenkins.plugins.unifiedparameters.renderer.RendererSecurityContract;
import io.jenkins.plugins.unifiedparameters.renderer.TrustedRendererRegistry;
import io.jenkins.plugins.unifiedparameters.renderer.UnifiedParameterRenderer;
import java.util.Set;

@Extension
public final class AccessibleChoiceGridRenderer extends UnifiedParameterRenderer {

    public static final String ID = "accessible-choice-grid";
    public static final String ADJUNCT = "io.jenkins.plugins.unifiedparameters.accessible-choice-grid";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public Set<String> getSupportedFamilies() {
        return Set.of(
                TrustedRendererRegistry.FAMILY_SINGLE_CHOICE,
                TrustedRendererRegistry.FAMILY_MULTI_CHOICE);
    }

    @Override
    public RendererSecurityContract getSecurityContract() {
        return new RendererSecurityContract(
                1,
                true,
                true,
                false,
                false,
                Set.of(ADJUNCT));
    }
}
