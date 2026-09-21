package io.jenkins.plugins.parameterfoundry.renderer;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import java.util.Set;

/**
 * Companion renderers contribute an ID and reviewed static assets. The SPI
 * deliberately has no method that returns HTML or JavaScript source.
 */
public abstract class FoundryParameterRenderer implements ExtensionPoint {

    public abstract String getId();

    public abstract Set<String> getSupportedFamilies();

    public abstract RendererSecurityContract getSecurityContract();

    public static ExtensionList<FoundryParameterRenderer> all() {
        return ExtensionList.lookup(FoundryParameterRenderer.class);
    }
}
