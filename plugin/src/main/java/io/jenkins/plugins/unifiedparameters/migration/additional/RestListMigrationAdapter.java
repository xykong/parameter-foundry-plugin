package io.jenkins.plugins.unifiedparameters.migration.additional;

import hudson.Extension;
import hudson.model.ParameterDefinition;
import io.jenkins.plugins.unifiedparameters.migration.MigrationText;
import java.util.List;

@Extension
public final class RestListMigrationAdapter extends AbstractAdditionalMigrationAdapter {

    @Override
    public String getSourcePluginId() {
        return "rest-list-parameter";
    }

    @Override
    protected String sourceClassName() {
        return "io.jenkins.plugins.restlistparam.RestListParameterDefinition";
    }

    @Override
    protected String reasonKey() {
        return "MigrationReasonRestList";
    }

    @Override
    protected String targetSkeleton(ParameterDefinition definition) {
        return "unifiedChoice(\n"
                + "    name: "
                + quote(definition.getName())
                + ",\n"
                + "    providerId: 'TODO-governed-rest-provider',\n"
                + "    renderer: 'searchable'\n"
                + ")";
    }

    @Override
    protected List<MigrationText> decisions(ParameterDefinition definition) {
        return List.of(
                MigrationText.of("MigrationDecisionRestList1"),
                MigrationText.of("MigrationDecisionRestList2"),
                MigrationText.of("MigrationDecisionRestList3"));
    }
}
