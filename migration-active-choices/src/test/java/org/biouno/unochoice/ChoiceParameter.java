package org.biouno.unochoice;

import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest2;

public class ChoiceParameter extends ParameterDefinition {

    public ChoiceParameter(String name) {
        super(name);
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
