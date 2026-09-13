package io.jenkins.plugins.unifiedparameters.migration.additional;

import hudson.Extension;
import hudson.model.ParameterDefinition;
import io.jenkins.plugins.unifiedparameters.migration.MigrationClassification;
import io.jenkins.plugins.unifiedparameters.migration.MigrationPlan;
import io.jenkins.plugins.unifiedparameters.migration.MigrationText;
import io.jenkins.plugins.unifiedparameters.migration.SourceMigrationAdapter;
import java.util.List;

@Extension
public final class StashedFileMigrationAdapter extends SourceMigrationAdapter {

    private static final String SOURCE_CLASS = "io.jenkins.plugins.file_parameters.StashedFileParameterDefinition";

    @Override
    public String getSourcePluginId() {
        return "file-parameters";
    }

    @Override
    public boolean supports(ParameterDefinition definition) {
        return definition != null
                && (SOURCE_CLASS.equals(definition.getClass().getName())
                        || definition.getClass().getName().endsWith(".StashedFileParameterDefinition"));
    }

    @Override
    public MigrationPlan inspect(ParameterDefinition definition) {
        if (!supports(definition)) {
            throw new IllegalArgumentException("Unsupported source definition");
        }
        return new MigrationPlan(
                MigrationClassification.D,
                MigrationText.of("MigrationReasonStashedFile"),
                null,
                List.of(
                        MigrationText.of("MigrationDecisionStashedFile1"),
                        MigrationText.of("MigrationDecisionStashedFile2"),
                        MigrationText.of("MigrationDecisionStashedFile3"),
                        MigrationText.of("MigrationDecisionStashedFile4")),
                null,
                verificationFixture(definition));
    }

    private static MigrationText verificationFixture(ParameterDefinition definition) {
        return MigrationText.of("MigrationCaseStashedFile", definition.getName());
    }
}
