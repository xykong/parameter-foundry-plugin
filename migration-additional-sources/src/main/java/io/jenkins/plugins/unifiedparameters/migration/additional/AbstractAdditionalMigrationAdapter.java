package io.jenkins.plugins.unifiedparameters.migration.additional;

import hudson.model.ParameterDefinition;
import io.jenkins.plugins.unifiedparameters.migration.MigrationClassification;
import io.jenkins.plugins.unifiedparameters.migration.MigrationPlan;
import io.jenkins.plugins.unifiedparameters.migration.SourceMigrationAdapter;
import java.util.List;

abstract class AbstractAdditionalMigrationAdapter extends SourceMigrationAdapter {

    protected abstract String sourceClassName();

    protected abstract String targetSkeleton(ParameterDefinition definition);

    protected abstract List<String> decisions(ParameterDefinition definition);

    protected abstract String capabilitySummary();

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
                capabilitySummary() + " The adapter only inspects the definition class and never executes source code.",
                null,
                decisions(definition),
                targetSkeleton(definition),
                verificationFixture(definition));
    }

    protected String verificationFixture(ParameterDefinition definition) {
        return "For "
                + definition.getName()
                + ", verify browser, standard REST, Typed API, CLI, Pipeline/upstream, remote/webhook, Rebuild, "
                + "default, empty, invalid, timeout, and source-unavailable behavior.";
    }

    protected static String quote(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }
}
