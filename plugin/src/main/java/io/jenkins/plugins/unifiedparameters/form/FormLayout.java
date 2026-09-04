package io.jenkins.plugins.unifiedparameters.form;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.ListBoxModel;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public final class FormLayout extends AbstractDescribableImpl<FormLayout> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String type;
    private String title;
    private int columns = 1;
    private boolean collapsed;
    private List<String> fieldNames = List.of();
    private List<FormLayout> children = List.of();
    private String visibleWhenField;
    private String visibleWhenValue;

    @DataBoundConstructor
    public FormLayout(String id, String type) {
        this.id = id;
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title == null || title.isBlank() ? id : title;
    }

    @DataBoundSetter
    public void setTitle(String title) {
        this.title = title;
    }

    public int getColumns() {
        return columns;
    }

    @DataBoundSetter
    public void setColumns(int columns) {
        this.columns = columns;
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    @DataBoundSetter
    public void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
    }

    public List<String> getFieldNames() {
        return fieldNames == null ? List.of() : fieldNames;
    }

    public String getFieldNamesText() {
        return String.join(",", fieldNames);
    }

    @DataBoundSetter
    public void setFieldNames(List<String> fieldNames) {
        this.fieldNames = fieldNames == null ? List.of() : List.copyOf(new ArrayList<>(fieldNames));
    }

    @DataBoundSetter
    public void setFieldNamesText(String fieldNamesText) {
        this.fieldNames = fieldNamesText == null
                ? List.of()
                : java.util.Arrays.stream(fieldNamesText.split("[\\r\\n,]+"))
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .toList();
    }

    public List<FormLayout> getChildren() {
        return children == null ? List.of() : children;
    }

    @DataBoundSetter
    public void setChildren(List<FormLayout> children) {
        this.children = children == null ? List.of() : List.copyOf(new ArrayList<>(children));
    }

    public String getVisibleWhenField() {
        return visibleWhenField;
    }

    @DataBoundSetter
    public void setVisibleWhenField(String visibleWhenField) {
        this.visibleWhenField = visibleWhenField == null || visibleWhenField.isBlank() ? null : visibleWhenField;
    }

    public String getVisibleWhenValue() {
        return visibleWhenValue;
    }

    @DataBoundSetter
    public void setVisibleWhenValue(String visibleWhenValue) {
        this.visibleWhenValue = visibleWhenValue;
    }

    @Extension
    @Symbol("unifiedFormLayout")
    public static final class DescriptorImpl extends Descriptor<FormLayout> {
        public ListBoxModel doFillTypeItems() {
            return new ListBoxModel(
                    new ListBoxModel.Option("Section", "section"),
                    new ListBoxModel.Option("Grid", "grid"),
                    new ListBoxModel.Option("Tabs", "tabs"),
                    new ListBoxModel.Option("Collapsible group", "collapsible"),
                    new ListBoxModel.Option("Conditional group", "conditional"));
        }

        @Override
        public String getDisplayName() {
            return "Unified Form Layout";
        }
    }
}
