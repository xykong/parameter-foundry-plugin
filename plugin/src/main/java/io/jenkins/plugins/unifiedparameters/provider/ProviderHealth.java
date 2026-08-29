package io.jenkins.plugins.unifiedparameters.provider;

import java.time.Instant;

public record ProviderHealth(
        String providerId,
        String ownerId,
        boolean healthy,
        Instant checkedAt,
        long latencyMillis,
        int optionCount,
        String message) {}
