package io.jenkins.plugins.unifiedparameters.provider;

import io.jenkins.plugins.unifiedparameters.evaluation.ErrorCode;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

final class ProviderRateLimiter {

    private final int principalRate;
    private final int jobRate;
    private final int providerRate;
    private final Clock clock;
    private final Semaphore generalGlobal;
    private final Semaphore reservedGlobal;
    private final Map<String, Semaphore> principals = new ConcurrentHashMap<>();
    private final Map<String, Semaphore> jobs = new ConcurrentHashMap<>();
    private final Map<String, Semaphore> providers = new ConcurrentHashMap<>();
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    ProviderRateLimiter() {
        this(60, 120, 240, 64, Clock.systemUTC());
    }

    ProviderRateLimiter(int principalRate, int jobRate, int providerRate, int globalInFlight, Clock clock) {
        this.principalRate = principalRate;
        this.jobRate = jobRate;
        this.providerRate = providerRate;
        int reserved = Math.max(1, globalInFlight / 4);
        this.generalGlobal = new Semaphore(Math.max(1, globalInFlight - reserved));
        this.reservedGlobal = new Semaphore(reserved);
        this.clock = clock;
    }

    Lease acquire(ProviderRequest request) {
        String principal = request.principal() == null ? "anonymous" : request.principal();
        String job = request.job().getFullName();
        String provider = request.ownerId() + "/" + request.providerId();
        checkRate("principal:" + principal, principalRate, request.providerId());
        checkRate("job:" + job, jobRate, request.providerId());
        checkRate("provider:" + provider, providerRate, request.providerId());
        Semaphore principalPermit = principals.computeIfAbsent(principal, ignored -> new Semaphore(8));
        Semaphore jobPermit = jobs.computeIfAbsent(job, ignored -> new Semaphore(16));
        Semaphore providerPermit = providers.computeIfAbsent(provider, ignored -> new Semaphore(8));
        Semaphore globalPermit = selectGlobalPermit(request.operation());
        if (globalPermit == null) {
            throw limited(request.providerId());
        }
        if (!principalPermit.tryAcquire()) {
            globalPermit.release();
            throw limited(request.providerId());
        }
        if (!jobPermit.tryAcquire()) {
            principalPermit.release();
            globalPermit.release();
            throw limited(request.providerId());
        }
        if (!providerPermit.tryAcquire()) {
            jobPermit.release();
            principalPermit.release();
            globalPermit.release();
            throw limited(request.providerId());
        }
        return new Lease(globalPermit, principalPermit, jobPermit, providerPermit);
    }

    private Semaphore selectGlobalPermit(ProviderOperation operation) {
        if ((operation == ProviderOperation.BUILD || operation == ProviderOperation.VALIDATE)
                && reservedGlobal.tryAcquire()) {
            return reservedGlobal;
        }
        return generalGlobal.tryAcquire() ? generalGlobal : null;
    }

    private void checkRate(String key, int limit, String providerId) {
        long minute = clock.millis() / 60_000;
        Window window = windows.computeIfAbsent(key, ignored -> new Window(minute));
        synchronized (window) {
            if (window.minute != minute) {
                window.minute = minute;
                window.count = 0;
            }
            if (window.count >= limit) {
                throw limited(providerId);
            }
            window.count++;
        }
    }

    private static ProviderException limited(String providerId) {
        return new ProviderException(ErrorCode.RATE_LIMITED, providerId, "Provider request budget is exhausted.", 60);
    }

    static final class Lease implements AutoCloseable {
        private final Semaphore[] semaphores;

        Lease(Semaphore... semaphores) {
            this.semaphores = semaphores;
        }

        @Override
        public void close() {
            for (int index = semaphores.length - 1; index >= 0; index--) {
                semaphores[index].release();
            }
        }
    }

    private static final class Window {
        private long minute;
        private int count;

        Window(long minute) {
            this.minute = minute;
        }
    }
}
