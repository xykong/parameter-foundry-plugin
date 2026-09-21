package io.jenkins.plugins.parameterfoundry.provider;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.model.FreeStyleProject;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ProviderRateLimiterTest {

    @Test
    void sweepDropsIdleBudgetsAndStaleWindows(JenkinsRule jenkins) throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-10T00:00:00Z"));
        ProviderRateLimiter limiter = new ProviderRateLimiter(100, 100, 100, 4, clock);
        FreeStyleProject job = jenkins.createFreeStyleProject("rate-limiter-sweep");

        ProviderRateLimiter.Lease first = limiter.acquire(request(job));
        ProviderRateLimiter.Lease second = limiter.acquire(request(job));
        assertEquals(6, limiter.trackedKeys());

        first.close();
        second.close();
        limiter.sweepForEviction();
        // The current-minute windows remain; idle semaphores are dropped.
        assertEquals(3, limiter.trackedKeys());

        clock.advanceSeconds(61);
        limiter.sweepForEviction();
        assertEquals(0, limiter.trackedKeys());
    }

    @Test
    void sweepKeepsHeldBudgets(JenkinsRule jenkins) throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-10T00:00:00Z"));
        ProviderRateLimiter limiter = new ProviderRateLimiter(100, 100, 100, 4, clock);
        FreeStyleProject job = jenkins.createFreeStyleProject("rate-limiter-held");

        ProviderRateLimiter.Lease held = limiter.acquire(request(job));
        try {
            limiter.sweepForEviction();
            // Principal, job, and provider budgets stay while a permit is in flight.
            assertEquals(6, limiter.trackedKeys());
        } finally {
            held.close();
        }
    }

    @Test
    void sweepPreservesPerMinuteRateBudget(JenkinsRule jenkins) throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-10T00:00:00Z"));
        ProviderRateLimiter limiter = new ProviderRateLimiter(1, 100, 100, 4, clock);
        FreeStyleProject job = jenkins.createFreeStyleProject("rate-limiter-window");

        try (ProviderRateLimiter.Lease ignored = limiter.acquire(request(job))) {
            // Budget exhausted for the current minute.
        }
        clock.advanceSeconds(61);
        limiter.sweepForEviction();
        assertDoesNotThrow(() -> {
            try (ProviderRateLimiter.Lease ignored = limiter.acquire(request(job))) {
                // A fresh minute starts a fresh budget.
            }
        });
    }

    private static ProviderRequest request(FreeStyleProject job) {
        return new ProviderRequest(
                job,
                "capacity",
                "digest",
                ProviderScope.GLOBAL,
                "jenkins",
                Map.of(),
                ProviderOperation.RESOLVE,
                Instant.now().plusSeconds(5),
                "builder");
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
