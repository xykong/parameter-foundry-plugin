package io.jenkins.plugins.unifiedparameters.api;

import io.jenkins.plugins.unifiedparameters.migration.MigrationRecord;
import java.util.List;

/** The applied records of one atomic batch, grouped for the Job's migration page. */
public final class MigrationBatchView {

    private final String batchId;
    private final List<MigrationRecord> records;

    MigrationBatchView(String batchId, List<MigrationRecord> records) {
        this.batchId = batchId;
        this.records = List.copyOf(records);
    }

    public String getBatchId() {
        return batchId;
    }

    public List<MigrationRecord> getRecords() {
        return records;
    }

    public int getSize() {
        return records.size();
    }

    /** {@code size} alone collides with a JEXL built-in inside Jelly expressions. */
    public int getRecordCount() {
        return records.size();
    }

    public String getCreatedAt() {
        return records.isEmpty() ? null : records.get(0).getCreatedAt();
    }

    public String getActor() {
        return records.isEmpty() ? null : records.get(0).getActor();
    }

    public String getStatus() {
        for (MigrationRecord record : records) {
            if (!"APPLIED".equals(record.getStatus())) {
                return record.getStatus();
            }
        }
        return "APPLIED";
    }

    public boolean isApplied() {
        return "APPLIED".equals(getStatus());
    }
}
