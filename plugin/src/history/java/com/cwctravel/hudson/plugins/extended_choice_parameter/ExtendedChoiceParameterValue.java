package com.cwctravel.hudson.plugins.extended_choice_parameter;

import hudson.model.StringParameterValue;

/** Passive reader for the characterized legacy string serialization contract. */
public class ExtendedChoiceParameterValue extends StringParameterValue {
    private static final long serialVersionUID = 7993744779892775177L;

    public ExtendedChoiceParameterValue(String name, String value) {
        super(name, value);
    }
}
