package io.jenkins.plugins.parameterfoundry.migration.additional;

import hudson.model.ParameterDefinition;
import io.jenkins.plugins.parameterfoundry.migration.MigrationClassification;
import io.jenkins.plugins.parameterfoundry.migration.MigrationPlan;
import io.jenkins.plugins.parameterfoundry.migration.MigrationText;
import io.jenkins.plugins.parameterfoundry.migration.SourceMigrationAdapter;
import java.util.List;

abstract class AbstractAdditionalMigrationAdapter extends SourceMigrationAdapter {

    protected abstract String sourceClassName();

    protected abstract String targetSkeleton(ParameterDefinition definition);

    protected abstract List<MigrationText> decisions(ParameterDefinition definition);

    protected abstract String reasonKey();

    @Override
    public boolean supports(ParameterDefinition definition) {
        return definition != null
                && sourceClassName().equals(definition.getClass().getName());
    }

    @Override
    public MigrationPlan inspect(ParameterDefinition definition) {
        if (!supports(definition)) {
            throw new IllegalArgumentException("Unsupported source definition");
        }
        return new MigrationPlan(
                MigrationClassification.C,
                MigrationText.of(reasonKey()),
                null,
                decisions(definition),
                targetSkeleton(definition),
                verificationFixture(definition));
    }

    protected MigrationText verificationFixture(ParameterDefinition definition) {
        return MigrationText.of("MigrationCaseAdditional", definition.getName());
    }

    protected static String quote(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }
}
