package io.jenkins.plugins.unifiedparameters.migration;

import hudson.Extension;
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

    synchronized void update(MigrationRecord record) {
        save();
    }

    public MigrationRecord findRecord(String id) {
        return records.stream()
                .filter(record -> record.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown migration record: " + id));
    }

    @Override
    public String getDisplayName() {
        return "Unified Parameters Migration Records";
    }
}
