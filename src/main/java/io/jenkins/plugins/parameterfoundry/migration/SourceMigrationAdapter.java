package io.jenkins.plugins.parameterfoundry.migration;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.ParameterDefinition;
import io.jenkins.plugins.parameterfoundry.provider.ParameterFoundryGlobalConfiguration;

public abstract class SourceMigrationAdapter implements ExtensionPoint {

    public abstract String getSourcePluginId();

    public abstract boolean supports(ParameterDefinition definition);

    public abstract MigrationPlan inspect(ParameterDefinition definition);

    /**
     * Values the source control offers today, when the adapter can read them without executing
     * source code. An empty list means "unknown", not "no values". The replacement simulation uses
     * it to tell a dropped option apart from a build record that the source itself can no longer
     * produce.
     */
    public java.util.List<String> currentSourceValues(ParameterDefinition definition) {
        return java.util.List.of();
    }

    /**
     * Default value of the source control, when the adapter can read it without executing source
     * code. An empty string means "unknown". The simulation uses it to verify the absent-input path
     * of a parameter that has no recorded build yet.
     */
    public String currentSourceDefault(ParameterDefinition definition) {
        return "";
    }

    public boolean isEnabled() {
        return ParameterFoundryGlobalConfiguration.migrationAdaptersFeatureEnabled();
    }

    public static ExtensionList<SourceMigrationAdapter> all() {
        return ExtensionList.lookup(SourceMigrationAdapter.class);
    }
}
