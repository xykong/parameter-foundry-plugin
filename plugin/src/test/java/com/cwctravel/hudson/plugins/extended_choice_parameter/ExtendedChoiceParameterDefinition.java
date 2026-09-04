package com.cwctravel.hudson.plugins.extended_choice_parameter;

import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest2;

public final class ExtendedChoiceParameterDefinition extends ParameterDefinition {

    public static final String PARAMETER_TYPE_SINGLE_SELECT = "PT_SINGLE_SELECT";
    public static final String PARAMETER_TYPE_RADIO = "PT_RADIO";
    public static final String PARAMETER_TYPE_MULTI_SELECT = "PT_MULTI_SELECT";
    public static final String PARAMETER_TYPE_CHECK_BOX = "PT_CHECKBOX";
    public static final String PARAMETER_TYPE_JSON = "PT_JSON";
    public static final String PARAMETER_TYPE_MULTI_LEVEL_MULTI_SELECT = "PT_MULTI_LEVEL_MULTI_SELECT";
    public static final String PARAMETER_TYPE_MULTI_LEVEL_SINGLE_SELECT = "PT_MULTI_LEVEL_SINGLE_SELECT";

    private final String type;
    private final String value;
    private final String defaultValue;
    private final String multiSelectDelimiter;
    private final String groovyScript;

    public ExtendedChoiceParameterDefinition(
            String name, String type, String value, String defaultValue, String groovyScript) {
        super(name);
        this.type = type;
        this.value = value;
        this.defaultValue = defaultValue;
        this.multiSelectDelimiter = ",";
        this.groovyScript = groovyScript;
    }

    public String getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public String getMultiSelectDelimiter() {
        return multiSelectDelimiter;
    }

    public String getGroovyScript() {
        return groovyScript;
    }

    public String getPropertyFile() {
        return null;
    }

    public String getPropertyKey() {
        return null;
    }

    public String getGroovyScriptFile() {
        return null;
    }

    public String getDefaultPropertyFile() {
        return null;
    }

    public String getDefaultPropertyKey() {
        return null;
    }

    public String getDefaultGroovyScript() {
        return null;
    }

    public String getDefaultGroovyScriptFile() {
        return null;
    }

    public String getJavascript() {
        return null;
    }

    public String getJavascriptFile() {
        return null;
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
