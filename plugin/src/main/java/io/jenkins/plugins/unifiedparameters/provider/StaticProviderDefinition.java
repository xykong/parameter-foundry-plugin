package io.jenkins.plugins.unifiedparameters.provider;

import hudson.Extension;
import hudson.model.Descriptor;
import io.jenkins.plugins.unifiedparameters.model.Option;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public final class StaticProviderDefinition extends SharedProviderDefinition {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<Option> options = List.of();

    @DataBoundConstructor
    public StaticProviderDefinition(String id) {
        super(id);
    }

    public List<Option> getOptions() {
        return options;
    }

    @DataBoundSetter
    public void setOptions(List<Option> options) {
        this.options = options == null ? List.of() : List.copyOf(new ArrayList<>(options));
    }

    @Override
    public List<Option> load(ProviderLoadContext context) {
        context.checkCancelled();
        return options;
    }

    @Override
    protected String providerFingerprint() {
        return Digests.options(options);
    }

    @Extension
    @Symbol("unifiedStaticProvider")
    public static final class DescriptorImpl extends Descriptor<SharedProviderDefinition> {
        @Override
        public String getDisplayName() {
            return "Unified Parameters Static Provider";
        }
    }
}
