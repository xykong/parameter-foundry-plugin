package io.jenkins.plugins.unifiedparameters.migration.additional;

import hudson.Extension;
import hudson.model.ParameterDefinition;
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
    protected String capabilitySummary() {
        return "Custom Checkbox maps to the Unified multiple-choice model; source-specific styling is not business semantics.";
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
    protected List<String> decisions(ParameterDefinition definition) {
        return List.of(
                "Confirm whether selection order is business-significant; Unified multi-choice canonicalizes membership.",
                "Map source format/appearance settings to a built-in renderer or reviewed companion renderer only.",
                "Confirm required, empty, duplicate, disabled, and environment-export behavior.");
    }
}
