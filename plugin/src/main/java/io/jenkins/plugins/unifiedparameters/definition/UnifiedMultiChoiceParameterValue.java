package io.jenkins.plugins.unifiedparameters.definition;

import hudson.EnvVars;
import hudson.model.ParameterValue;
import hudson.model.Run;
import io.jenkins.plugins.unifiedparameters.snapshot.ProviderSnapshotMetadata;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.sf.json.JSONArray;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean(defaultVisibility = 2)
public final class UnifiedMultiChoiceParameterValue extends ParameterValue {

    @Serial
    private static final long serialVersionUID = 1L;

    private final List<String> value;
    private final String exportMode;
    private final String delimiter;
    private final ProviderSnapshotMetadata providerSnapshot;

    @DataBoundConstructor
    public UnifiedMultiChoiceParameterValue(String name, List<String> value) {
        this(name, value, null, "delimited", ",", null);
    }

    public UnifiedMultiChoiceParameterValue(
            String name, List<String> value, String description, String exportMode, String delimiter) {
        this(name, value, description, exportMode, delimiter, null);
    }

    public UnifiedMultiChoiceParameterValue(
            String name,
            List<String> value,
            String description,
            String exportMode,
            String delimiter,
            ProviderSnapshotMetadata providerSnapshot) {
        super(name, description);
        List<String> normalized = value == null ? new ArrayList<>() : new ArrayList<>(value);
        normalized.sort(String::compareTo);
        this.value = List.copyOf(normalized);
        this.exportMode = exportMode == null ? "delimited" : exportMode;
        this.delimiter = delimiter == null ? "," : delimiter;
        this.providerSnapshot = providerSnapshot;
    }

    @Override
    @Exported(visibility = 4)
    public List<String> getValue() {
        return value;
    }

    public String getExportMode() {
        return exportMode;
    }

    public String getDelimiter() {
        return delimiter;
    }

    @Exported
    public ProviderSnapshotMetadata getProviderSnapshot() {
        return providerSnapshot;
    }

    @Override
    public void buildEnvironment(Run<?, ?> build, EnvVars environment) {
        switch (exportMode) {
            case "none":
                return;
            case "json":
                environment.put(getName(), JSONArray.fromObject(value).toString());
                return;
            default:
                environment.put(getName(), String.join(delimiter, value));
        }
    }

    @Override
    public String getShortDescription() {
        return getName() + "=" + value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof UnifiedMultiChoiceParameterValue valueObject)) {
            return false;
        }
        return Objects.equals(getName(), valueObject.getName()) && Objects.equals(value, valueObject.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getName(), value);
    }
}
