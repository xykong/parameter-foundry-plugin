package io.jenkins.plugins.parameterfoundry.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/** Provider-derived source defaults, kept separately from displayed option values. */
public final class ChoiceSourceMetadata implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final boolean selected;
    private final boolean apiSelected;
    private final boolean renderFailure;
    private final String apiValue;
    private final String selectedApiValue;

    public ChoiceSourceMetadata(boolean selected, String apiValue, String selectedApiValue) {
        this(selected, selected, apiValue, selectedApiValue);
    }

    public ChoiceSourceMetadata(boolean selected, boolean apiSelected, String apiValue, String selectedApiValue) {
        this(selected, apiSelected, false, apiValue, selectedApiValue);
    }

    public ChoiceSourceMetadata(
            boolean selected, boolean apiSelected, boolean renderFailure, String apiValue, String selectedApiValue) {
        this.selected = selected;
        this.apiSelected = apiSelected;
        this.renderFailure = renderFailure;
        this.apiValue = Objects.requireNonNull(apiValue);
        this.selectedApiValue = Objects.requireNonNull(selectedApiValue);
    }

    public boolean selected() {
        return selected;
    }

    public String apiValue() {
        return apiValue;
    }

    public boolean apiSelected() {
        return apiSelected;
    }

    public boolean renderFailure() {
        return renderFailure;
    }

    public String selectedApiValue() {
        return selectedApiValue;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ChoiceSourceMetadata metadata
                && selected == metadata.selected
                && apiSelected == metadata.apiSelected
                && renderFailure == metadata.renderFailure
                && Objects.equals(apiValue, metadata.apiValue)
                && Objects.equals(selectedApiValue, metadata.selectedApiValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(selected, apiSelected, renderFailure, apiValue, selectedApiValue);
    }
}
