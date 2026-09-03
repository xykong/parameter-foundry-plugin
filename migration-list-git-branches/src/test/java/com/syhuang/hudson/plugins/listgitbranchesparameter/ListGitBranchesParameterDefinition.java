package com.syhuang.hudson.plugins.listgitbranchesparameter;

import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest2;

public final class ListGitBranchesParameterDefinition extends ParameterDefinition {

    private final String type;
    private final String remoteURL;
    private final String credentialsId;

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

    @Override
    public ParameterValue createValue(StaplerRequest2 request) {
        return null;
    }

    @Override
    public ParameterValue createValue(StaplerRequest2 request, JSONObject submitted) {
        return null;
    }
}
