package io.jenkins.plugins.unifiedparameters.provider;

import io.jenkins.plugins.unifiedparameters.model.Option;
import java.util.List;

final class TransformChain {

    private TransformChain() {}

    static List<Option> apply(List<Option> options, List<TransformStep> transforms, String providerId) {
        List<Option> current = List.copyOf(options);
        for (int index = 0; index < transforms.size(); index++) {
            try {
                current = transforms.get(index).apply(current);
            } catch (RuntimeException exception) {
                throw new ProviderException(
                        io.jenkins.plugins.unifiedparameters.evaluation.ErrorCode.PROVIDER_UNAVAILABLE,
                        providerId,
                        "Transform step " + index + " failed: " + exception.getMessage());
            }
        }
        return current;
    }

    static String digest(List<TransformStep> transforms) {
        return Digests.sha256(transforms.stream()
                .map(TransformStep::fingerprint)
                .reduce("", (left, right) -> left + '\u0001' + right));
    }
}
