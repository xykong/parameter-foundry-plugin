package io.jenkins.plugins.unifiedparameters.migration;

import java.util.List;

public record MigrationPreviewHandle(
        String jobFullName,
        String parameterName,
        String sourcePluginId,
        MigrationClassification classification,
        String jobDigest,
        String sourceDefinitionDigest,
        List<String> requiredDecisions) {

    public MigrationPreviewHandle {
        requiredDecisions = List.copyOf(requiredDecisions);
    }
}
