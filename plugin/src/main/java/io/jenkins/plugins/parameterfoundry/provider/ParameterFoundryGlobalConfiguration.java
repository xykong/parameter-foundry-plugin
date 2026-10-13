package io.jenkins.plugins.parameterfoundry.provider;

import hudson.Extension;
import hudson.model.Descriptor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest2;

@Extension
@Symbol("parameterFoundry")
public final class ParameterFoundryGlobalConfiguration extends GlobalConfiguration {

    private List<SharedProviderDefinition> providers = List.of();
    private List<ConnectionProfile> connectionProfiles = List.of();
    private boolean migrationAdaptersEnabled = true;
    private boolean scriptProviderEnabled = true;
    private boolean gitRefsProviderEnabled = true;
    private boolean accessibleChoiceGridEnabled = true;
    private boolean legacyGitHistoryEnabled;
    private boolean legacyExtendedChoiceHistoryEnabled;
    private boolean legacyFileHistoryEnabled;
    private long legacyFileHistoryMaxBytes;

    public ParameterFoundryGlobalConfiguration() {
        load();
    }

    public static ParameterFoundryGlobalConfiguration get() {
        return GlobalConfiguration.all().get(ParameterFoundryGlobalConfiguration.class);
    }

    public boolean isLegacyFileHistoryEnabled() {
        return legacyFileHistoryEnabled;
    }

    @DataBoundSetter
    public void setLegacyFileHistoryEnabled(boolean enabled) {
        legacyFileHistoryEnabled = enabled;
        save();
    }

    public long getLegacyFileHistoryMaxBytes() {
        return legacyFileHistoryMaxBytes == 0 ? 100L * 1024 * 1024 : legacyFileHistoryMaxBytes;
    }

    @DataBoundSetter
    public void setLegacyFileHistoryMaxBytes(long maximum) {
        if (maximum < 1) throw new IllegalArgumentException("Historical file byte limit must be positive");
        legacyFileHistoryMaxBytes = maximum;
        save();
    }

    public boolean isLegacyExtendedChoiceHistoryEnabled() {
        return legacyExtendedChoiceHistoryEnabled;
    }

    @DataBoundSetter
    public void setLegacyExtendedChoiceHistoryEnabled(boolean enabled) {
        legacyExtendedChoiceHistoryEnabled = enabled;
        save();
    }

    public boolean isLegacyGitHistoryEnabled() {
        return legacyGitHistoryEnabled;
    }

    @DataBoundSetter
    public void setLegacyGitHistoryEnabled(boolean enabled) {
        legacyGitHistoryEnabled = enabled;
        save();
    }

    public List<SharedProviderDefinition> getProviders() {
        return providers == null ? List.of() : providers;
    }

    @DataBoundSetter
    public void setProviders(List<SharedProviderDefinition> providers) {
        this.providers = providers == null ? List.of() : List.copyOf(new ArrayList<>(providers));
        requireUniqueIds(
                this.providers.stream().map(SharedProviderDefinition::getId).toList(), "Provider");
        save();
    }

    public List<ConnectionProfile> getConnectionProfiles() {
        return connectionProfiles == null ? List.of() : connectionProfiles;
    }

    @DataBoundSetter
    public void setConnectionProfiles(List<ConnectionProfile> connectionProfiles) {
        this.connectionProfiles =
                connectionProfiles == null ? List.of() : List.copyOf(new ArrayList<>(connectionProfiles));
        requireUniqueIds(
                this.connectionProfiles.stream().map(ConnectionProfile::getId).toList(), "Profile");
        save();
    }

    public boolean isMigrationAdaptersEnabled() {
        return migrationAdaptersEnabled;
    }

    @DataBoundSetter
    public void setMigrationAdaptersEnabled(boolean migrationAdaptersEnabled) {
        this.migrationAdaptersEnabled = migrationAdaptersEnabled;
        save();
    }

    public boolean isScriptProviderEnabled() {
        return scriptProviderEnabled;
    }

    @DataBoundSetter
    public void setScriptProviderEnabled(boolean scriptProviderEnabled) {
        this.scriptProviderEnabled = scriptProviderEnabled;
        save();
    }

    public boolean isGitRefsProviderEnabled() {
        return gitRefsProviderEnabled;
    }

    @DataBoundSetter
    public void setGitRefsProviderEnabled(boolean gitRefsProviderEnabled) {
        this.gitRefsProviderEnabled = gitRefsProviderEnabled;
        save();
    }

    public boolean isAccessibleChoiceGridEnabled() {
        return accessibleChoiceGridEnabled;
    }

    @DataBoundSetter
    public void setAccessibleChoiceGridEnabled(boolean accessibleChoiceGridEnabled) {
        this.accessibleChoiceGridEnabled = accessibleChoiceGridEnabled;
        save();
    }

    public static boolean migrationAdaptersFeatureEnabled() {
        ParameterFoundryGlobalConfiguration configuration = current();
        return configuration == null || configuration.isMigrationAdaptersEnabled();
    }

    public static boolean scriptProviderFeatureEnabled() {
        ParameterFoundryGlobalConfiguration configuration = current();
        return configuration == null || configuration.isScriptProviderEnabled();
    }

    public static boolean gitRefsProviderFeatureEnabled() {
        ParameterFoundryGlobalConfiguration configuration = current();
        return configuration == null || configuration.isGitRefsProviderEnabled();
    }

    public static boolean accessibleChoiceGridFeatureEnabled() {
        ParameterFoundryGlobalConfiguration configuration = current();
        return configuration == null || configuration.isAccessibleChoiceGridEnabled();
    }

    private static ParameterFoundryGlobalConfiguration current() {
        return Jenkins.getInstanceOrNull() == null ? null : get();
    }

    @Override
    public boolean configure(StaplerRequest2 request, JSONObject json) throws Descriptor.FormException {
        request.bindJSON(this, json);
        save();
        return true;
    }

    static void requireUniqueIds(List<String> ids, String kind) {
        Set<String> unique = new HashSet<>();
        for (String id : ids) {
            if (!unique.add(id)) {
                throw new IllegalArgumentException("Duplicate " + kind + " ID: " + id);
            }
        }
    }

    @Override
    public String getDisplayName() {
        return "Parameter Foundry Providers";
    }
}
