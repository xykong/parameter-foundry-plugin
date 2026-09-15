package example.partial;

import hudson.Extension;
import hudson.model.ParameterDefinition;
import io.jenkins.plugins.unifiedparameters.definition.UnifiedChoiceParameterDefinition;
import io.jenkins.plugins.unifiedparameters.migration.MigrationClassification;
import io.jenkins.plugins.unifiedparameters.migration.MigrationPlan;
import io.jenkins.plugins.unifiedparameters.migration.MigrationText;
import io.jenkins.plugins.unifiedparameters.migration.SourceMigrationAdapter;
import io.jenkins.plugins.unifiedparameters.model.Option;
import java.util.List;

/** Test adapter that loses an option the source still offers. */
@Extension
public class PartialOptionMigrationAdapter extends SourceMigrationAdapter {

    @Override
    public String getSourcePluginId() {
        return "partial-option-stub";
    }

    @Override
    public boolean supports(ParameterDefinition definition) {
        return definition instanceof PartialOptionParameterDefinition;
    }

    @Override
    public MigrationPlan inspect(ParameterDefinition definition) {
        UnifiedChoiceParameterDefinition target = new UnifiedChoiceParameterDefinition(definition.getName());
        target.setChoices(List.of(new Option("alpha"), new Option("beta")));
        return new MigrationPlan(
                MigrationClassification.A, MigrationText.of("MigrationPreviewSkeletonSummary"), target, List.of());
    }

    @Override
    public List<String> currentSourceValues(ParameterDefinition definition) {
        return List.of("alpha", "beta", "gamma");
    }
}
