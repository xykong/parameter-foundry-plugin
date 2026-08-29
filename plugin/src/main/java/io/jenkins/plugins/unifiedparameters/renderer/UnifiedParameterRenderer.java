package io.jenkins.plugins.unifiedparameters.renderer;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import java.util.Set;

/**
 * Companion renderers contribute an ID and reviewed static assets. The SPI
 * deliberately has no method that returns HTML or JavaScript source.
 */
public abstract class UnifiedParameterRenderer implements ExtensionPoint {

    public abstract String getId();

    public abstract Set<String> getSupportedFamilies();

    public abstract RendererSecurityContract getSecurityContract();

    public static ExtensionList<UnifiedParameterRenderer> all() {
        return ExtensionList.lookup(UnifiedParameterRenderer.class);
    }
}
