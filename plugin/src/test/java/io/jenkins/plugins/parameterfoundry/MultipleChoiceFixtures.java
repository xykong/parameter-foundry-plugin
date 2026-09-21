package io.jenkins.plugins.parameterfoundry;

import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition;

/** Creates the foundry multiple-selection shape that replaced the legacy multi-choice definition. */
public final class MultipleChoiceFixtures {

    private MultipleChoiceFixtures() {}

    public static FoundryChoiceParameterDefinition multipleChoice(String name) {
        FoundryChoiceParameterDefinition definition = new FoundryChoiceParameterDefinition(name);
        definition.setSelectionMode("multiple");
        return definition;
    }
}
