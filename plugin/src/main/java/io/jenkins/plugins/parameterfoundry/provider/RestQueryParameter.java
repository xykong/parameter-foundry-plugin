package io.jenkins.plugins.unifiedparameters.provider;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import java.io.Serial;
import java.io.Serializable;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public final class RestQueryParameter extends AbstractDescribableImpl<RestQueryParameter> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String name;
    private final String value;
    private String dependencyParameter;

    @DataBoundConstructor
    public RestQueryParameter(String name, String value) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Query parameter name is required.");
        }
        this.name = name;
        this.value = value == null ? "" : value;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public String getDependencyParameter() {
        return dependencyParameter;
    }

    @DataBoundSetter
    public void setDependencyParameter(String dependencyParameter) {
        this.dependencyParameter =
                dependencyParameter == null || dependencyParameter.isBlank() ? null : dependencyParameter;
    }

    public String resolveValue(ProviderRequest request) {
        if (dependencyParameter == null) {
            return value;
        }
        String dependencyValue = request.dependencyValues().get(dependencyParameter);
        if (dependencyValue == null) {
            throw new ProviderException(
                    io.jenkins.plugins.unifiedparameters.evaluation.ErrorCode.DEPENDENCY_INVALID,
                    request.providerId(),
                    "REST query dependency is unavailable: " + dependencyParameter);
        }
        return dependencyValue;
    }

    @Extension
    @Symbol("unifiedRestQuery")
    public static final class DescriptorImpl extends Descriptor<RestQueryParameter> {
        @Override
        public String getDisplayName() {
            return "Unified Parameters REST Query";
        }
    }
}
