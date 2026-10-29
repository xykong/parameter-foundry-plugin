package io.jenkins.plugins.parameterfoundry.migration;

import java.io.Serial;
import java.io.Serializable;

/**
 * The A/B result recorded for a migrated parameter: the source and target runs, the canonical
 * value each build received, and whether the environment and downstream consumption agreed.
 */
public final class MigrationEvidence implements Serializable {

    public static final String PASS = "PASS";
    public static final String FAIL = "FAIL";
    public static final String PENDING = "PENDING";
    public static final String UNVERIFIED = "UNVERIFIED";

    @Serial
    private static final long serialVersionUID = 1L;

    private String status = UNVERIFIED;
    private String summary;
    private String sourceRun;
    private String targetRun;
    private String sourceValue;
    private String targetValue;
    private String environmentDifferences;
    private String recordedAt;
    private String actor;

    public MigrationEvidence() {}

    public static MigrationEvidence of(
            String status,
            String summary,
            String sourceRun,
            String targetRun,
            String sourceValue,
            String targetValue,
            String environmentDifferences,
            String recordedAt,
            String actor) {
        MigrationEvidence evidence = new MigrationEvidence();
        evidence.status = status;
        evidence.summary = summary;
        evidence.sourceRun = sourceRun;
        evidence.targetRun = targetRun;
        evidence.sourceValue = sourceValue;
        evidence.targetValue = targetValue;
        evidence.environmentDifferences = environmentDifferences;
        evidence.recordedAt = recordedAt;
        evidence.actor = actor;
        return evidence;
    }

    public String getStatus() {
        return status == null ? UNVERIFIED : status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getSourceRun() {
        return sourceRun;
    }

    public void setSourceRun(String sourceRun) {
        this.sourceRun = sourceRun;
    }

    public String getTargetRun() {
        return targetRun;
    }

    public void setTargetRun(String targetRun) {
        this.targetRun = targetRun;
    }

    public String getSourceValue() {
        return sourceValue;
    }

    public void setSourceValue(String sourceValue) {
        this.sourceValue = sourceValue;
    }

    public String getTargetValue() {
        return targetValue;
    }

    public void setTargetValue(String targetValue) {
        this.targetValue = targetValue;
    }

    public String getEnvironmentDifferences() {
        return environmentDifferences;
    }

    public void setEnvironmentDifferences(String environmentDifferences) {
        this.environmentDifferences = environmentDifferences;
    }

    public String getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(String recordedAt) {
        this.recordedAt = recordedAt;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public boolean isVerified() {
        return PASS.equals(getStatus());
    }

    @Override
    public String toString() {
        return getStatus() + ": " + summary;
    }
}
