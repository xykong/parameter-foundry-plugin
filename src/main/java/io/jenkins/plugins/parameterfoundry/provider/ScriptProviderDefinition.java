package io.jenkins.plugins.parameterfoundry.provider;

import hudson.Extension;
import hudson.model.Descriptor;
import io.jenkins.plugins.parameterfoundry.evaluation.ErrorCode;
import io.jenkins.plugins.parameterfoundry.provider.extension.CompanionProviderRegistry;
import io.jenkins.plugins.parameterfoundry.provider.extension.ScriptProviderExtension;
import java.io.Serial;
import java.util.List;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A persisted reference to a reviewed Script Provider engine and governed
 * source.
 *
 * <p>The plugin stores no source text and never evaluates arbitrary Job input.
 * A first-party or external reviewed implementation owns execution,
 * permissions, auditing, and sandbox behavior.</p>
 */
public final class ScriptProviderDefinition extends SharedProviderDefinition {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String engineId;
    private final String sourceReference;

    @DataBoundConstructor
    public ScriptProviderDefinition(String id, String engineId, String sourceReference) {
        super(id);
        this.engineId = requireReference(engineId, "engineId");
        this.sourceReference = requireReference(sourceReference, "sourceReference");
    }

    public String getEngineId() {
        return engineId;
    }

    public String getSourceReference() {
        return sourceReference;
    }

    @Override
    public List<io.jenkins.plugins.parameterfoundry.model.Option> load(ProviderLoadContext context) {
        if (!ParameterFoundryGlobalConfiguration.scriptProviderFeatureEnabled()) {
            throw new ProviderException(
                    ErrorCode.PROVIDER_UNAVAILABLE, getId(), "The sandboxed Script Provider is disabled.");
        }
        ScriptProviderExtension extension = CompanionProviderRegistry.findReviewedScriptEngine(engineId)
                .orElseThrow(() -> new ProviderException(
                        ErrorCode.PROVIDER_UNAVAILABLE, getId(), "Reviewed Script Provider companion is unavailable."));
        List<io.jenkins.plugins.parameterfoundry.model.Option> result = extension.evaluate(context, sourceReference);
        if (result == null) {
            throw new ProviderException(
                    ErrorCode.PROVIDER_UNAVAILABLE, getId(), "Script Provider returned no normalized result.");
        }
        return List.copyOf(result);
    }

    @Override
    protected String providerFingerprint() {
        return engineId + '\u0000' + sourceReference;
    }

    private static String requireReference(String value, String field) {
        if (value == null
                || value.isBlank()
                || value.length() > 512
                || !value.matches("[A-Za-z0-9][A-Za-z0-9._:/@-]{0,511}")) {
            throw new IllegalArgumentException(field
                    + " must be 1-512 characters using letters, digits, dot, underscore, colon, slash, at, or hyphen.");
        }
        return value;
    }

    @Extension
    @Symbol("foundryScriptProvider")
    public static final class DescriptorImpl extends Descriptor<SharedProviderDefinition> {
        @Override
        public String getDisplayName() {
            return "Parameter Foundry Reviewed Script Provider";
        }
    }
}
