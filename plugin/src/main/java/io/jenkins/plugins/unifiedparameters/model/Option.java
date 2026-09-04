package io.jenkins.plugins.unifiedparameters.model;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public final class Option extends AbstractDescribableImpl<Option> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String value;
    private String label;
    private String description;
    private String group;
    private boolean disabled;
    private String disabledReason;
    private List<String> tags = List.of();

    @DataBoundConstructor
    public Option(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public String getLabel() {
        return label == null || label.isEmpty() ? value : label;
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

    public String getGroup() {
        return group;
    }

    @DataBoundSetter
    public void setGroup(String group) {
        this.group = group;
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

    public List<String> getTags() {
        return tags == null ? List.of() : tags;
    }

    @DataBoundSetter
    public void setTags(List<String> tags) {
        this.tags = tags == null ? List.of() : List.copyOf(new ArrayList<>(tags));
    }

    public String getTagsText() {
        return String.join(", ", tags);
    }

    @DataBoundSetter
    public void setTagsText(String tagsText) {
        if (tagsText == null || tagsText.isBlank()) {
            tags = List.of();
            return;
        }
        tags = java.util.Arrays.stream(tagsText.split(","))
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .distinct()
                .toList();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Option option)) {
            return false;
        }
        return disabled == option.disabled
                && Objects.equals(value, option.value)
                && Objects.equals(label, option.label)
                && Objects.equals(description, option.description)
                && Objects.equals(group, option.group)
                && Objects.equals(disabledReason, option.disabledReason)
                && Objects.equals(tags, option.tags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, label, description, group, disabled, disabledReason, tags);
    }

    @Extension
    @Symbol("unifiedOption")
    public static final class DescriptorImpl extends Descriptor<Option> {
        @Override
        public String getDisplayName() {
            return "Unified parameter option";
        }
    }
}
