package io.jenkins.plugins.parameterfoundry.migration.additional;

import hudson.Extension;
import hudson.model.ParameterDefinition;
import io.jenkins.plugins.parameterfoundry.migration.MigrationText;
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
    protected String reasonKey() {
        return "MigrationReasonJsonEditor";
    }

    @Override
    protected String targetSkeleton(ParameterDefinition definition) {
        return "foundryForm(\n"
                + "    name: "
                + quote(definition.getName())
                + ",\n"
                + "    fields: [/* TODO fields derived from approved JSON Schema subset */],\n"
                + "    output: 'map',\n"
                + "    renderer: 'schema'\n"
                + ")";
    }

    @Override
    protected List<MigrationText> decisions(ParameterDefinition definition) {
        return List.of(
                MigrationText.of("MigrationDecisionJsonEditor1"),
                MigrationText.of("MigrationDecisionJsonEditor2"),
                MigrationText.of("MigrationDecisionJsonEditor3"));
    }
}
