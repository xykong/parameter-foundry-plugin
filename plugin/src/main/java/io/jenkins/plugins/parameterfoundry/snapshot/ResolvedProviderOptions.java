package io.jenkins.plugins.parameterfoundry.snapshot;

import io.jenkins.plugins.parameterfoundry.model.ChoiceMatrix;
import io.jenkins.plugins.parameterfoundry.model.Option;
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
