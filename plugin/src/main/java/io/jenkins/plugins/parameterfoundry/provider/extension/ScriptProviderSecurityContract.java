package io.jenkins.plugins.unifiedparameters.provider.extension;

import java.util.Set;

public record ScriptProviderSecurityContract(
        int apiVersion,
        boolean separatePlugin,
        boolean dedicatedConfigurePermission,
        boolean audited,
        boolean boundedOutput,
        boolean schemaValidated,
        boolean sandboxed,
        Set<String> sourceKinds) {

    public ScriptProviderSecurityContract {
        sourceKinds = sourceKinds == null ? Set.of() : Set.copyOf(sourceKinds);
    }

    public boolean isReviewedV1() {
        return apiVersion == 1
                && separatePlugin
                && dedicatedConfigurePermission
                && audited
                && boundedOutput
                && schemaValidated
                && !sourceKinds.isEmpty();
    }

    public boolean isReviewedBuiltInV1() {
        return apiVersion == 1
                && dedicatedConfigurePermission
                && audited
                && boundedOutput
                && schemaValidated
                && sandboxed
                && !sourceKinds.isEmpty();
    }
}
