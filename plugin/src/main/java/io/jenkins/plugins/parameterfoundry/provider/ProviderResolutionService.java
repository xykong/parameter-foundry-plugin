package io.jenkins.plugins.parameterfoundry.provider;

import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import io.jenkins.plugins.parameterfoundry.definition.AbstractFoundryParameterDefinition;
import io.jenkins.plugins.parameterfoundry.evaluation.ErrorCode;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import jenkins.model.Jenkins;

public final class ProviderResolutionService {

    private ProviderResolutionService() {}

    public static ProviderResolution resolve(
            Job<?, ?> job,
            AbstractFoundryParameterDefinition definition,
            ProviderOperation operation,
            Map<String, String> dependencies) {
        if (!definition.isProviderBacked()) {
            Instant generated = Instant.now();
            return new ProviderResolution(
                    definition.getSelectableOptions(),
                    definition.getTable(),
                    Digests.sha256("inline\u0000" + definition.getName()),
                    Digests.options(definition.getSelectableOptions()),
                    generated,
                    Instant.MAX,
                    true,
                    false,
                    null,
                    "job/" + job.getFullName() + "/inline/" + definition.getName());
        }
        ProviderRegistry.LocatedProvider located = requireProvider(job, definition);
        ProviderResolution resolution = ProviderRuntime.get()
                .resolve(
                        job,
                        located,
                        operation,
                        dependencies,
                        Jenkins.getAuthentication2().getName());
        if (definition.isRequired() && resolution.options().isEmpty()) {
            throw new ProviderException(
                    ErrorCode.PROVIDER_UNAVAILABLE,
                    definition.getProviderId(),
                    "Required parameter Provider returned no options.");
        }
        return resolution;
    }

    public static String currentProviderDigest(Job<?, ?> job, AbstractFoundryParameterDefinition definition) {
        if (!definition.isProviderBacked()) {
            return Digests.sha256("inline\u0000" + definition.getName());
        }
        ProviderRegistry.LocatedProvider located = requireProvider(job, definition);
        SharedProviderDefinition provider = located.provider();
        if (provider instanceof ConnectionProfileConsumer consumer) {
            ConnectionProfile profile = ProviderRegistry.findConnectionProfile(job, consumer.getConnectionProfileId())
                    .map(ProviderRegistry.LocatedProfile::profile)
                    .orElseThrow(() -> new ProviderException(
                            ErrorCode.PROVIDER_UNAVAILABLE,
                            provider.getId(),
                            "Connection Profile is not visible to this Job."));
            return Digests.sha256(provider.runtimeConfigurationDigest(job) + '\u0000' + profile.configurationDigest());
        }
        return provider.runtimeConfigurationDigest(job);
    }

    public static Optional<Job<?, ?>> findOwningJob(ParameterDefinition definition) {
        for (Job<?, ?> job : Jenkins.get().getAllItems(Job.class)) {
            ParametersDefinitionProperty property = job.getProperty(ParametersDefinitionProperty.class);
            if (property == null) {
                continue;
            }
            for (ParameterDefinition candidate : property.getParameterDefinitions()) {
                if (candidate == definition) {
                    return Optional.of(job);
                }
            }
        }
        return Optional.empty();
    }

    private static ProviderRegistry.LocatedProvider requireProvider(
            Job<?, ?> job, AbstractFoundryParameterDefinition definition) {
        return ProviderRegistry.findProvider(job, definition.getProviderId())
                .orElseThrow(() -> new ProviderException(
                        ErrorCode.PROVIDER_UNAVAILABLE,
                        definition.getProviderId(),
                        "Provider is not visible to this Job."));
    }
}
