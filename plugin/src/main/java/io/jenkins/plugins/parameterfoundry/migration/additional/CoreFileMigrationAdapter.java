package io.jenkins.plugins.parameterfoundry.migration.additional;

import hudson.Extension;
import hudson.model.FileParameterDefinition;
import hudson.model.ParameterDefinition;
import io.jenkins.plugins.parameterfoundry.companion.file.FoundryFileParameterDefinition;
import io.jenkins.plugins.parameterfoundry.migration.MigrationClassification;
import io.jenkins.plugins.parameterfoundry.migration.MigrationPlan;
import io.jenkins.plugins.parameterfoundry.migration.MigrationText;
import io.jenkins.plugins.parameterfoundry.migration.SourceMigrationAdapter;
import java.util.List;

/** Preserves Core's relative destination, parameter identity and filename environment contract. */
@Extension
public final class CoreFileMigrationAdapter extends SourceMigrationAdapter {
    @Override
    public String getSourcePluginId() {
        return "jenkins-core";
    }

    @Override
    public boolean supports(ParameterDefinition definition) {
        return definition != null && definition.getClass() == FileParameterDefinition.class;
    }

    @Override
    public MigrationPlan inspect(ParameterDefinition definition) {
        if (!supports(definition)) {
            throw new IllegalArgumentException("Unsupported Core file definition");
        }
        var target = new FoundryFileParameterDefinition(definition.getName());
        target.setWorkspacePath(definition.getName());
        target.setDescription(definition.getDescription());
        return new MigrationPlan(
                MigrationClassification.B,
                MigrationText.of("MigrationReasonCoreFile"),
                target,
                List.of(
                        MigrationText.of("MigrationDecisionFoundryFileLimits"),
                        MigrationText.of("MigrationDecisionCoreFileWorkspace"),
                        MigrationText.of("MigrationDecisionCoreFileHistory")),
                null,
                MigrationText.of("MigrationCaseFoundryFile", definition.getName()));
    }
}
