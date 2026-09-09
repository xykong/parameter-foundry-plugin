package io.jenkins.plugins.unifiedparameters.model;

import java.io.Serial;
import java.io.Serializable;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public final class MatrixCell implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String row;
    private final String column;
    private final String value;
    private String label;
    private String description;
    private boolean disabled;
    private String disabledReason;

    @DataBoundConstructor
    public MatrixCell(String row, String column, String value) {
        this.row = row;
        this.column = column;
        this.value = value;
    }

    public String getRow() {
        return row;
    }

    public String getColumn() {
        return column;
    }

    public String getValue() {
        return value;
    }

    public String getLabel() {
        return label == null || label.isBlank() ? value : label;
    }

    @DataBoundSetter
    public void setLabel(String label) {
        this.label = label;
    }

    public String getDescription() {
        return description;
    }

    @DataBoundSetter
    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isDisabled() {
        return disabled;
    }

    @DataBoundSetter
    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public String getDisabledReason() {
        return disabledReason;
    }

    @DataBoundSetter
    public void setDisabledReason(String disabledReason) {
        this.disabledReason = disabledReason;
    }
}
