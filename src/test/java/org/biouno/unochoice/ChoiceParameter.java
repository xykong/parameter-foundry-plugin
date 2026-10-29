package org.biouno.unochoice;

import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import net.sf.json.JSONObject;
import org.biouno.unochoice.model.GroovyScript;
import org.biouno.unochoice.model.SecureScript;
import org.kohsuke.stapler.StaplerRequest2;

public class ChoiceParameter extends ParameterDefinition {

    private GroovyScript script = new GroovyScript(new SecureScript(""));
    private String choiceType = "PT_SINGLE_SELECT";
    private boolean filterable;

    public ChoiceParameter(String name) {
        super(name);
    }

    /** Mirrors the real element: the script is a model object, not a plain string. */
    public GroovyScript getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = new GroovyScript(new SecureScript(script));
    }

    public String getChoiceType() {
        return choiceType;
    }

    public void setChoiceType(String choiceType) {
        this.choiceType = choiceType;
    }

    public boolean getFilterable() {
        return filterable;
    }

    public void setFilterable(boolean filterable) {
        this.filterable = filterable;
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
