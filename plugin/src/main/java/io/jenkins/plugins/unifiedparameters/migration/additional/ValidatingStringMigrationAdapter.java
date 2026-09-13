package io.jenkins.plugins.unifiedparameters.migration.additional;

import hudson.Extension;
import hudson.model.ParameterDefinition;
import io.jenkins.plugins.unifiedparameters.migration.MigrationText;
import java.util.List;

@Extension
public final class ValidatingStringMigrationAdapter extends AbstractAdditionalMigrationAdapter {

    @Override
    public String getSourcePluginId() {
        return "validating-string-parameter";
    }

    @Override
    protected String sourceClassName() {
        return "hudson.plugins.validating_string_parameter.ValidatingStringParameterDefinition";
    }

    @Override
    protected String reasonKey() {
        return "MigrationReasonValidatingString";
    }

    @Override
    protected String targetSkeleton(ParameterDefinition definition) {
        return "unifiedForm(\n"
                + "    name: "
                + quote(definition.getName())
                + ",\n"
                + "    fields: [stringField(/* TODO regex, length, default, required */)],\n"
                + "    renderer: 'schema'\n"
                + ")";
    }

    @Override
    protected List<MigrationText> decisions(ParameterDefinition definition) {
        return List.of(
                MigrationText.of("MigrationDecisionValidatingString1"),
                MigrationText.of("MigrationDecisionValidatingString2"),
                MigrationText.of("MigrationDecisionValidatingString3"));
    }
}
