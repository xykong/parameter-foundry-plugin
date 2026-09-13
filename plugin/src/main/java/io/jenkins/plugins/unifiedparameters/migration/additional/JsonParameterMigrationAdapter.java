package io.jenkins.plugins.unifiedparameters.migration.additional;

import hudson.Extension;
import hudson.model.ParameterDefinition;
import io.jenkins.plugins.unifiedparameters.migration.MigrationText;
import java.util.List;

@Extension
public final class JsonParameterMigrationAdapter extends AbstractAdditionalMigrationAdapter {

    @Override
    public String getSourcePluginId() {
        return "json-parameter";
    }

    @Override
    protected String sourceClassName() {
        return "com.github.cyanbaz.jenkins.plugins.jsonparameter.JsonParameterDefinition";
    }

    @Override
    public boolean supports(ParameterDefinition definition) {
        return super.supports(definition)
                || (definition != null
                        && "com.github.cyanbaz.jenkins.plugins.jsonparameter.JsonParameterReferenceDefinition"
                                .equals(definition.getClass().getName()));
    }

    @Override
    protected String reasonKey() {
        return "MigrationReasonJsonParameter";
    }

    @Override
    protected String targetSkeleton(ParameterDefinition definition) {
        return "unifiedForm(\n"
                + "    name: "
                + quote(definition.getName())
                + ",\n"
                + "    fields: [/* TODO typed object fields */],\n"
                + "    output: 'json',\n"
                + "    renderer: 'schema'\n"
                + ")";
    }

    @Override
    protected List<MigrationText> decisions(ParameterDefinition definition) {
        return List.of(
                MigrationText.of("MigrationDecisionJsonParameter1"),
                MigrationText.of("MigrationDecisionJsonParameter2"),
                MigrationText.of("MigrationDecisionJsonParameter3"));
    }
}
