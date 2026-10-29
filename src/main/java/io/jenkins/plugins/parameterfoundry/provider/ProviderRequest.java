package io.jenkins.plugins.parameterfoundry.provider;

import hudson.model.Job;
import java.time.Instant;
import java.util.Map;

public record ProviderRequest(
        Job<?, ?> job,
        String providerId,
        String providerDigest,
        ProviderScope scope,
        String ownerId,
        Map<String, String> dependencyValues,
        ProviderOperation operation,
        Instant deadline,
        String principal) {

    public ProviderRequest {
        dependencyValues = dependencyValues == null ? Map.of() : Map.copyOf(dependencyValues);
    }

    public boolean isExpired() {
        return !Instant.now().isBefore(deadline);
    }
}
