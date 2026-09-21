package io.jenkins.plugins.unifiedparameters.snapshot;

import io.jenkins.plugins.unifiedparameters.model.ChoiceMatrix;
import io.jenkins.plugins.unifiedparameters.model.Option;
import java.util.List;

public record ResolvedProviderOptions(
        List<Option> options,
        ChoiceMatrix matrix,
        ProviderSnapshotMetadata metadata,
        String snapshotId,
        boolean refreshed) {

    public ResolvedProviderOptions {
        options = List.copyOf(options);
    }
}
