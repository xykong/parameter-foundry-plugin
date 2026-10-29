package io.jenkins.plugins.parameterfoundry.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.FreeStyleProject;
import io.jenkins.plugins.parameterfoundry.companion.git.GitRefProviderDefinition;
import io.jenkins.plugins.parameterfoundry.evaluation.ErrorCode;
import io.jenkins.plugins.parameterfoundry.model.Option;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ProviderRuntimeTest {

    @Test
    void cachesTransformsAndPublishesRedactedHealth(JenkinsRule jenkins) throws Exception {
        FreeStyleProject job = jenkins.createFreeStyleProject("provider-runtime");
        CountingProvider provider = new CountingProvider("releases");
        provider.setOptions(List.of(new Option("web"), new Option("android"), new Option("web")));
        TransformStep distinct = new TransformStep("distinct");
        TransformStep sort = new TransformStep("sort");
        provider.setTransforms(List.of(distinct, sort));
        ProviderRegistry.LocatedProvider located =
                new ProviderRegistry.LocatedProvider(provider, ProviderScope.GLOBAL, "jenkins");
        ProviderRuntime runtime = runtime(new ProviderRateLimiter());

        ProviderResolution first = runtime.resolve(job, located, ProviderOperation.RESOLVE, Map.of(), "builder");
        ProviderResolution second = runtime.resolve(job, located, ProviderOperation.RESOLVE, Map.of(), "builder");

        assertEquals(
                List.of("android", "web"),
                first.options().stream().map(Option::getValue).toList());
        assertFalse(first.cacheHit());
        assertTrue(second.cacheHit());
        assertEquals(1, provider.loads.get());
        ProviderHealth health = runtime.health("jenkins", "releases").orElseThrow();
        assertTrue(health.healthy());
        assertEquals(2, health.optionCount());
        assertFalse(health.message().contains("android"));
    }

    @Test
    void lastKnownGoodAndEmptyPoliciesDoNotHideProvenance(JenkinsRule jenkins) throws Exception {
        FreeStyleProject job = jenkins.createFreeStyleProject("provider-fallback");
        CountingProvider provider = new CountingProvider("fallback");
        provider.setOptions(List.of(new Option("stable")));
        provider.setCacheTtlSeconds(0);
        provider.setFailurePolicy("last-known-good");
        provider.setMaximumStalenessSeconds(60);
        ProviderRegistry.LocatedProvider located =
                new ProviderRegistry.LocatedProvider(provider, ProviderScope.GLOBAL, "jenkins");
        ProviderRuntime runtime = runtime(new ProviderRateLimiter());

        ProviderResolution fresh = runtime.resolve(job, located, ProviderOperation.RESOLVE, Map.of(), "builder");
        provider.fail = true;
        ProviderResolution fallback = runtime.resolve(job, located, ProviderOperation.BUILD, Map.of(), "builder");

        assertFalse(fresh.fallback());
        assertTrue(fallback.fallback());
        assertEquals(
                List.of("stable"),
                fallback.options().stream().map(Option::getValue).toList());
        assertTrue(fallback.fallbackReason().contains("Synthetic provider failure"));
        assertEquals("jenkins/fallback", fallback.redactedProvenance());

        provider.setFailurePolicy("empty");
        provider.setMaximumStalenessSeconds(0);
        ProviderResolution empty =
                runtime.resolve(job, located, ProviderOperation.RESOLVE, Map.of("version", "new"), "builder");
        assertTrue(empty.fallback());
        assertTrue(empty.options().isEmpty());
    }

    @Test
    void declaredFallbackOptionsAreServedWhenTheProviderFails(JenkinsRule jenkins) throws Exception {
        FreeStyleProject job = jenkins.createFreeStyleProject("provider-declared-fallback");
        CountingProvider provider = new CountingProvider("declared-fallback");
        provider.setOptions(List.of(new Option("live")));
        provider.setFallbackOptions(List.of(new Option("release/prod")));
        provider.setCacheTtlSeconds(0);
        ProviderRegistry.LocatedProvider located =
                new ProviderRegistry.LocatedProvider(provider, ProviderScope.GLOBAL, "jenkins");
        ProviderRuntime runtime = runtime(new ProviderRateLimiter());

        ProviderResolution fresh = runtime.resolve(job, located, ProviderOperation.RESOLVE, Map.of(), "builder");
        provider.fail = true;
        ProviderResolution fallback = runtime.resolve(job, located, ProviderOperation.BUILD, Map.of(), "builder");

        assertFalse(fresh.fallback());
        assertEquals(
                List.of("live"), fresh.options().stream().map(Option::getValue).toList());
        assertTrue(fallback.fallback());
        assertEquals(
                List.of("release/prod"),
                fallback.options().stream().map(Option::getValue).toList());
        assertTrue(fallback.fallbackReason().contains("Synthetic provider failure"));
    }

    @Test
    void theConfigurationDigestTracksTheFallbackOptions() {
        CountingProvider without = new CountingProvider("fallback-digest");
        CountingProvider with = new CountingProvider("fallback-digest");
        with.setFallbackOptions(List.of(new Option("release/prod")));

        assertNotEquals(without.configurationDigest(), with.configurationDigest());
    }

    @Test
    void requestBudgetsRejectBeforeAdditionalProviderWork(JenkinsRule jenkins) throws Exception {
        FreeStyleProject job = jenkins.createFreeStyleProject("provider-budget");
        CountingProvider provider = new CountingProvider("budget");
        provider.setOptions(List.of(new Option("one")));
        ProviderRegistry.LocatedProvider located =
                new ProviderRegistry.LocatedProvider(provider, ProviderScope.GLOBAL, "jenkins");
        ProviderRateLimiter limiter = new ProviderRateLimiter(1, 10, 10, 4, Clock.systemUTC());
        ProviderRuntime runtime = runtime(limiter);

        runtime.resolve(job, located, ProviderOperation.RESOLVE, Map.of(), "builder");
        ProviderException exception = assertThrows(
                ProviderException.class,
                () -> runtime.resolve(job, located, ProviderOperation.RESOLVE, Map.of(), "builder"));

        assertEquals(ErrorCode.RATE_LIMITED, exception.getCode());
        assertEquals(60, exception.getRetryAfterSeconds());
        assertEquals(1, provider.loads.get());
    }

    @Test
    void uncachedGitFailuresStillConsumeRequestBudgets(JenkinsRule jenkins) throws Exception {
        FreeStyleProject job = jenkins.createFreeStyleProject("git-failure-budget");
        GitRefProviderDefinition provider = new GitRefProviderDefinition("git-budget", "git-profile");
        provider.setCacheTtlSeconds(0);
        ParameterFoundryGlobalConfiguration configuration = ParameterFoundryGlobalConfiguration.get();
        configuration.setConnectionProfiles(
                List.of(new ConnectionProfile("git-profile", "https://example.invalid/repo.git")));
        configuration.setGitRefsProviderEnabled(false);
        ProviderRegistry.LocatedProvider located =
                new ProviderRegistry.LocatedProvider(provider, ProviderScope.GLOBAL, "jenkins");
        ProviderRuntime runtime =
                runtime(new ProviderRateLimiter(1, 10, 10, 4, Clock.fixed(Instant.EPOCH, java.time.ZoneOffset.UTC)));

        ProviderException first = assertThrows(
                ProviderException.class,
                () -> runtime.resolve(job, located, ProviderOperation.PREVIEW, Map.of(), "builder"));
        assertEquals(ErrorCode.PROVIDER_UNAVAILABLE, first.getCode());
        ProviderHealth health = runtime.health("jenkins", "git-budget").orElseThrow();
        ProviderException second = assertThrows(
                ProviderException.class,
                () -> runtime.resolve(job, located, ProviderOperation.PREVIEW, Map.of(), "builder"));
        assertEquals(ErrorCode.RATE_LIMITED, second.getCode());
        assertEquals(health, runtime.health("jenkins", "git-budget").orElseThrow());
    }

    @Test
    void negativeCachePreventsRepeatedFailingProviderWork(JenkinsRule jenkins) throws Exception {
        FreeStyleProject job = jenkins.createFreeStyleProject("provider-negative-cache");
        CountingProvider provider = new CountingProvider("negative");
        provider.fail = true;
        ProviderRegistry.LocatedProvider located =
                new ProviderRegistry.LocatedProvider(provider, ProviderScope.GLOBAL, "jenkins");
        ProviderRuntime runtime = runtime(new ProviderRateLimiter());

        assertThrows(
                ProviderException.class,
                () -> runtime.resolve(job, located, ProviderOperation.RESOLVE, Map.of(), "builder"));
        assertThrows(
                ProviderException.class,
                () -> runtime.resolve(job, located, ProviderOperation.RESOLVE, Map.of(), "builder"));

        assertEquals(1, provider.loads.get());
    }

    @Test
    void sweepRemovesExpiredCacheAndKeepsFreshEntries(JenkinsRule jenkins) throws Exception {
        ProviderRuntime runtime = runtime(new ProviderRateLimiter());

        FreeStyleProject expiredJob = jenkins.createFreeStyleProject("provider-cache-expired");
        CountingProvider expiredProvider = new CountingProvider("expired");
        expiredProvider.setOptions(List.of(new Option("one")));
        expiredProvider.setCacheTtlSeconds(0);
        ProviderRegistry.LocatedProvider expiredLocated =
                new ProviderRegistry.LocatedProvider(expiredProvider, ProviderScope.GLOBAL, "jenkins");
        runtime.resolve(expiredJob, expiredLocated, ProviderOperation.RESOLVE, Map.of(), "builder");

        FreeStyleProject freshJob = jenkins.createFreeStyleProject("provider-cache-fresh");
        CountingProvider freshProvider = new CountingProvider("fresh");
        freshProvider.setOptions(List.of(new Option("one")));
        ProviderRegistry.LocatedProvider freshLocated =
                new ProviderRegistry.LocatedProvider(freshProvider, ProviderScope.GLOBAL, "jenkins");
        runtime.resolve(freshJob, freshLocated, ProviderOperation.RESOLVE, Map.of(), "builder");
        Thread.sleep(1_100L);
        runtime.sweepForEviction();
        // The expired entry is gone; the fresh entry survives the sweep.
        assertEquals(1, runtime.trackedCacheKeys());
        runtime.resolve(expiredJob, expiredLocated, ProviderOperation.RESOLVE, Map.of(), "builder");
        assertEquals(2, expiredProvider.loads.get());
        ProviderResolution cached =
                runtime.resolve(freshJob, freshLocated, ProviderOperation.RESOLVE, Map.of(), "builder");
        assertTrue(cached.cacheHit());
        assertEquals(1, freshProvider.loads.get());
    }

    @Test
    void sweepRetainsEntriesStillUsableForLastKnownGood(JenkinsRule jenkins) throws Exception {
        ProviderRuntime runtime = runtime(new ProviderRateLimiter());

        FreeStyleProject job = jenkins.createFreeStyleProject("provider-cache-stale-fallback");
        CountingProvider provider = new CountingProvider("stale-fallback");
        provider.setOptions(List.of(new Option("stable")));
        provider.setCacheTtlSeconds(0);
        provider.setFailurePolicy("last-known-good");
        provider.setMaximumStalenessSeconds(60);
        ProviderRegistry.LocatedProvider located =
                new ProviderRegistry.LocatedProvider(provider, ProviderScope.GLOBAL, "jenkins");

        runtime.resolve(job, located, ProviderOperation.RESOLVE, Map.of(), "builder");
        runtime.sweepForEviction();
        assertEquals(1, runtime.trackedCacheKeys());

        provider.fail = true;
        ProviderResolution fallback = runtime.resolve(job, located, ProviderOperation.BUILD, Map.of(), "builder");
        assertTrue(fallback.fallback());
        assertEquals(
                List.of("stable"),
                fallback.options().stream().map(Option::getValue).toList());
    }

    @Test
    void validateAndBuildUseReservedCapacityWhenPreviewCapacityIsFull(JenkinsRule jenkins) throws Exception {
        FreeStyleProject job = jenkins.createFreeStyleProject("provider-reserved");
        ProviderRateLimiter limiter = new ProviderRateLimiter(100, 100, 100, 4, Clock.systemUTC());
        ProviderRequest preview = request(job, ProviderOperation.PREVIEW);
        ProviderRateLimiter.Lease first = limiter.acquire(preview);
        ProviderRateLimiter.Lease second = limiter.acquire(preview);
        ProviderRateLimiter.Lease third = limiter.acquire(preview);
        try {
            assertThrows(ProviderException.class, () -> limiter.acquire(preview));
            try (ProviderRateLimiter.Lease ignored = limiter.acquire(request(job, ProviderOperation.BUILD))) {
                assertTrue(true);
            }
        } finally {
            first.close();
            second.close();
            third.close();
        }
    }

    @Test
    void transformFailureReportsTheFailingStep(JenkinsRule jenkins) throws Exception {
        FreeStyleProject job = jenkins.createFreeStyleProject("provider-transform-error");
        CountingProvider provider = new CountingProvider("transform-error");
        provider.setOptions(List.of(new Option("one")));
        TransformStep invalidReplacement = new TransformStep("map");
        invalidReplacement.setPattern("(.*)");
        invalidReplacement.setReplacement("$2");
        provider.setTransforms(List.of(invalidReplacement));
        ProviderRegistry.LocatedProvider located =
                new ProviderRegistry.LocatedProvider(provider, ProviderScope.GLOBAL, "jenkins");

        ProviderException exception = assertThrows(
                ProviderException.class,
                () -> runtime(new ProviderRateLimiter())
                        .resolve(job, located, ProviderOperation.RESOLVE, Map.of(), "builder"));

        assertTrue(exception.getMessage().contains("Transform step 0"));
    }

    private static ProviderRuntime runtime(ProviderRateLimiter limiter) {
        return new ProviderRuntime(
                new ThreadPoolExecutor(2, 4, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(8), runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setDaemon(true);
                    return thread;
                }),
                limiter);
    }

    private static ProviderRequest request(FreeStyleProject job, ProviderOperation operation) {
        return new ProviderRequest(
                job,
                "capacity",
                "digest",
                ProviderScope.GLOBAL,
                "jenkins",
                Map.of(),
                operation,
                Instant.now().plusSeconds(5),
                "builder");
    }

    private static final class CountingProvider extends SharedProviderDefinition {
        private final AtomicInteger loads = new AtomicInteger();
        private List<Option> options = List.of();
        private boolean fail;

        CountingProvider(String id) {
            super(id);
        }

        void setOptions(List<Option> options) {
            this.options = List.copyOf(options);
        }

        @Override
        public List<Option> load(ProviderLoadContext context) {
            loads.incrementAndGet();
            if (fail) {
                throw new ProviderException(ErrorCode.PROVIDER_UNAVAILABLE, getId(), "Synthetic provider failure.");
            }
            return options;
        }

        @Override
        protected String providerFingerprint() {
            return Digests.options(options);
        }
    }
}
