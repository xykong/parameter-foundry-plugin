package io.jenkins.plugins.parameterfoundry.migration;

import hudson.model.ParameterDefinition;
import java.util.List;

/** One parameter of a Job-wide migration batch, with the exact target diff. */
public record MigrationBatchEntry(
        String parameterName,
        String sourcePluginId,
        String sourceClass,
        String classification,
        boolean applicable,
        String reason,
        List<String> decisions,
        String diff,
        String sourceDefinitionDigest,
        String targetType) {

    public MigrationBatchEntry {
        decisions = List.copyOf(decisions);
        diff = diff == null ? "" : diff;
    }

    static MigrationBatchEntry applicable(
            ParameterDefinition source,
            SourceMigrationAdapter adapter,
            MigrationPlan plan,
            String diff,
            String sourceDefinitionDigest) {
        return new MigrationBatchEntry(
                source.getName(),
                adapter.getSourcePluginId(),
                source.getClass().getName(),
                plan.getClassification().name(),
                true,
                plan.getReason(),
                plan.getDecisions(),
                diff,
                sourceDefinitionDigest,
                plan.getTarget().getClass().getSimpleName());
    }

    static MigrationBatchEntry blocked(ParameterDefinition source, SourceMigrationAdapter adapter, MigrationPlan plan) {
        return new MigrationBatchEntry(
                source.getName(),
                adapter.getSourcePluginId(),
                source.getClass().getName(),
                plan.getClassification().name(),
                false,
                plan.getReason(),
                plan.getDecisions(),
                "",
                "",
                "");
    }

    static MigrationBatchEntry failed(ParameterDefinition source, SourceMigrationAdapter adapter, String reason) {
        return new MigrationBatchEntry(
                source.getName(),
                adapter.getSourcePluginId(),
                source.getClass().getName(),
                MigrationClassification.D.name(),
                false,
                reason,
                List.of(),
                "",
                "",
                "");
    }

    static MigrationBatchEntry unsupported(ParameterDefinition source, String reason) {
        return new MigrationBatchEntry(
                source.getName(),
                "",
                source.getClass().getName(),
                MigrationClassification.D.name(),
                false,
                reason,
                List.of(),
                "",
                "",
                "");
    }

    public String getParameterName() {
        return parameterName;
    }

    public String getSourcePluginId() {
        return sourcePluginId;
    }

    public String getSourceClass() {
        return sourceClass;
    }

    public String getClassification() {
        return classification;
    }

    public boolean isApplicable() {
        return applicable;
    }

    public String getReason() {
        return reason;
    }

    public List<String> getDecisions() {
        return decisions;
    }

    public String getDiff() {
        return diff;
    }

    public String getSourceDefinitionDigest() {
        return sourceDefinitionDigest;
    }

    public String getTargetType() {
        return targetType;
    }
}
