package io.jenkins.plugins.unifiedparameters.migration.additional;

import hudson.Extension;
import hudson.model.ParameterDefinition;
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
    protected String capabilitySummary() {
        return "Multiselect maps to the Unified multiple-choice model and a bounded list/chips renderer.";
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
    protected List<String> decisions(ParameterDefinition definition) {
        return List.of(
                "Choose the standard transport contract: repeated fields, JSON array, or a governed delimiter.",
                "Confirm ordering, duplicate, minimum/maximum selection, and empty-value semantics.",
                "Verify Pipeline params, environment export, Rebuild, and remote trigger behavior.");
    }
}
