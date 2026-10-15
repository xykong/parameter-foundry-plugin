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
    private String fingerprint;
    private String matchUri;

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

    /** An administrator-supplied compatibility key, never learned from a normal resolution. */
    public String getFingerprint() {
        return fingerprint;
    }

    @DataBoundSetter
    public void setFingerprint(String fingerprint) {
        if (fingerprint != null
                && !fingerprint.isEmpty()
                && (!"TLS_REJECTED".equals(kind) || !fingerprint.matches("[0-9a-f]{64}"))) {
            throw new IllegalArgumentException(
                    "Only TLS_REJECTED accepts a 64-character lowercase hexadecimal fingerprint.");
        }
        this.fingerprint = fingerprint == null || fingerprint.isEmpty() ? null : fingerprint;
    }

    public String getMatchUri() {
        return matchUri;
    }

    @DataBoundSetter
    public void setMatchUri(String matchUri) {
        if (matchUri != null && !matchUri.isEmpty()) RawRedirectTargets.checkedUri(matchUri);
        this.matchUri = matchUri == null || matchUri.isEmpty() ? null : matchUri;
    }

    Declaration snapshot() {
        return new Declaration(kind, status, value, fingerprint, matchUri);
    }

    /** Frozen before digest computation; no mutable configuration row is read during a request. */
    record Declaration(String kind, int status, String value, String fingerprint, String matchUri) {
        void validate() {
            RawScalarRestTransport.FailureKind.valueOf(kind);
            Objects.requireNonNull(value);
            if (matchUri != null) RawRedirectTargets.checkedUri(matchUri);
            if (status != 0 && (!"HTTP_STATUS".equals(kind) || status < 100 || status > 599)) {
                throw new IllegalArgumentException();
            }
            if ("TLS_REJECTED".equals(kind)) {
                if (status != 0
                        || fingerprint == null
                        || !fingerprint.matches("[0-9a-f]{64}")
                        || value.isEmpty()
                        || value.contains("{")
                        || value.contains("}")) {
                    throw new IllegalArgumentException();
                }
            } else if ("REDIRECT_LIMIT".equals(kind)
                    && (value.isEmpty() || value.contains("{") || value.contains("}"))) {
                throw new IllegalArgumentException();
            } else if (fingerprint != null) {
                throw new IllegalArgumentException();
            }
        }

        String key() {
            return kind + ":" + status + ":" + fingerprint + ":" + matchUri;
        }

        boolean matches(RawScalarRestTransport.Failure failure) {
            return kind.equals(failure.kind().name())
                    && (matchUri == null
                            || failure.targetUri() != null
                                    && matchUri.equals(failure.targetUri().toASCIIString()))
                    && (status == 0 || status == failure.status())
                    && (!"TLS_REJECTED".equals(kind) || fingerprint.equals(failure.fingerprint()));
        }

        boolean includesUri() {
            return value.contains("{uri}");
        }

        String render(URI uri, int responseStatus) {
            if ("TLS_REJECTED".equals(kind)) return value;
            return value.replace("{status}", String.valueOf(responseStatus)).replace("{uri}", uri.toString());
        }
    }

    boolean matches(RawScalarRestTransport.Failure failure) {
        return snapshot().matches(failure);
    }

    boolean includesUri() {
        return value.contains("{uri}");
    }

    String render(URI uri, int responseStatus) {
        return snapshot().render(uri, responseStatus);
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
