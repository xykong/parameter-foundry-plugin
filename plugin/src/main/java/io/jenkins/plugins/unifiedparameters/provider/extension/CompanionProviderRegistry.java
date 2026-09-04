package io.jenkins.plugins.unifiedparameters.provider.extension;

import hudson.PluginWrapper;
import io.jenkins.plugins.unifiedparameters.provider.UnifiedParametersGlobalConfiguration;
import java.util.Optional;
import jenkins.model.Jenkins;

public final class CompanionProviderRegistry {

    private CompanionProviderRegistry() {}

    public static Optional<ScriptProviderExtension> findReviewedScriptEngine(String engineId) {
        if (!UnifiedParametersGlobalConfiguration.scriptProviderFeatureEnabled()
                || engineId == null
                || !engineId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) {
            return Optional.empty();
        }
        return ScriptProviderExtension.all().stream()
                .filter(extension -> engineId.equals(extension.getEngineId()))
                .filter(extension -> {
                    PluginWrapper owner = Jenkins.get().getPluginManager().whichPlugin(extension.getClass());
                    if (owner == null) {
                        return false;
                    }
                    return "unified-parameters".equals(owner.getShortName())
                            ? extension.getSecurityContract().isReviewedBuiltInV1()
                            : extension.getSecurityContract().isReviewedV1();
                })
                .filter(extension -> {
                    net.sf.json.JSONObject schema = extension.getOutputSchema();
                    return schema != null
                            && "array".equals(schema.optString("type"))
                            && schema.optJSONObject("items") != null;
                })
                .findFirst();
    }
}
