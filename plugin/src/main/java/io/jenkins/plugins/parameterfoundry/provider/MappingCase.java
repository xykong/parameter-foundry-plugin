package io.jenkins.plugins.parameterfoundry.provider;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import io.jenkins.plugins.parameterfoundry.model.Option;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * One dependency value and the options the parameter offers while that value is selected.
 * This is declarative data: nothing here is executed.
 */
public final class MappingCase extends AbstractDescribableImpl<MappingCase> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String value;
    private List<Option> options = List.of();

    @DataBoundConstructor
    public MappingCase(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Mapping case value is required.");
        }
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public List<Option> getOptions() {
        return options == null ? List.of() : options;
    }

    @DataBoundSetter
    public void setOptions(List<Option> options) {
        this.options = options == null ? List.of() : List.copyOf(new ArrayList<>(options));
    }

    @Extension
    @Symbol("foundryMappingCase")
    public static final class DescriptorImpl extends Descriptor<MappingCase> {
        @Override
        public String getDisplayName() {
            return "Dependency value and its options";
        }
    }
}
