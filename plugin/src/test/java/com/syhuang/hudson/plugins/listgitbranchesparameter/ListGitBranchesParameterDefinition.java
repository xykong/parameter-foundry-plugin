package com.syhuang.hudson.plugins.listgitbranchesparameter;

import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest2;

public final class ListGitBranchesParameterDefinition extends ParameterDefinition {

    private final String type;
    private final String remoteURL;
    private final String credentialsId;
    private String branchFilter = "";
    private String tagFilter = "";
    private String sortMode = "";
    private String defaultValue = "";
    private String selectedValue = "";
    private boolean quickFilterEnabled;
    private int listSize = 10;

    public ListGitBranchesParameterDefinition(String name, String type) {
        this(name, type, null, null);
    }

    public ListGitBranchesParameterDefinition(String name, String type, String remoteURL, String credentialsId) {
        super(name);
        this.type = type;
        this.remoteURL = remoteURL;
        this.credentialsId = credentialsId;
    }

    public String getType() {
        return type;
    }

    public String getRemoteURL() {
        return remoteURL;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public String getBranchFilter() {
        return branchFilter;
    }

    public void setBranchFilter(String branchFilter) {
        this.branchFilter = branchFilter;
    }

    public String getTagFilter() {
        return tagFilter;
    }

    public void setTagFilter(String tagFilter) {
        this.tagFilter = tagFilter;
    }

    public String getSortMode() {
        return sortMode;
    }

    public void setSortMode(String sortMode) {
        this.sortMode = sortMode;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getSelectedValue() {
        return selectedValue;
    }

    public void setSelectedValue(String selectedValue) {
        this.selectedValue = selectedValue;
    }

    public boolean getQuickFilterEnabled() {
        return quickFilterEnabled;
    }

    public void setQuickFilterEnabled(boolean quickFilterEnabled) {
        this.quickFilterEnabled = quickFilterEnabled;
    }

    public int getListSize() {
        return listSize;
    }

    public void setListSize(int listSize) {
        this.listSize = listSize;
    }

    @Override
    public ParameterValue createValue(StaplerRequest2 request) {
        return null;
    }

    @Override
    public ParameterValue createValue(StaplerRequest2 request, JSONObject submitted) {
        return null;
    }
}
