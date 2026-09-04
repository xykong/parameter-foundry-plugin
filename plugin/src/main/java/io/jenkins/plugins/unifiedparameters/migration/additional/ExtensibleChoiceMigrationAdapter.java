package io.jenkins.plugins.unifiedparameters.migration.additional;

import hudson.Extension;
import hudson.model.ParameterDefinition;
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
    protected String capabilitySummary() {
        return "Extensible Choice provider-backed or static choice configuration requires a governed Provider mapping.";
    }

    @Override
    protected String targetSkeleton(ParameterDefinition definition) {
        return "unifiedChoice(\n"
                + "    name: "
                + quote(definition.getName())
                + ",\n"
                + "    providerId: 'TODO-governed-provider',\n"
                + "    renderer: 'searchable'\n"
                + ")";
    }

    @Override
    protected List<String> decisions(ParameterDefinition definition) {
        return List.of(
                "Choose static Inline options or a governed Provider; do not copy an unreviewed provider implementation.",
                "Confirm single/multiple cardinality, order, default, empty, disabled, and unavailable-provider behavior.",
                "Bind any external source and credentials through an administrator-owned Connection Profile.");
    }
}
