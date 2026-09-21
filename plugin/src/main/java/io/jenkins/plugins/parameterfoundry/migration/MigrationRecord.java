package io.jenkins.plugins.unifiedparameters.migration;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;

public final class MigrationRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String sourceJob;
    private final String targetJob;
    private final String parameterName;
    private final String adapterId;
    private final String actor;
    private final String createdAt;
    private final String sourceDigest;
    private final String targetDigest;
    private final String backupFile;
    private final List<String> decisions;
    private final String batchId;
    private String status;
    private MigrationEvidence evidence;

    MigrationRecord(
            String id,
            String sourceJob,
            String targetJob,
            String parameterName,
            String adapterId,
            String actor,
            Instant createdAt,
            String sourceDigest,
            String targetDigest,
            String backupFile,
            List<String> decisions,
            String status) {
        this(
                id,
                sourceJob,
                targetJob,
                parameterName,
                adapterId,
                actor,
                createdAt,
                sourceDigest,
                targetDigest,
                backupFile,
                decisions,
                status,
                null);
    }

    MigrationRecord(
            String id,
            String sourceJob,
            String targetJob,
            String parameterName,
            String adapterId,
            String actor,
            Instant createdAt,
            String sourceDigest,
            String targetDigest,
            String backupFile,
            List<String> decisions,
            String status,
            String batchId) {
        this.id = id;
        this.sourceJob = sourceJob;
        this.targetJob = targetJob;
        this.parameterName = parameterName;
        this.adapterId = adapterId;
        this.actor = actor;
        this.createdAt = createdAt.toString();
        this.sourceDigest = sourceDigest;
        this.targetDigest = targetDigest;
        this.backupFile = backupFile;
        this.decisions = List.copyOf(decisions);
        this.status = status;
        this.batchId = batchId;
    }

    public String getId() {
        return id;
    }

    public String getSourceJob() {
        return sourceJob;
    }

    public String getTargetJob() {
        return targetJob;
    }

    public String getParameterName() {
        return parameterName;
    }

    public String getAdapterId() {
        return adapterId;
    }

    public String getActor() {
        return actor;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getSourceDigest() {
        return sourceDigest;
    }

    public String getTargetDigest() {
        return targetDigest;
    }

    public String getBackupFile() {
        return backupFile;
    }

    public List<String> getDecisions() {
        return decisions;
    }

    public String getStatus() {
        return status;
    }

    /** Id of the atomic batch that created this record, or {@code null} for a single migration. */
    public String getBatchId() {
        return batchId;
    }

    public MigrationEvidence getEvidence() {
        return evidence;
    }

    public String getEvidenceStatus() {
        return evidence == null ? MigrationEvidence.UNVERIFIED : evidence.getStatus();
    }

    public String getEvidenceSummary() {
        return evidence == null ? null : evidence.getSummary();
    }

    void setStatus(String status) {
        this.status = status;
    }

    void setEvidence(MigrationEvidence evidence) {
        this.evidence = evidence;
    }
}
