package io.jenkins.plugins.unifiedparameters.migration.additional;

import hudson.Extension;
import hudson.model.ParameterDefinition;
import java.util.List;

@Extension
public final class EditableChoiceMigrationAdapter extends AbstractAdditionalMigrationAdapter {

    @Override
    public String getSourcePluginId() {
        return "editable-choice";
    }

    @Override
    protected String sourceClassName() {
        return "io.jenkins.plugins.editable_choice.EditableChoiceParameterDefinition";
    }

    @Override
    protected String capabilitySummary() {
        return "Editable Choice maps to a governed choice plus explicit custom-value policy.";
    }

    @Override
    protected String targetSkeleton(ParameterDefinition definition) {
        return "unifiedChoice(\n"
                + "    name: "
                + quote(definition.getName())
                + ",\n"
                + "    choices: [/* TODO source choices */],\n"
                + "    allowCustomValue: true,\n"
                + "    customValueMaxLength: 256,\n"
                + "    renderer: 'searchable'\n"
                + ")";
    }

    @Override
    protected List<String> decisions(ParameterDefinition definition) {
        return List.of(
                "Confirm which custom values are legitimate business inputs and define a bounded regex/length policy.",
                "Confirm whether custom values are persisted as canonical values or only used for one build.",
                "Compare duplicate, empty, default, Rebuild, and API handling with the source behavior.");
    }
}
