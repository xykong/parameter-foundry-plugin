package io.jenkins.plugins.unifiedparameters.provider;

import io.jenkins.plugins.unifiedparameters.evaluation.ErrorCode;
import io.jenkins.plugins.unifiedparameters.evaluation.EvaluationException;

public final class ProviderException extends EvaluationException {

    private final int retryAfterSeconds;

    public ProviderException(ErrorCode code, String providerId, String message) {
        this(code, providerId, message, 0);
    }

    public ProviderException(ErrorCode code, String providerId, String message, int retryAfterSeconds) {
        super(code, providerId, message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
