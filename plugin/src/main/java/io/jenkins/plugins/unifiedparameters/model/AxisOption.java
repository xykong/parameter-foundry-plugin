package io.jenkins.plugins.unifiedparameters.model;

import java.io.Serial;
import java.io.Serializable;
import org.kohsuke.stapler.DataBoundConstructor;

public final class AxisOption implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String value;
    private final String label;

    @DataBoundConstructor
    public AxisOption(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public String getValue() {
        return value;
    }

    public String getLabel() {
        return label == null || label.isBlank() ? value : label;
    }
}
