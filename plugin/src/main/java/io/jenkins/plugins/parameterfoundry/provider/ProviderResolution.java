package io.jenkins.plugins.parameterfoundry.provider;

import io.jenkins.plugins.parameterfoundry.model.ChoiceMatrix;
import io.jenkins.plugins.parameterfoundry.model.Option;
import java.time.Instant;
import java.util.List;

public record ProviderResolution(
        List<Option> options,
        ChoiceMatrix matrix,
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
