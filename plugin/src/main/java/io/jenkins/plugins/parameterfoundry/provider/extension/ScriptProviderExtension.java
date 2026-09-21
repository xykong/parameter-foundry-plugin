package io.jenkins.plugins.unifiedparameters.provider.extension;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import io.jenkins.plugins.unifiedparameters.model.Option;
import io.jenkins.plugins.unifiedparameters.provider.ProviderLoadContext;
import java.util.List;
import net.sf.json.JSONObject;

/**
 * High-risk script execution uses a governed request/result contract. The
 * first-party implementation ships in the Unified Parameters HPI, while
 * external companions may implement the same SPI.
 */
public abstract class ScriptProviderExtension implements ExtensionPoint {

    public abstract String getEngineId();

    public abstract ScriptProviderSecurityContract getSecurityContract();

    public abstract JSONObject getOutputSchema();

    public abstract List<Option> evaluate(ProviderLoadContext context, String governedSourceReference);

    public static ExtensionList<ScriptProviderExtension> all() {
        return ExtensionList.lookup(ScriptProviderExtension.class);
    }
}
