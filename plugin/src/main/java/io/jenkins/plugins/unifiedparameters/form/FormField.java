package io.jenkins.plugins.unifiedparameters.form;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.unifiedparameters.model.Option;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public final class FormField extends AbstractDescribableImpl<FormField> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String name;
    private final String type;
    private String label;
    private String description;
    private boolean required;
    private boolean secret;
    private String defaultValue;
    private int minLength = -1;
    private int maxLength = -1;
    private String pattern;
    private Double minimum;
    private Double maximum;
    private int minItems;
    private int maxItems = -1;
    private String itemType = "string";
    private List<Option> choices = List.of();
    private List<FormField> fields = List.of();
    private String environmentVariable;

    @DataBoundConstructor
    public FormField(String name, String type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getLabel() {
        return label == null || label.isBlank() ? name : label;
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

    public boolean isRequired() {
        return required;
    }

    @DataBoundSetter
    public void setRequired(boolean required) {
        this.required = required;
    }

    public boolean isSecret() {
        return secret;
    }

    @DataBoundSetter
    public void setSecret(boolean secret) {
        this.secret = secret;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    @DataBoundSetter
    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public int getMinLength() {
        return minLength;
    }

    @DataBoundSetter
    public void setMinLength(int minLength) {
        this.minLength = minLength;
    }

    public int getMaxLength() {
        return maxLength;
    }

    @DataBoundSetter
    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
    }

    public String getPattern() {
        return pattern;
    }

    @DataBoundSetter
    public void setPattern(String pattern) {
        this.pattern = pattern == null || pattern.isBlank() ? null : pattern;
    }

    public Double getMinimum() {
        return minimum;
    }

    @DataBoundSetter
    public void setMinimum(Double minimum) {
        this.minimum = minimum;
    }

    public Double getMaximum() {
        return maximum;
    }

    @DataBoundSetter
    public void setMaximum(Double maximum) {
        this.maximum = maximum;
    }

    public int getMinItems() {
        return minItems;
    }

    @DataBoundSetter
    public void setMinItems(int minItems) {
        this.minItems = minItems;
    }

    public int getMaxItems() {
        return maxItems;
    }

    @DataBoundSetter
    public void setMaxItems(int maxItems) {
        this.maxItems = maxItems;
    }

    public String getItemType() {
        return itemType;
    }

    @DataBoundSetter
    public void setItemType(String itemType) {
        this.itemType = itemType;
    }

    public List<Option> getChoices() {
        return choices;
    }

    @DataBoundSetter
    public void setChoices(List<Option> choices) {
        this.choices = choices == null ? List.of() : List.copyOf(new ArrayList<>(choices));
    }

    public List<FormField> getFields() {
        return fields;
    }

    @DataBoundSetter
    public void setFields(List<FormField> fields) {
        this.fields = fields == null ? List.of() : List.copyOf(new ArrayList<>(fields));
    }

    public String getEnvironmentVariable() {
        return environmentVariable;
    }

    @DataBoundSetter
    public void setEnvironmentVariable(String environmentVariable) {
        this.environmentVariable =
                environmentVariable == null || environmentVariable.isBlank() ? null : environmentVariable;
    }

    @Extension
    @Symbol("unifiedFormField")
    public static final class DescriptorImpl extends Descriptor<FormField> {
        public ListBoxModel doFillTypeItems() {
            return new ListBoxModel(
                    new ListBoxModel.Option("String", "string"),
                    new ListBoxModel.Option("Text", "text"),
                    new ListBoxModel.Option("Integer", "integer"),
                    new ListBoxModel.Option("Decimal", "decimal"),
                    new ListBoxModel.Option("Boolean", "boolean"),
                    new ListBoxModel.Option("Choice", "choice"),
                    new ListBoxModel.Option("Multi-choice", "multi-choice"),
                    new ListBoxModel.Option("Date", "date"),
                    new ListBoxModel.Option("Time", "time"),
                    new ListBoxModel.Option("Read-only content", "content"),
                    new ListBoxModel.Option("Object", "object"),
                    new ListBoxModel.Option("Array", "array"));
        }

        @Override
        public String getDisplayName() {
            return "Unified Form Field";
        }
    }
}
