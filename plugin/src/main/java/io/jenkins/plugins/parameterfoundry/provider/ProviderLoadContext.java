package io.jenkins.plugins.parameterfoundry.provider;

import java.util.Optional;

public record ProviderLoadContext(ProviderRequest request, Optional<ConnectionProfile> connectionProfile) {

    public void checkCancelled() {
        if (Thread.currentThread().isInterrupted() || request.isExpired()) {
            throw new ProviderException(
                    io.jenkins.plugins.parameterfoundry.evaluation.ErrorCode.PROVIDER_UNAVAILABLE,
                    request.providerId(),
                    "Provider resolution exceeded its deadline.");
        }
    }
}
