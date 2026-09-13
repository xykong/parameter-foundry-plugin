package io.jenkins.plugins.unifiedparameters.migration.additional;

import hudson.Extension;
import hudson.model.ParameterDefinition;
import io.jenkins.plugins.unifiedparameters.migration.MigrationText;
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
    protected String reasonKey() {
        return "MigrationReasonEditableChoice";
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
    protected List<MigrationText> decisions(ParameterDefinition definition) {
        return List.of(
                MigrationText.of("MigrationDecisionEditableChoice1"),
                MigrationText.of("MigrationDecisionEditableChoice2"),
                MigrationText.of("MigrationDecisionEditableChoice3"));
    }
}
