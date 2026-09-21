package io.jenkins.plugins.parameterfoundry.migration;

import java.util.List;

/** The records created by one atomic batch apply. */
public record MigrationBatchResult(String batchId, String jobFullName, List<MigrationRecord> records) {

    public MigrationBatchResult {
        records = List.copyOf(records);
    }

    public String getBatchId() {
        return batchId;
    }

    public String getJobFullName() {
        return jobFullName;
    }

    public List<MigrationRecord> getRecords() {
        return records;
    }

    public int getRecordCount() {
        return records.size();
    }
}
