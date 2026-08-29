package io.jenkins.plugins.unifiedparameters.provider;

import hudson.model.AbstractDescribableImpl;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.kohsuke.stapler.DataBoundSetter;

public abstract class SharedProviderDefinition extends AbstractDescribableImpl<SharedProviderDefinition>
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String id;
    private String displayName;
    private boolean usableByChildren;
    private int cacheTtlSeconds = 300;
    private String failurePolicy = "fail-closed";
    private int maximumStalenessSeconds;
    private int timeoutSeconds = 5;
    private List<TransformStep> transforms = List.of();

    protected SharedProviderDefinition(String id) {
        this.id = requireId(id);
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName == null || displayName.isBlank() ? id : displayName;
    }

    @DataBoundSetter
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean isUsableByChildren() {
        return usableByChildren;
    }

    @DataBoundSetter
    public void setUsableByChildren(boolean usableByChildren) {
        this.usableByChildren = usableByChildren;
    }

    public int getCacheTtlSeconds() {
        return cacheTtlSeconds;
    }

    @DataBoundSetter
    public void setCacheTtlSeconds(int cacheTtlSeconds) {
        if (cacheTtlSeconds < 0 || cacheTtlSeconds > 86_400) {
            throw new IllegalArgumentException("cacheTtlSeconds must be between 0 and 86400.");
        }
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    public String getFailurePolicy() {
        return failurePolicy;
    }

    @DataBoundSetter
    public void setFailurePolicy(String failurePolicy) {
        if (!java.util.Set.of("fail-closed", "last-known-good", "empty").contains(failurePolicy)) {
            throw new IllegalArgumentException("Unsupported failure policy: " + failurePolicy);
        }
        this.failurePolicy = failurePolicy;
    }

    public int getMaximumStalenessSeconds() {
        return maximumStalenessSeconds;
    }

    @DataBoundSetter
    public void setMaximumStalenessSeconds(int maximumStalenessSeconds) {
        if (maximumStalenessSeconds < 0 || maximumStalenessSeconds > 604_800) {
            throw new IllegalArgumentException("maximumStalenessSeconds must be between 0 and 604800.");
        }
        this.maximumStalenessSeconds = maximumStalenessSeconds;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    @DataBoundSetter
    public void setTimeoutSeconds(int timeoutSeconds) {
        if (timeoutSeconds < 1 || timeoutSeconds > 30) {
            throw new IllegalArgumentException("timeoutSeconds must be between 1 and 30.");
        }
        this.timeoutSeconds = timeoutSeconds;
    }

    public List<TransformStep> getTransforms() {
        return transforms;
    }

    @DataBoundSetter
    public void setTransforms(List<TransformStep> transforms) {
        this.transforms = transforms == null ? List.of() : List.copyOf(new ArrayList<>(transforms));
    }

    public final String configurationDigest() {
        return Digests.sha256(getClass().getName()
                + '\u0000'
                + id
                + '\u0000'
                + cacheTtlSeconds
                + '\u0000'
                + timeoutSeconds
                + '\u0000'
                + failurePolicy
                + '\u0000'
                + maximumStalenessSeconds
                + '\u0000'
                + TransformChain.digest(transforms)
                + '\u0000'
                + providerFingerprint());
    }

    public abstract List<io.jenkins.plugins.unifiedparameters.model.Option> load(ProviderLoadContext context);

    protected abstract String providerFingerprint();

    private static String requireId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) {
            throw new IllegalArgumentException(
                    "Provider ID must be 1-128 characters using letters, digits, dot, underscore, or hyphen.");
        }
        return id;
    }
}
