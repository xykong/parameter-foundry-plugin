package io.jenkins.plugins.unifiedparameters.migration.additional;

import hudson.Extension;
import hudson.model.ParameterDefinition;
import io.jenkins.plugins.unifiedparameters.migration.MigrationText;
import java.util.List;

@Extension
public final class CustomCheckboxMigrationAdapter extends AbstractAdditionalMigrationAdapter {

    @Override
    public String getSourcePluginId() {
        return "custom-checkbox-parameter";
    }

    @Override
    protected String sourceClassName() {
        return "com.bluersw.CheckboxParameterDefinition";
    }

    @Override
    protected String reasonKey() {
        return "MigrationReasonCustomCheckbox";
    }

    @Override
    protected String targetSkeleton(ParameterDefinition definition) {
        return "unifiedChoice(\n" + "    selectionMode: 'multiple',\n"
                + "    name: "
                + quote(definition.getName())
                + ",\n"
                + "    choices: [/* TODO source choices */],\n"
                + "    renderer: 'checkbox',\n"
                + "    standardApiInputMode: 'repeated'\n"
                + ")";
    }

    @Override
    protected List<MigrationText> decisions(ParameterDefinition definition) {
        return List.of(
                MigrationText.of("MigrationDecisionCustomCheckbox1"),
                MigrationText.of("MigrationDecisionCustomCheckbox2"),
                MigrationText.of("MigrationDecisionCustomCheckbox3"));
    }
}
