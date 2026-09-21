package io.jenkins.plugins.parameterfoundry.companion.renderer;

import hudson.Extension;
import io.jenkins.plugins.parameterfoundry.renderer.FoundryParameterRenderer;
import io.jenkins.plugins.parameterfoundry.renderer.RendererSecurityContract;
import io.jenkins.plugins.parameterfoundry.renderer.TrustedRendererRegistry;
import java.util.Set;

@Extension
public final class AccessibleChoiceGridRenderer extends FoundryParameterRenderer {

    public static final String ID = "accessible-choice-grid";
    public static final String ADJUNCT = "io.jenkins.plugins.parameterfoundry.accessible-choice-grid";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public Set<String> getSupportedFamilies() {
        return Set.of(TrustedRendererRegistry.FAMILY_SINGLE_CHOICE, TrustedRendererRegistry.FAMILY_MULTI_CHOICE);
    }

    @Override
    public RendererSecurityContract getSecurityContract() {
        return new RendererSecurityContract(1, true, true, false, false, Set.of(ADJUNCT));
    }
}
