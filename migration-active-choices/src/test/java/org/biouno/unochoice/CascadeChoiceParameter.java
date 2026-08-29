package org.biouno.unochoice;

public final class CascadeChoiceParameter extends ChoiceParameter {

    private final String referencedParameters;

    public CascadeChoiceParameter(String name, String referencedParameters) {
        super(name);
        this.referencedParameters = referencedParameters;
    }

    public String getReferencedParameters() {
        return referencedParameters;
    }
}
