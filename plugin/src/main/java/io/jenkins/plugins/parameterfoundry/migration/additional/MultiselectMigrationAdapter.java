package io.jenkins.plugins.unifiedparameters.migration.additional;

import hudson.Extension;
import hudson.model.ParameterDefinition;
import io.jenkins.plugins.unifiedparameters.migration.MigrationText;
import java.util.List;

@Extension
public final class MultiselectMigrationAdapter extends AbstractAdditionalMigrationAdapter {

    @Override
    public String getSourcePluginId() {
        return "multiselect-parameter";
    }

    @Override
    protected String sourceClassName() {
        return "de.westemeyer.plugins.multiselect.MultiselectParameterDefinition";
    }

    @Override
    protected String reasonKey() {
        return "MigrationReasonMultiselect";
    }

    @Override
    protected String targetSkeleton(ParameterDefinition definition) {
        return "unifiedChoice(\n" + "    selectionMode: 'multiple',\n"
                + "    name: "
                + quote(definition.getName())
                + ",\n"
                + "    choices: [/* TODO source choices */],\n"
                + "    renderer: 'list',\n"
                + "    standardApiInputMode: 'json-array'\n"
                + ")";
    }

    @Override
    protected List<MigrationText> decisions(ParameterDefinition definition) {
        return List.of(
                MigrationText.of("MigrationDecisionMultiselect1"),
                MigrationText.of("MigrationDecisionMultiselect2"),
                MigrationText.of("MigrationDecisionMultiselect3"));
    }
}
