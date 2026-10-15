package io.jenkins.plugins.parameterfoundry.provider;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/** A literal response value, without trimming, splitting, or expression evaluation. */
public final class RestRawLiteral extends AbstractDescribableImpl<RestRawLiteral> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String value;

    @DataBoundConstructor
    public RestRawLiteral(String value) {
        this.value = Objects.requireNonNull(value, "A raw literal must be a string.");
    }

    public String getValue() {
        return value;
    }

    @Extension
    @Symbol("foundryRawLiteral")
    public static final class DescriptorImpl extends Descriptor<RestRawLiteral> {
        @Override
        public String getDisplayName() {
            return "Literal response value";
        }
    }
}
