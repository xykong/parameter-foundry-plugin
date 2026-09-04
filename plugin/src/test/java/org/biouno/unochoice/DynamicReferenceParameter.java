package org.biouno.unochoice;

public final class DynamicReferenceParameter extends ChoiceParameter {

    private final String choiceType;
    private final boolean omitValueField;
    private final String referencedParameters;

    public DynamicReferenceParameter(String name) {
        this(name, "", "ET_FORMATTED_HTML", false);
    }

    public DynamicReferenceParameter(
            String name, String referencedParameters, String choiceType, boolean omitValueField) {
        super(name);
        this.choiceType = choiceType;
        this.omitValueField = omitValueField;
        this.referencedParameters = referencedParameters;
    }

    public String getChoiceType() {
        return choiceType;
    }

    public boolean getOmitValueField() {
        return omitValueField;
    }

    public String getReferencedParameters() {
        return referencedParameters;
    }
}
