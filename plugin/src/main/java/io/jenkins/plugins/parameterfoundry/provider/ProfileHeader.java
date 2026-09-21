package io.jenkins.plugins.unifiedparameters.provider;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import java.io.Serial;
import java.io.Serializable;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

public final class ProfileHeader extends AbstractDescribableImpl<ProfileHeader> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String name;
    private final String value;

    @DataBoundConstructor
    public ProfileHeader(String name, String value) {
        if (name == null || !name.matches("[A-Za-z0-9!#$%&'*+.^_`|~-]{1,128}")) {
            throw new IllegalArgumentException("Header name is invalid.");
        }
        if (java.util.Set.of("authorization", "cookie", "host", "content-length")
                .contains(name.toLowerCase(java.util.Locale.ROOT))) {
            throw new IllegalArgumentException("Header must be supplied by the governed transport.");
        }
        if (value == null || value.length() > 4096 || value.contains("\r") || value.contains("\n")) {
            throw new IllegalArgumentException("Header value is invalid.");
        }
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    @Extension
    @Symbol("unifiedProfileHeader")
    public static final class DescriptorImpl extends Descriptor<ProfileHeader> {
        @Override
        public String getDisplayName() {
            return "Unified Parameters Profile Header";
        }
    }
}
