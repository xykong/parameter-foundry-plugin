package io.jenkins.plugins.parameterfoundry.migration.additional;

import hudson.Extension;
import hudson.model.ParameterDefinition;
import io.jenkins.plugins.parameterfoundry.companion.file.FoundryBase64FileParameterDefinition;
import io.jenkins.plugins.parameterfoundry.companion.file.FoundryStashedFileParameterDefinition;
import io.jenkins.plugins.parameterfoundry.migration.MigrationClassification;
import io.jenkins.plugins.parameterfoundry.migration.MigrationPlan;
import io.jenkins.plugins.parameterfoundry.migration.MigrationText;
import io.jenkins.plugins.parameterfoundry.migration.SourceMigrationAdapter;
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
                        || "io.jenkins.plugins.file_parameters.Base64FileParameterDefinition"
                                .equals(definition.getClass().getName()));
    }

    @Override
    public MigrationPlan inspect(ParameterDefinition definition) {
        if (!supports(definition)) {
            throw new IllegalArgumentException("Unsupported source definition");
        }
        ParameterDefinition target = SOURCE_CLASS.equals(definition.getClass().getName())
                ? new FoundryStashedFileParameterDefinition(definition.getName())
                : new FoundryBase64FileParameterDefinition(definition.getName());
        target.setDescription(definition.getDescription());
        return new MigrationPlan(
                MigrationClassification.B,
                MigrationText.of("MigrationReasonFoundryFile"),
                target,
                List.of(
                        MigrationText.of("MigrationDecisionFoundryFileLimits"),
                        MigrationText.of("MigrationDecisionFoundryFileWrapper"),
                        MigrationText.of("MigrationDecisionFoundryFileHistory")),
                null,
                verificationFixture(definition));
    }

    private static MigrationText verificationFixture(ParameterDefinition definition) {
        return MigrationText.of("MigrationCaseStashedFile", definition.getName());
    }
}
