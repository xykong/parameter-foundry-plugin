package io.jenkins.plugins.unifiedparameters.migration.additional;

import hudson.Extension;
import hudson.model.ParameterDefinition;
import java.util.List;

@Extension
public final class JsonEditorMigrationAdapter extends AbstractAdditionalMigrationAdapter {

    @Override
    public String getSourcePluginId() {
        return "json-editor-parameter";
    }

    @Override
    protected String sourceClassName() {
        return "io.jenkins.plugins.json_editor_parameter.JsonEditorParameterDefinition";
    }

    @Override
    protected String capabilitySummary() {
        return "JSON Editor maps to a typed Unified Form Schema rather than an arbitrary editor widget.";
    }

    @Override
    protected String targetSkeleton(ParameterDefinition definition) {
        return "unifiedForm(\n"
                + "    name: "
                + quote(definition.getName())
                + ",\n"
                + "    fields: [/* TODO fields derived from approved JSON Schema subset */],\n"
                + "    output: 'map',\n"
                + "    renderer: 'schema'\n"
                + ")";
    }

    @Override
    protected List<String> decisions(ParameterDefinition definition) {
        return List.of(
                "Confirm the JSON Schema subset and reject unsupported executable/editor extensions.",
                "Choose Map versus JSON-string Pipeline output and explicit field-to-environment mappings.",
                "Verify nested object/array, required/type/range, unknown-field, and canonical serialization behavior.");
    }
}
