package io.jenkins.plugins.parameterfoundry.migration;

import hudson.Extension;
import io.jenkins.plugins.parameterfoundry.Messages;
import java.util.ArrayList;
import java.util.List;
import jenkins.model.GlobalConfiguration;

@Extension
public final class MigrationRecordStore extends GlobalConfiguration {

    private List<MigrationRecord> records = List.of();

    public MigrationRecordStore() {
        load();
    }

    public static MigrationRecordStore get() {
        return GlobalConfiguration.all().get(MigrationRecordStore.class);
    }

    public List<MigrationRecord> getRecords() {
        return records;
    }

    synchronized void add(MigrationRecord record) {
        List<MigrationRecord> updated = new ArrayList<>(records);
        updated.add(record);
        records = List.copyOf(updated);
        save();
    }

    synchronized void addAll(List<MigrationRecord> newRecords) {
        List<MigrationRecord> updated = new ArrayList<>(records);
        updated.addAll(newRecords);
        records = List.copyOf(updated);
        save();
    }

    synchronized void update(MigrationRecord record) {
        save();
    }

    public MigrationRecord findRecord(String id) {
        return records.stream()
                .filter(record -> record.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(Messages.MigrationErrorUnknownRecord(id)));
    }

    /** Every record created by one atomic batch, in creation order. */
    public List<MigrationRecord> findBatch(String batchId) {
        if (batchId == null) {
            return List.of();
        }
        return records.stream()
                .filter(record -> batchId.equals(record.getBatchId()))
                .toList();
    }

    @Override
    public String getDisplayName() {
        return "Parameter Foundry Migration Records";
    }
}
