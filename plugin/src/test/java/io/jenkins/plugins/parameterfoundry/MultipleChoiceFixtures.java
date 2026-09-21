package io.jenkins.plugins.unifiedparameters;

import io.jenkins.plugins.unifiedparameters.definition.UnifiedChoiceParameterDefinition;

/** Creates the unified multiple-selection shape that replaced the legacy multi-choice definition. */
public final class MultipleChoiceFixtures {

    private MultipleChoiceFixtures() {}

    public static UnifiedChoiceParameterDefinition multipleChoice(String name) {
        UnifiedChoiceParameterDefinition definition = new UnifiedChoiceParameterDefinition(name);
        definition.setSelectionMode("multiple");
        return definition;
    }
}
