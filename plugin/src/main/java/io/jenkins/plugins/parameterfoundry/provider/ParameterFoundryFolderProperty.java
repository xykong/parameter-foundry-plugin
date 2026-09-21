package io.jenkins.plugins.parameterfoundry.provider;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.hudson.plugins.folder.AbstractFolderProperty;
import com.cloudbees.hudson.plugins.folder.AbstractFolderPropertyDescriptor;
import hudson.Extension;
import java.util.ArrayList;
import java.util.List;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public final class ParameterFoundryFolderProperty extends AbstractFolderProperty<AbstractFolder<?>> {

    private List<SharedProviderDefinition> providers = List.of();
    private List<ConnectionProfile> connectionProfiles = List.of();

    @DataBoundConstructor
    public ParameterFoundryFolderProperty() {}

    public List<SharedProviderDefinition> getProviders() {
        return providers == null ? List.of() : providers;
    }

    @DataBoundSetter
    public void setProviders(List<SharedProviderDefinition> providers) {
        this.providers = providers == null ? List.of() : List.copyOf(new ArrayList<>(providers));
        ParameterFoundryGlobalConfiguration.requireUniqueIds(
                this.providers.stream().map(SharedProviderDefinition::getId).toList(), "Provider");
    }

    public List<ConnectionProfile> getConnectionProfiles() {
        return connectionProfiles == null ? List.of() : connectionProfiles;
    }

    @DataBoundSetter
    public void setConnectionProfiles(List<ConnectionProfile> connectionProfiles) {
        this.connectionProfiles =
                connectionProfiles == null ? List.of() : List.copyOf(new ArrayList<>(connectionProfiles));
        ParameterFoundryGlobalConfiguration.requireUniqueIds(
                this.connectionProfiles.stream().map(ConnectionProfile::getId).toList(), "Profile");
    }

    @Extension
    @Symbol("parameterFoundry")
    public static final class DescriptorImpl extends AbstractFolderPropertyDescriptor {
        @Override
        public String getDisplayName() {
            return "Parameter Foundry Providers";
        }
    }
}
