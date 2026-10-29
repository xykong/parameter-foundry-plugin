package io.jenkins.plugins.parameterfoundry.companion.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.FreeStyleProject;
import io.jenkins.plugins.parameterfoundry.evaluation.ErrorCode;
import io.jenkins.plugins.parameterfoundry.model.Option;
import io.jenkins.plugins.parameterfoundry.provider.ConnectionProfile;
import io.jenkins.plugins.parameterfoundry.provider.ParameterFoundryGlobalConfiguration;
import io.jenkins.plugins.parameterfoundry.provider.ProviderException;
import io.jenkins.plugins.parameterfoundry.provider.ProviderOperation;
import io.jenkins.plugins.parameterfoundry.provider.ProviderRegistry;
import io.jenkins.plugins.parameterfoundry.provider.ProviderResolution;
import io.jenkins.plugins.parameterfoundry.provider.ProviderRuntime;
import io.jenkins.plugins.parameterfoundry.provider.ProviderScope;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class GitRefProviderRuntimeTest {
    @Test
    void uncachedGitRetriesImmediatelyAndRestoresHealth(JenkinsRule jenkins) throws Exception {
        AtomicBoolean fail = new AtomicBoolean(true);
        AtomicInteger loads = new AtomicInteger();
        GitRefProviderDefinition provider = provider(fail, loads);
        provider.setCacheTtlSeconds(0);
        FreeStyleProject job = job(jenkins);
        ProviderRuntime runtime = new ProviderRuntime();

        assertThrows(ProviderException.class, () -> resolve(runtime, job, provider));
        assertFalse(runtime.health("jenkins", "git").orElseThrow().healthy());
        fail.set(false);
        ProviderResolution recovered = resolve(runtime, job, provider);
        assertEquals(
                List.of("master"),
                recovered.options().stream().map(Option::getValue).toList());
        assertFalse(recovered.cacheHit());
        assertFalse(recovered.fallback());
        assertEquals(2, loads.get());
        assertTrue(runtime.health("jenkins", "git").orElseThrow().healthy());
    }

    @Test
    void cachedGitRetainsFailureCooldown(JenkinsRule jenkins) throws Exception {
        AtomicBoolean fail = new AtomicBoolean(true);
        AtomicInteger loads = new AtomicInteger();
        GitRefProviderDefinition provider = provider(fail, loads);
        provider.setCacheTtlSeconds(30);
        FreeStyleProject job = job(jenkins);
        ProviderRuntime runtime = new ProviderRuntime();

        assertThrows(ProviderException.class, () -> resolve(runtime, job, provider));
        fail.set(false);
        assertThrows(ProviderException.class, () -> resolve(runtime, job, provider));
        assertEquals(1, loads.get());
    }

    @Test
    void uncachedGitKeepsDeclaredFallbackThenRecovers(JenkinsRule jenkins) throws Exception {
        AtomicBoolean fail = new AtomicBoolean(true);
        AtomicInteger loads = new AtomicInteger();
        GitRefProviderDefinition provider = provider(fail, loads);
        provider.setCacheTtlSeconds(0);
        provider.setFallbackOptions(List.of(new Option("reviewed-fallback")));
        FreeStyleProject job = job(jenkins);
        ProviderRuntime runtime = new ProviderRuntime();

        ProviderResolution fallback = resolve(runtime, job, provider);
        assertTrue(fallback.fallback());
        assertEquals("reviewed-fallback", fallback.options().get(0).getValue());
        fail.set(false);
        ProviderResolution recovered = resolve(runtime, job, provider);
        assertFalse(recovered.fallback());
        assertEquals("master", recovered.options().get(0).getValue());
        assertEquals(2, loads.get());
    }

    @Test
    void uncachedConcurrentRequestsStillShareOneLoad(JenkinsRule jenkins) throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger loads = new AtomicInteger();
        GitRefProviderDefinition provider = new GitRefProviderDefinition("git", "git-profile", context -> {
            loads.incrementAndGet();
            entered.countDown();
            try {
                if (!release.await(4, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Fixture release timed out");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            return Map.of("refs/heads/master", "revision");
        });
        provider.setCacheTtlSeconds(0);
        FreeStyleProject job = job(jenkins);
        ProviderRuntime runtime = new ProviderRuntime();
        FutureTask<ProviderResolution> first = new FutureTask<>(() -> resolve(runtime, job, provider));
        FutureTask<ProviderResolution> second = new FutureTask<>(() -> resolve(runtime, job, provider));
        Thread firstCaller = new Thread(first, "git-first-caller");
        Thread secondCaller = new Thread(second, "git-second-caller");
        try {
            firstCaller.start();
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            secondCaller.start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (secondCaller.getState() != Thread.State.TIMED_WAITING && System.nanoTime() < deadline) {
                Thread.sleep(5);
            }
            assertEquals(Thread.State.TIMED_WAITING, secondCaller.getState());
            assertEquals(1, loads.get());
            release.countDown();
            assertEquals(
                    first.get(2, TimeUnit.SECONDS).options(),
                    second.get(2, TimeUnit.SECONDS).options());
            assertEquals(1, loads.get());
        } finally {
            release.countDown();
            firstCaller.join(2_000);
            secondCaller.join(2_000);
        }
    }

    private static GitRefProviderDefinition provider(AtomicBoolean fail, AtomicInteger loads) {
        return new GitRefProviderDefinition("git", "git-profile", context -> {
            loads.incrementAndGet();
            if (fail.get()) {
                throw new ProviderException(ErrorCode.PROVIDER_UNAVAILABLE, "git", "Owned source unavailable.");
            }
            return Map.of("refs/heads/master", "revision");
        });
    }

    private static FreeStyleProject job(JenkinsRule jenkins) throws Exception {
        ParameterFoundryGlobalConfiguration.get()
                .setConnectionProfiles(
                        List.of(new ConnectionProfile("git-profile", "https://example.invalid/repo.git")));
        return jenkins.createFreeStyleProject("git-runtime");
    }

    private static ProviderResolution resolve(
            ProviderRuntime runtime, FreeStyleProject job, GitRefProviderDefinition provider) {
        return runtime.resolve(
                job,
                new ProviderRegistry.LocatedProvider(provider, ProviderScope.GLOBAL, "jenkins"),
                ProviderOperation.PREVIEW,
                Map.of(),
                "builder");
    }
}
