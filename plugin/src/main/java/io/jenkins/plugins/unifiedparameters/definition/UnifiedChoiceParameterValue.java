package io.jenkins.plugins.unifiedparameters.definition;

import hudson.EnvVars;
import hudson.model.ParameterValue;
import hudson.model.Run;
import io.jenkins.plugins.unifiedparameters.snapshot.ProviderSnapshotMetadata;
import java.io.Serial;
import java.util.Objects;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean(defaultVisibility = 2)
public final class UnifiedChoiceParameterValue extends ParameterValue {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String value;
    private final String exportMode;
    private final ProviderSnapshotMetadata providerSnapshot;

    @DataBoundConstructor
    public UnifiedChoiceParameterValue(String name, String value) {
        this(name, value, null, "string", null);
    }

    public UnifiedChoiceParameterValue(String name, String value, String description, String exportMode) {
        this(name, value, description, exportMode, null);
    }

    public UnifiedChoiceParameterValue(
            String name,
            String value,
            String description,
            String exportMode,
            ProviderSnapshotMetadata providerSnapshot) {
        super(name, description);
        this.value = value;
        this.exportMode = exportMode == null ? "string" : exportMode;
        this.providerSnapshot = providerSnapshot;
    }

    @Override
    @Exported(visibility = 4)
    public String getValue() {
        return value;
    }

    public String getExportMode() {
        return exportMode;
    }

    @Exported
    public ProviderSnapshotMetadata getProviderSnapshot() {
        return providerSnapshot;
    }

    @Override
    public void buildEnvironment(Run<?, ?> build, EnvVars environment) {
        if (!"none".equals(exportMode) && value != null) {
            environment.put(getName(), value);
        }
    }

    @Override
    public String getShortDescription() {
        return getName() + "=" + (value == null ? "" : value);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof UnifiedChoiceParameterValue valueObject)) {
            return false;
        }
        return Objects.equals(getName(), valueObject.getName()) && Objects.equals(value, valueObject.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getName(), value);
    }
}
