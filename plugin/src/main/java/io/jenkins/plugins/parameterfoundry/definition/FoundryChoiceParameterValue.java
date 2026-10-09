package io.jenkins.plugins.parameterfoundry.definition;

import hudson.EnvVars;
import hudson.model.ParameterValue;
import hudson.model.Run;
import io.jenkins.plugins.parameterfoundry.snapshot.ProviderSnapshotMetadata;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import net.sf.json.JSONArray;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Canonical value for {@link FoundryChoiceParameterDefinition}. One type covers both
 * cardinalities: {@code selectionMode} states the mode, {@code value} carries the scalar of a
 * single selection, and {@code values} carries the unordered set of a multiple selection.
 */
@ExportedBean(defaultVisibility = 2)
public final class FoundryChoiceParameterValue extends ParameterValue {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String selectionMode;
    private final String value;
    private final List<String> values;
    private final String exportMode;
    private final String delimiter;
    private final List<String> submittedValues;
    private final String exportOrder;
    private final ProviderSnapshotMetadata providerSnapshot;
    private boolean preserveRepeatedSubmissions;
    private String legacyDefaultLiteral;

    public static FoundryChoiceParameterValue legacyDefault(
            String name,
            List<String> canonical,
            List<String> submitted,
            String description,
            String exportMode,
            String delimiter,
            String literal) {
        var result = multiple(name, canonical, submitted, description, exportMode, delimiter, "submitted", null, true);
        result.legacyDefaultLiteral = Objects.requireNonNull(literal);
        return result;
    }

    public String getLegacyDefaultLiteral() {
        return legacyDefaultLiteral;
    }

    @DataBoundConstructor
    public FoundryChoiceParameterValue(String name, Object value) {
        this(name, modeFor(value), value, null, null, null, null, null, null);
    }

    public FoundryChoiceParameterValue(String name, String value, String description, String exportMode) {
        this(name, "single", value, description, exportMode, null, null, null, null);
    }

    public static FoundryChoiceParameterValue single(
            String name,
            String value,
            String description,
            String exportMode,
            ProviderSnapshotMetadata providerSnapshot) {
        return new FoundryChoiceParameterValue(
                name, "single", value, description, exportMode, null, providerSnapshot, null, null);
    }

    public static FoundryChoiceParameterValue multiple(
            String name,
            List<String> values,
            String description,
            String exportMode,
            String delimiter,
            ProviderSnapshotMetadata providerSnapshot) {
        return multiple(name, values, values, description, exportMode, delimiter, "canonical", providerSnapshot);
    }

    /**
     * Canonical value plus the order the caller submitted. The canonical value stays the sorted set;
     * the submitted order is only used when the definition exports the environment variable in the
     * order the source control used.
     */
    public static FoundryChoiceParameterValue multiple(
            String name,
            List<String> values,
            List<String> submittedValues,
            String description,
            String exportMode,
            String delimiter,
            String exportOrder,
            ProviderSnapshotMetadata providerSnapshot) {
        return new FoundryChoiceParameterValue(
                name,
                "multiple",
                values,
                description,
                exportMode,
                delimiter,
                providerSnapshot,
                submittedValues,
                exportOrder);
    }

    public static FoundryChoiceParameterValue multiple(
            String name,
            List<String> values,
            List<String> submittedValues,
            String description,
            String exportMode,
            String delimiter,
            String exportOrder,
            ProviderSnapshotMetadata providerSnapshot,
            boolean preserveRepeatedSubmissions) {
        var result = multiple(
                name, values, submittedValues, description, exportMode, delimiter, exportOrder, providerSnapshot);
        result.preserveRepeatedSubmissions = preserveRepeatedSubmissions;
        return result;
    }

    private FoundryChoiceParameterValue(
            String name,
            String selectionMode,
            Object rawValue,
            String description,
            String exportMode,
            String delimiter,
            ProviderSnapshotMetadata providerSnapshot,
            List<String> submittedValues,
            String exportOrder) {
        super(name, description);
        boolean multiple = "multiple".equals(selectionMode);
        this.selectionMode = multiple ? "multiple" : "single";
        this.value = multiple ? null : scalarFor(rawValue);
        this.values = multiple ? normalized(rawValue) : null;
        this.exportMode = exportMode == null ? (multiple ? "delimited" : "string") : exportMode;
        this.delimiter = delimiter == null || delimiter.isEmpty() ? "," : delimiter;
        this.providerSnapshot = providerSnapshot;
        this.submittedValues = multiple ? ordered(submittedValues) : null;
        this.exportOrder = exportOrder == null || exportOrder.isBlank() ? "canonical" : exportOrder;
    }

    private static List<String> ordered(List<String> values) {
        if (values == null) {
            return List.of();
        }
        List<String> collected = new ArrayList<>();
        for (String value : values) {
            if (value != null) {
                collected.add(value);
            }
        }
        return List.copyOf(collected);
    }

    private static String modeFor(Object value) {
        return value instanceof Collection<?> || value instanceof Object[] ? "multiple" : "single";
    }

    private static String scalarFor(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static List<String> normalized(Object value) {
        List<String> collected = new ArrayList<>();
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item != null) {
                    collected.add(String.valueOf(item));
                }
            }
        } else if (value instanceof Object[] array) {
            for (Object item : array) {
                if (item != null) {
                    collected.add(String.valueOf(item));
                }
            }
        }
        collected.sort(String::compareTo);
        return List.copyOf(collected);
    }

    public String getSelectionMode() {
        return isMultiple() ? "multiple" : "single";
    }

    public boolean isMultiple() {
        return "multiple".equals(selectionMode);
    }

    /**
     * Exported as {@code value}: a {@link String} for single selection and a {@link List} for
     * multiple selection, matching the documented external contract of each mode.
     */
    @Override
    @Exported(visibility = 4)
    public Object getValue() {
        return isMultiple() ? getValues() : value;
    }

    public String getScalarValue() {
        return isMultiple() ? null : value;
    }

    public List<String> getValues() {
        if (isMultiple()) {
            return values == null ? List.of() : values;
        }
        return value == null ? List.of() : List.of(value);
    }

    public String getExportMode() {
        if (exportMode != null && !exportMode.isBlank()) {
            return exportMode;
        }
        return isMultiple() ? "delimited" : "string";
    }

    public String getDelimiter() {
        return delimiter == null || delimiter.isEmpty() ? "," : delimiter;
    }

    /** Order the caller submitted, which the environment export can preserve for migrated Jobs. */
    public List<String> getSubmittedValues() {
        if (!isMultiple()) {
            return getValues();
        }
        if (preserveRepeatedSubmissions
                && submittedValues != null
                && new java.util.HashSet<>(submittedValues).equals(new java.util.HashSet<>(getValues()))) {
            return submittedValues;
        }
        if (submittedValues == null || submittedValues.size() != getValues().size()) {
            return getValues();
        }
        return submittedValues;
    }

    public String getExportOrder() {
        return exportOrder == null || exportOrder.isBlank() ? "canonical" : exportOrder;
    }

    @Exported
    public ProviderSnapshotMetadata getProviderSnapshot() {
        return providerSnapshot;
    }

    @Override
    public void buildEnvironment(Run<?, ?> build, EnvVars environment) {
        String mode = getExportMode();
        if ("none".equals(mode)) {
            return;
        }
        if (legacyDefaultLiteral != null) {
            environment.put(getName(), legacyDefaultLiteral);
            return;
        }
        if (!isMultiple()) {
            if (value != null) {
                environment.put(getName(), value);
            }
            return;
        }
        List<String> exported = "submitted".equals(getExportOrder()) ? getSubmittedValues() : getValues();
        if ("json".equals(mode)) {
            environment.put(getName(), JSONArray.fromObject(exported).toString());
            return;
        }
        environment.put(getName(), String.join(getDelimiter(), exported));
    }

    @Override
    public String getShortDescription() {
        Object current = getValue();
        return getName() + "=" + (current == null ? "" : current);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FoundryChoiceParameterValue valueObject)) {
            return false;
        }
        return Objects.equals(getName(), valueObject.getName())
                && Objects.equals(selectionMode, valueObject.selectionMode)
                && Objects.equals(legacyDefaultLiteral, valueObject.legacyDefaultLiteral)
                && Objects.equals(getValues(), valueObject.getValues());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getName(), selectionMode, getValues(), legacyDefaultLiteral);
    }
}
