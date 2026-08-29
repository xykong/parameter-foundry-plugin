package io.jenkins.plugins.unifiedparameters.provider;

import io.jenkins.plugins.unifiedparameters.model.Option;
import java.time.Instant;
import java.util.List;

public record ProviderResolution(
        List<Option> options,
        String providerDigest,
        String optionSetDigest,
        Instant generatedAt,
        Instant expiresAt,
        boolean cacheHit,
        boolean fallback,
        String fallbackReason,
        String redactedProvenance) {

    public ProviderResolution {
        options = List.copyOf(options);
    }
}
