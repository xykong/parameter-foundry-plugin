package io.jenkins.plugins.parameterfoundry.provider;

import hudson.Extension;
import hudson.model.Descriptor;
import io.jenkins.plugins.parameterfoundry.evaluation.ErrorCode;
import io.jenkins.plugins.parameterfoundry.model.Option;
import java.io.Serial;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Declarative dependency mapping: the parameter offers a different option list per value of another
 * parameter. This is the governed replacement for an Active Choices cascade whose script branches on
 * a parameter value, and it executes no code at all — the mapping is configured data that an
 * administrator reviews.
 */
public final class MappingProviderDefinition extends SharedProviderDefinition {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String dependencyParameter;
    private List<Option> defaultOptions = List.of();
    private List<MappingCase> cases = List.of();

    @DataBoundConstructor
    public MappingProviderDefinition(String id, String dependencyParameter) {
        super(id);
        if (dependencyParameter == null || !dependencyParameter.matches("[A-Za-z_][A-Za-z0-9_.-]{0,127}")) {
            throw new IllegalArgumentException("Dependency parameter name is invalid.");
        }
        this.dependencyParameter = dependencyParameter;
    }

    public String getDependencyParameter() {
        return dependencyParameter;
    }

    /** Options used when the dependency has a value no case covers, or no value at all. */
    public List<Option> getDefaultOptions() {
        return defaultOptions == null ? List.of() : defaultOptions;
    }

    @DataBoundSetter
    public void setDefaultOptions(List<Option> defaultOptions) {
        this.defaultOptions = defaultOptions == null ? List.of() : List.copyOf(new ArrayList<>(defaultOptions));
    }

    public List<MappingCase> getCases() {
        return cases == null ? List.of() : cases;
    }

    @DataBoundSetter
    public void setCases(List<MappingCase> cases) {
        List<MappingCase> normalized = cases == null ? List.of() : List.copyOf(new ArrayList<>(cases));
        Set<String> seen = new LinkedHashSet<>();
        for (MappingCase item : normalized) {
            if (!seen.add(item.getValue())) {
                throw new IllegalArgumentException("Duplicate mapping case value: " + item.getValue());
            }
        }
        this.cases = normalized;
    }

    @Override
    public List<Option> load(ProviderLoadContext context) {
        context.checkCancelled();
        String dependencyValue = dependencyValues(context).get(dependencyParameter);
        if (dependencyValue != null) {
            for (MappingCase item : getCases()) {
                if (item.getValue().equals(dependencyValue)) {
                    return item.getOptions();
                }
            }
        }
        if (getDefaultOptions().isEmpty() && getCases().isEmpty()) {
            throw new ProviderException(
                    ErrorCode.PROVIDER_UNAVAILABLE,
                    getId(),
                    "The dependency mapping has no options for " + dependencyParameter + ".");
        }
        return getDefaultOptions();
    }

    private static Map<String, String> dependencyValues(ProviderLoadContext context) {
        if (context == null || context.request() == null) {
            return Map.of();
        }
        return context.request().dependencyValues();
    }

    @Override
    protected String providerFingerprint() {
        StringBuilder encoded = new StringBuilder(dependencyParameter);
        getDefaultOptions().forEach(option -> encoded.append('\u0000').append(option.getValue()));
        for (MappingCase item : getCases()) {
            encoded.append('\u0001').append(item.getValue());
            item.getOptions().forEach(option -> encoded.append('\u0000').append(option.getValue()));
        }
        return Digests.sha256(encoded.toString());
    }

    @Extension
    @Symbol("foundryMappingProvider")
    public static final class DescriptorImpl extends Descriptor<SharedProviderDefinition> {
        @Override
        public String getDisplayName() {
            return "Parameter Foundry Dependency Mapping Provider";
        }
    }
}
