package com.syhuang.hudson.plugins.listgitbranchesparameter;

import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest2;

public final class ListGitBranchesParameterDefinition extends ParameterDefinition {

    private final String type;

    public ListGitBranchesParameterDefinition(String name, String type) {
        super(name);
        this.type = type;
    }

    public String getType() {
        return type;
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
