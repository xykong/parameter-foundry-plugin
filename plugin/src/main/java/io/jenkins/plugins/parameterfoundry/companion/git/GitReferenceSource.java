package io.jenkins.plugins.parameterfoundry.companion.git;

import io.jenkins.plugins.parameterfoundry.provider.ProviderLoadContext;
import java.util.Map;

@FunctionalInterface
interface GitReferenceSource {
    Map<String, String> fetch(ProviderLoadContext context);
}
