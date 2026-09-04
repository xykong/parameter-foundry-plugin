package io.jenkins.plugins.unifiedparameters.migration;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.ParameterDefinition;
import io.jenkins.plugins.unifiedparameters.provider.UnifiedParametersGlobalConfiguration;

public abstract class SourceMigrationAdapter implements ExtensionPoint {

    public abstract String getSourcePluginId();

    public abstract boolean supports(ParameterDefinition definition);

    public abstract MigrationPlan inspect(ParameterDefinition definition);

    public boolean isEnabled() {
        return UnifiedParametersGlobalConfiguration.migrationAdaptersFeatureEnabled();
    }

    public static ExtensionList<SourceMigrationAdapter> all() {
        return ExtensionList.lookup(SourceMigrationAdapter.class);
    }
}
