package io.jenkins.plugins.parameterfoundry.migration;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Dry-run result for every parameter of one Job: the parameters that can be migrated atomically
 * and the ones that need a human decision first.
 */
public final class MigrationBatchPreview {

    private final String jobFullName;
    private final String jobDigest;
    private final List<MigrationBatchEntry> entries;
    private final List<MigrationBatchEntry> blocked;

    public MigrationBatchPreview(
            String jobFullName,
            String jobDigest,
            List<MigrationBatchEntry> entries,
            List<MigrationBatchEntry> blocked) {
        this.jobFullName = jobFullName;
        this.jobDigest = jobDigest;
        this.entries = List.copyOf(entries);
        this.blocked = List.copyOf(blocked);
    }

    public String getJobFullName() {
        return jobFullName;
    }

    public String getJobDigest() {
        return jobDigest;
    }

    public List<MigrationBatchEntry> getEntries() {
        return entries;
    }

    public List<MigrationBatchEntry> getBlocked() {
        return blocked;
    }

    public boolean isApplicable() {
        return !entries.isEmpty();
    }

    public int getApplicableCount() {
        return entries.size();
    }

    public int getBlockedCount() {
        return blocked.size();
    }

    /** Union of the decisions of every applicable entry, in preview order. */
    public List<String> getRequiredDecisions() {
        Set<String> decisions = new LinkedHashSet<>();
        for (MigrationBatchEntry entry : entries) {
            decisions.addAll(entry.decisions());
        }
        return List.copyOf(decisions);
    }

    public List<String> getBlockedParameterNames() {
        List<String> names = new ArrayList<>();
        for (MigrationBatchEntry entry : blocked) {
            names.add(entry.parameterName());
        }
        return List.copyOf(names);
    }

    /** Concatenated per-parameter diff, with the parameter name as the section header. */
    public String getDiff() {
        if (entries.isEmpty()) {
            return "";
        }
        StringBuilder diff = new StringBuilder();
        for (MigrationBatchEntry entry : entries) {
            diff.append(entry.parameterName())
                    .append(": ")
                    .append(entry.sourceClass())
                    .append(" -> ")
                    .append(entry.targetType())
                    .append('\n')
                    .append(entry.diff())
                    .append("\n\n");
        }
        return diff.toString().stripTrailing();
    }
}
