package io.jenkins.plugins.parameterfoundry.migration.additional;

import hudson.Extension;
import hudson.model.ParameterDefinition;
import io.jenkins.plugins.parameterfoundry.migration.MigrationText;
import java.util.List;

@Extension
public final class ExtensibleChoiceMigrationAdapter extends AbstractAdditionalMigrationAdapter {

    @Override
    public String getSourcePluginId() {
        return "extensible-choice-parameter";
    }

    @Override
    protected String sourceClassName() {
        return "jp.ikedam.jenkins.plugins.extensible_choice_parameter.ExtensibleChoiceParameterDefinition";
    }

    @Override
    protected String reasonKey() {
        return "MigrationReasonExtensibleChoice";
    }

    @Override
    protected String targetSkeleton(ParameterDefinition definition) {
        return "foundryChoice(\n"
                + "    name: "
                + quote(definition.getName())
                + ",\n"
                + "    providerId: 'TODO-governed-provider',\n"
                + "    renderer: 'searchable'\n"
                + ")";
    }

    @Override
    protected List<MigrationText> decisions(ParameterDefinition definition) {
        return List.of(
                MigrationText.of("MigrationDecisionExtensibleChoice1"),
                MigrationText.of("MigrationDecisionExtensibleChoice2"),
                MigrationText.of("MigrationDecisionExtensibleChoice3"));
    }
}
