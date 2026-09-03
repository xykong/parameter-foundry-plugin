package io.jenkins.plugins.unifiedparameters.companion.git;

import io.jenkins.plugins.unifiedparameters.provider.ProviderLoadContext;
import java.util.Map;

@FunctionalInterface
interface GitReferenceSource {
    Map<String, String> fetch(ProviderLoadContext context);
}
