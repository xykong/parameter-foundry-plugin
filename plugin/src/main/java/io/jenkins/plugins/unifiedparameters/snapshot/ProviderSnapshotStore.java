package io.jenkins.plugins.unifiedparameters.snapshot;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Job;
import io.jenkins.plugins.unifiedparameters.definition.AbstractUnifiedParameterDefinition;
import io.jenkins.plugins.unifiedparameters.definition.UnifiedChoiceParameterDefinition;
import io.jenkins.plugins.unifiedparameters.definition.UnifiedMultiChoiceParameterDefinition;
import io.jenkins.plugins.unifiedparameters.evaluation.ErrorCode;
import io.jenkins.plugins.unifiedparameters.evaluation.EvaluationException;
import io.jenkins.plugins.unifiedparameters.model.Option;
import io.jenkins.plugins.unifiedparameters.provider.Digests;
import io.jenkins.plugins.unifiedparameters.provider.ProviderOperation;
import io.jenkins.plugins.unifiedparameters.provider.ProviderResolution;
import io.jenkins.plugins.unifiedparameters.provider.ProviderResolutionService;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Extension
public final class ProviderSnapshotStore {

    private static final int MAX_SNAPSHOTS = 10_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, Snapshot> snapshots = new ConcurrentHashMap<>();

    public static ProviderSnapshotStore get() {
        return ExtensionList.lookupSingleton(ProviderSnapshotStore.class);
    }

    public ResolvedProviderOptions create(
            Job<?, ?> job,
            AbstractUnifiedParameterDefinition definition,
            Map<String, String> dependencies,
            ProviderResolution resolution,
            SnapshotAudience audience) {
        if (!definition.isProviderBacked()) {
            return new ResolvedProviderOptions(resolution.options(), null, null, false);
        }
        evictExpired();
        if (snapshots.size() >= MAX_SNAPSHOTS) {
            throw new EvaluationException(
                    ErrorCode.RATE_LIMITED, definition.getName(), "Provider snapshot capacity is temporarily full.");
        }
        String id = newId();
        Snapshot snapshot = new Snapshot(
                id,
                audience,
                job.getFullName(),
                definition.getName(),
                definitionDigest(definition),
                definition.getProviderId(),
                resolution.providerDigest(),
                Digests.dependencies(dependencies),
                resolution.optionSetDigest(),
                resolution.generatedAt(),
                resolution.expiresAt(),
                resolution.fallback(),
                resolution.fallbackReason(),
                resolution.redactedProvenance(),
                resolution.options());
        snapshots.put(id, snapshot);
        return snapshot.toResolved(false);
    }

    public ResolvedProviderOptions resolveForBuild(
            Job<?, ?> job,
            AbstractUnifiedParameterDefinition definition,
            Map<String, String> dependencies,
            String submittedSnapshotId,
            ProviderOperation operation) {
        if (!definition.isProviderBacked()) {
            ProviderResolution resolution = ProviderResolutionService.resolve(job, definition, operation, dependencies);
            return new ResolvedProviderOptions(resolution.options(), null, null, false);
        }
        if (submittedSnapshotId != null && !submittedSnapshotId.isBlank()) {
            Snapshot snapshot = snapshots.get(submittedSnapshotId);
            if (snapshot == null) {
                throw invalid(definition, "Provider snapshot is unknown or no longer available.");
            }
            requireBinding(snapshot, job, definition, dependencies);
            if (Instant.now().isBefore(snapshot.expiresAt())) {
                return snapshot.toResolved(false);
            }
        }
        ProviderResolution refreshed = ProviderResolutionService.resolve(job, definition, operation, dependencies);
        ResolvedProviderOptions created =
                create(job, definition, dependencies, refreshed, SnapshotAudience.BUILD_VALUE);
        return new ResolvedProviderOptions(created.options(), created.metadata(), created.snapshotId(), true);
    }

    public void clear() {
        snapshots.clear();
    }

    private static void requireBinding(
            Snapshot snapshot,
            Job<?, ?> job,
            AbstractUnifiedParameterDefinition definition,
            Map<String, String> dependencies) {
        if (snapshot.audience() != SnapshotAudience.BUILD_VALUE) {
            throw invalid(definition, "Diagnostic Provider snapshots cannot be used for builds.");
        }
        if (!snapshot.jobFullName().equals(job.getFullName())
                || !snapshot.parameterName().equals(definition.getName())
                || !snapshot.definitionDigest().equals(definitionDigest(definition))
                || !snapshot.providerId().equals(definition.getProviderId())
                || !snapshot.providerDigest().equals(ProviderResolutionService.currentProviderDigest(job, definition))
                || !snapshot.dependencyDigest().equals(Digests.dependencies(dependencies))) {
            throw invalid(definition, "Provider snapshot does not match the current Job, definition, or dependencies.");
        }
    }

    private void evictExpired() {
        Instant now = Instant.now();
        snapshots.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
    }

    private static String definitionDigest(AbstractUnifiedParameterDefinition definition) {
        StringBuilder value = new StringBuilder(definition.getClass().getName())
                .append('\u0000')
                .append(definition.getName())
                .append('\u0000')
                .append(definition.getProviderId())
                .append('\u0000')
                .append(definition.getDependsOn())
                .append('\u0000')
                .append(definition.getRenderer())
                .append('\u0000')
                .append(definition.getDefaultMode())
                .append('\u0000')
                .append(definition.isRequired())
                .append('\u0000')
                .append(definition.getEnvironmentExport());
        if (definition instanceof UnifiedChoiceParameterDefinition single) {
            value.append('\u0000')
                    .append(single.getDefaultValue())
                    .append('\u0000')
                    .append(single.isAllowCustomValue())
                    .append('\u0000')
                    .append(single.getCustomValuePattern())
                    .append('\u0000')
                    .append(single.getCustomValueMaxLength());
        } else if (definition instanceof UnifiedMultiChoiceParameterDefinition multiple) {
            value.append('\u0000')
                    .append(multiple.getDefaultValues())
                    .append('\u0000')
                    .append(multiple.getMinSelections())
                    .append('\u0000')
                    .append(multiple.getMaxSelections())
                    .append('\u0000')
                    .append(multiple.getDuplicatePolicy());
        }
        return Digests.sha256(value.toString());
    }

    private static String newId() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return "up-snapshot-" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static EvaluationException invalid(AbstractUnifiedParameterDefinition definition, String message) {
        return new EvaluationException(ErrorCode.SNAPSHOT_INVALID, definition.getName(), message);
    }

    private record Snapshot(
            String id,
            SnapshotAudience audience,
            String jobFullName,
            String parameterName,
            String definitionDigest,
            String providerId,
            String providerDigest,
            String dependencyDigest,
            String optionSetDigest,
            Instant generatedAt,
            Instant expiresAt,
            boolean fallback,
            String fallbackReason,
            String redactedProvenance,
            List<Option> options) {

        Snapshot {
            options = List.copyOf(options);
        }

        ResolvedProviderOptions toResolved(boolean refreshed) {
            ProviderSnapshotMetadata metadata = new ProviderSnapshotMetadata(
                    id,
                    providerId,
                    providerDigest,
                    dependencyDigest,
                    optionSetDigest,
                    generatedAt.toEpochMilli(),
                    expiresAt.equals(Instant.MAX) ? Long.MAX_VALUE : expiresAt.toEpochMilli(),
                    fallback,
                    fallbackReason,
                    redactedProvenance);
            return new ResolvedProviderOptions(options, metadata, id, refreshed);
        }
    }
}
