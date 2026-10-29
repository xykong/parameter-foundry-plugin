package io.jenkins.plugins.parameterfoundry.snapshot;

import java.io.Serial;
import java.io.Serializable;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean(defaultVisibility = 2)
public final class ProviderSnapshotMetadata implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String snapshotId;
    private final String providerId;
    private final String providerDigest;
    private final String dependencyDigest;
    private final String optionSetDigest;
    private final long generatedAtMillis;
    private final long expiresAtMillis;
    private final boolean fallback;
    private final String fallbackReason;
    private final String redactedProvenance;

    public ProviderSnapshotMetadata(
            String snapshotId,
            String providerId,
            String providerDigest,
            String dependencyDigest,
            String optionSetDigest,
            long generatedAtMillis,
            long expiresAtMillis,
            boolean fallback,
            String fallbackReason,
            String redactedProvenance) {
        this.snapshotId = snapshotId;
        this.providerId = providerId;
        this.providerDigest = providerDigest;
        this.dependencyDigest = dependencyDigest;
        this.optionSetDigest = optionSetDigest;
        this.generatedAtMillis = generatedAtMillis;
        this.expiresAtMillis = expiresAtMillis;
        this.fallback = fallback;
        this.fallbackReason = fallbackReason;
        this.redactedProvenance = redactedProvenance;
    }

    @Exported
    public String getSnapshotId() {
        return snapshotId;
    }

    @Exported
    public String getProviderId() {
        return providerId;
    }

    @Exported
    public String getProviderDigest() {
        return providerDigest;
    }

    @Exported
    public String getDependencyDigest() {
        return dependencyDigest;
    }

    @Exported
    public String getOptionSetDigest() {
        return optionSetDigest;
    }

    @Exported
    public long getGeneratedAtMillis() {
        return generatedAtMillis;
    }

    @Exported
    public long getExpiresAtMillis() {
        return expiresAtMillis;
    }

    @Exported
    public boolean isFallback() {
        return fallback;
    }

    @Exported
    public String getFallbackReason() {
        return fallbackReason;
    }

    @Exported
    public String getRedactedProvenance() {
        return redactedProvenance;
    }
}
