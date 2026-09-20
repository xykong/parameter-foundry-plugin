package io.jenkins.plugins.unifiedparameters.provider;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Job;
import io.jenkins.plugins.unifiedparameters.evaluation.ErrorCode;
import io.jenkins.plugins.unifiedparameters.model.ChoiceMatrix;
import io.jenkins.plugins.unifiedparameters.model.Option;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Extension
public final class ProviderRuntime {

    private final Map<CacheKey, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Map<CacheKey, FailureEntry> failures = new ConcurrentHashMap<>();
    private final Map<CacheKey, CompletableFuture<CacheEntry>> inFlight = new ConcurrentHashMap<>();
    private final Map<String, ProviderHealth> health = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor executor;
    private final ProviderRateLimiter limiter;
    private volatile long lastEvictionMillis;

    public ProviderRuntime() {
        this(
                new ThreadPoolExecutor(
                        8,
                        48,
                        60,
                        TimeUnit.SECONDS,
                        new LinkedBlockingQueue<>(64),
                        runnable -> {
                            Thread thread = new Thread(runnable, "unified-parameters-provider");
                            thread.setDaemon(true);
                            return thread;
                        },
                        new ThreadPoolExecutor.AbortPolicy()),
                new ProviderRateLimiter());
    }

    ProviderRuntime(ThreadPoolExecutor executor, ProviderRateLimiter limiter) {
        this.executor = executor;
        this.limiter = limiter;
    }

    public static ProviderRuntime get() {
        return ExtensionList.lookupSingleton(ProviderRuntime.class);
    }

    public ProviderResolution resolve(
            Job<?, ?> job,
            ProviderRegistry.LocatedProvider located,
            ProviderOperation operation,
            Map<String, String> dependencies,
            String principal) {
        evictExpiredIfNeeded();
        SharedProviderDefinition provider = located.provider();
        Optional<ConnectionProfile> connectionProfile;
        if (provider instanceof ConnectionProfileConsumer consumer) {
            connectionProfile = ProviderRegistry.findConnectionProfile(job, consumer.getConnectionProfileId())
                    .map(ProviderRegistry.LocatedProfile::profile);
            if (connectionProfile.isEmpty()) {
                throw new ProviderException(
                        ErrorCode.PROVIDER_UNAVAILABLE,
                        provider.getId(),
                        "Connection Profile is not visible to this Job.");
            }
        } else {
            connectionProfile = Optional.empty();
        }
        String providerDigest = provider.runtimeConfigurationDigest(job);
        if (connectionProfile.isPresent()) {
            providerDigest = Digests.sha256(
                    providerDigest + '\u0000' + connectionProfile.orElseThrow().configurationDigest());
        }
        ProviderRequest request = new ProviderRequest(
                job,
                provider.getId(),
                providerDigest,
                located.scope(),
                located.ownerId(),
                dependencies,
                operation,
                Instant.now().plusSeconds(provider.getTimeoutSeconds()),
                principal);
        CacheKey key = new CacheKey(
                provider.getClass().getName(),
                request.providerDigest(),
                request.ownerId(),
                job.getFullName(),
                Digests.dependencies(request.dependencyValues()));
        try (ProviderRateLimiter.Lease ignored = limiter.acquire(request)) {
            CacheEntry cached = cache.get(key);
            Instant now = Instant.now();
            if (cached != null && now.isBefore(cached.expiresAt)) {
                return cached.toResolution(true, false, null);
            }
            FailureEntry previousFailure = failures.get(key);
            if (previousFailure != null && now.isBefore(previousFailure.expiresAt)) {
                return failure(provider, cached, request, previousFailure.exception, now);
            }
            try {
                CacheEntry loaded = loadCoalesced(key, located, request, connectionProfile);
                return loaded.toResolution(false, false, null);
            } catch (ProviderException exception) {
                failures.put(key, new FailureEntry(exception, now.plusSeconds(5)));
                return failure(provider, cached, request, exception, now);
            }
        }
    }

    public Optional<ProviderHealth> health(String ownerId, String providerId) {
        return Optional.ofNullable(health.get(ownerId + "/" + providerId));
    }

    public void clear() {
        cache.clear();
        failures.clear();
        inFlight.clear();
        health.clear();
    }

    private void evictExpiredIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastEvictionMillis < 60_000L) {
            return;
        }
        lastEvictionMillis = now;
        sweepForEviction();
    }

    void sweepForEviction() {
        Instant now = Instant.now();
        cache.entrySet()
                .removeIf(entry -> !now.isBefore(entry.getValue().expiresAt)
                        && Duration.between(entry.getValue().generatedAt, now).getSeconds()
                                > entry.getValue().maximumStalenessSeconds);
        failures.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt));
    }

    int trackedCacheKeys() {
        return cache.size() + failures.size();
    }

    private CacheEntry loadCoalesced(
            CacheKey key,
            ProviderRegistry.LocatedProvider located,
            ProviderRequest request,
            Optional<ConnectionProfile> connectionProfile) {
        CompletableFuture<CacheEntry> future;
        try {
            future = inFlight.computeIfAbsent(key, ignored -> loadAsync(key, located, request, connectionProfile));
        } catch (RejectedExecutionException exception) {
            throw new ProviderException(
                    ErrorCode.RATE_LIMITED, request.providerId(), "Provider work queue is full.", 1);
        }
        try {
            return future.get(
                    Math.max(
                            1,
                            Duration.between(Instant.now(), request.deadline()).toMillis()),
                    TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable(request, "Provider resolution was interrupted.", exception);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw unavailable(request, "Provider resolution timed out.", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof ProviderException providerException) {
                throw providerException;
            }
            throw unavailable(request, "Provider resolution failed.", cause);
        } finally {
            inFlight.remove(key, future);
        }
    }

    private CompletableFuture<CacheEntry> loadAsync(
            CacheKey key,
            ProviderRegistry.LocatedProvider located,
            ProviderRequest request,
            Optional<ConnectionProfile> connectionProfile) {
        long started = System.nanoTime();
        return CompletableFuture.supplyAsync(
                () -> {
                    SharedProviderDefinition provider = located.provider();
                    try {
                        ProviderLoadContext context = new ProviderLoadContext(request, connectionProfile);
                        ChoiceMatrix matrix = provider instanceof MatrixProvider matrixProvider
                                ? matrixProvider.loadMatrix(context)
                                : null;
                        List<Option> loaded = matrix != null ? matrix.selectableOptions() : provider.load(context);
                        if (loaded.size() > 10_000) {
                            throw new ProviderException(
                                    ErrorCode.PROVIDER_UNAVAILABLE,
                                    provider.getId(),
                                    "Provider returned more than 10,000 options.");
                        }
                        List<Option> transformed =
                                TransformChain.apply(loaded, provider.getTransforms(), provider.getId());
                        Instant generated = Instant.now();
                        CacheEntry entry = CacheEntry.create(
                                transformed,
                                matrix,
                                request.providerDigest(),
                                generated,
                                generated.plusSeconds(provider.getCacheTtlSeconds()),
                                provider.getMaximumStalenessSeconds(),
                                located.ownerId() + "/" + provider.getId());
                        cache.put(key, entry);
                        failures.remove(key);
                        recordHealth(located, true, started, transformed.size(), "OK");
                        return entry;
                    } catch (ProviderException exception) {
                        recordHealth(located, false, started, 0, exception.getMessage());
                        throw exception;
                    } catch (RuntimeException exception) {
                        recordHealth(located, false, started, 0, "Provider resolution failed.");
                        throw unavailable(request, "Provider resolution failed.", exception);
                    }
                },
                executor);
    }

    private void recordHealth(
            ProviderRegistry.LocatedProvider located, boolean healthy, long started, int optionCount, String message) {
        String key = located.ownerId() + "/" + located.provider().getId();
        health.put(
                key,
                new ProviderHealth(
                        located.provider().getId(),
                        located.ownerId(),
                        healthy,
                        Instant.now(),
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
                        optionCount,
                        message));
    }

    private static ProviderException unavailable(ProviderRequest request, String message, Throwable cause) {
        ProviderException exception =
                new ProviderException(ErrorCode.PROVIDER_UNAVAILABLE, request.providerId(), message);
        exception.initCause(cause);
        return exception;
    }

    private static ProviderResolution failure(
            SharedProviderDefinition provider,
            CacheEntry cached,
            ProviderRequest request,
            ProviderException exception,
            Instant now) {
        if (!provider.getFallbackOptions().isEmpty()) {
            CacheEntry fallback = CacheEntry.create(
                    provider.getFallbackOptions(),
                    null,
                    request.providerDigest(),
                    now,
                    now,
                    0,
                    request.ownerId() + "/" + provider.getId());
            return fallback.toResolution(false, true, exception.getMessage());
        }
        if ("last-known-good".equals(provider.getFailurePolicy())
                && cached != null
                && Duration.between(cached.generatedAt, now).getSeconds() <= provider.getMaximumStalenessSeconds()) {
            return cached.toResolution(true, true, exception.getMessage());
        }
        if ("empty".equals(provider.getFailurePolicy())) {
            CacheEntry empty = CacheEntry.create(
                    List.of(), null, request.providerDigest(), now, now, 0, request.ownerId() + "/" + provider.getId());
            return empty.toResolution(false, true, exception.getMessage());
        }
        throw exception;
    }

    private record CacheKey(
            String providerType, String providerDigest, String ownerId, String jobFullName, String dependencyDigest) {}

    private record CacheEntry(
            List<Option> options,
            ChoiceMatrix matrix,
            String providerDigest,
            String optionSetDigest,
            Instant generatedAt,
            Instant expiresAt,
            int maximumStalenessSeconds,
            String provenance) {

        static CacheEntry create(
                List<Option> options,
                ChoiceMatrix matrix,
                String providerDigest,
                Instant generatedAt,
                Instant expiresAt,
                int maximumStalenessSeconds,
                String provenance) {
            List<Option> normalized = List.copyOf(options);
            return new CacheEntry(
                    normalized,
                    matrix,
                    providerDigest,
                    Digests.options(normalized),
                    generatedAt,
                    expiresAt,
                    maximumStalenessSeconds,
                    provenance);
        }

        ProviderResolution toResolution(boolean cacheHit, boolean fallback, String fallbackReason) {
            return new ProviderResolution(
                    options,
                    matrix,
                    providerDigest,
                    optionSetDigest,
                    generatedAt,
                    expiresAt,
                    cacheHit,
                    fallback,
                    fallbackReason,
                    provenance);
        }
    }

    private record FailureEntry(ProviderException exception, Instant expiresAt) {}
}
