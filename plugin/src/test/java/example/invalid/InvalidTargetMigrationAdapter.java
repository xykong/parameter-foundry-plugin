package example.invalid;

import hudson.Extension;
import hudson.model.ParameterDefinition;
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition;
import io.jenkins.plugins.parameterfoundry.migration.MigrationClassification;
import io.jenkins.plugins.parameterfoundry.migration.MigrationPlan;
import io.jenkins.plugins.parameterfoundry.migration.MigrationText;
import io.jenkins.plugins.parameterfoundry.migration.SourceMigrationAdapter;
import java.util.List;

@Extension
public class InvalidTargetMigrationAdapter extends SourceMigrationAdapter {

    @Override
    public String getSourcePluginId() {
        return "invalid-target-stub";
    }

    @Override
    public boolean supports(ParameterDefinition definition) {
        return definition instanceof InvalidTargetParameterDefinition;
    }

    @Override
    public MigrationPlan inspect(ParameterDefinition definition) {
        FoundryChoiceParameterDefinition target = new FoundryChoiceParameterDefinition(definition.getName());
        target.setDefaultMode("explicit");
        target.setDefaultValue("not-an-option");
        return new MigrationPlan(
                MigrationClassification.A, MigrationText.of("MigrationPreviewSkeletonSummary"), target, List.of());
    }
}
