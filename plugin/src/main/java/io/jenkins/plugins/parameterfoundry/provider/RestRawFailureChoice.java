package io.jenkins.plugins.parameterfoundry.provider;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import java.io.Serial;
import java.io.Serializable;
import java.net.URI;
import java.util.Objects;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/** An administrator-declared choice for a classified transport outcome, never an exception dump. */
public final class RestRawFailureChoice extends AbstractDescribableImpl<RestRawFailureChoice> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String kind;
    private final String value;
    private int status;

    @DataBoundConstructor
    public RestRawFailureChoice(String kind, String value) {
        RawScalarRestTransport.FailureKind.valueOf(Objects.requireNonNull(kind));
        this.kind = kind;
        this.value = Objects.requireNonNull(value, "A failure choice must be a string.");
    }

    public String getKind() {
        return kind;
    }

    public String getValue() {
        return value;
    }

    public int getStatus() {
        return status;
    }

    @DataBoundSetter
    public void setStatus(int status) {
        if (status != 0 && (!"HTTP_STATUS".equals(kind) || status < 100 || status > 599)) {
            throw new IllegalArgumentException("Only HTTP_STATUS accepts a status, from 100 to 599, or zero for any.");
        }
        this.status = status;
    }

    boolean matches(RawScalarRestTransport.Failure failure) {
        return kind.equals(failure.kind().name()) && (status == 0 || status == failure.status());
    }

    boolean includesUri() {
        return value.contains("{uri}");
    }

    String render(URI uri, int responseStatus) {
        // Replace fixed tokens only. A token in the URI is never expanded a second time.
        return value.replace("{status}", String.valueOf(responseStatus)).replace("{uri}", uri.toString());
    }

    @Extension
    @Symbol("foundryRawFailureChoice")
    public static final class DescriptorImpl extends Descriptor<RestRawFailureChoice> {
        @Override
        public String getDisplayName() {
            return "Classified transport failure choice";
        }
    }
}
