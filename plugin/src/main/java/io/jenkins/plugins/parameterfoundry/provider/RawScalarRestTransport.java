package io.jenkins.plugins.parameterfoundry.provider;

import io.jenkins.plugins.parameterfoundry.evaluation.ErrorCode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import jenkins.util.Timer;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.routing.DefaultRoutePlanner;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.NoHttpResponseException;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;

/** Explicitly unauthenticated whole-body transport, separate from structured REST interpretation. */
final class RawScalarRestTransport {
    sealed interface Result permits Body, Failure, NextHop {}

    private record NextHop(URI uri) implements Result {}

    record Body(byte[] bytes) implements Result {
        Body {
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }

    enum FailureKind {
        HTTP_STATUS,
        DNS_UNRESOLVED,
        TLS_REJECTED,
        CONNECT_TIMEOUT,
        READ_TIMEOUT,
        CONNECTION_REFUSED,
        EARLY_CLOSE,
        PREMATURE_EOF,
        REDIRECT_LIMIT
    }

    record Failure(FailureKind kind, int status, String fingerprint, URI targetUri) implements Result {
        Failure(FailureKind kind, int status) {
            this(kind, status, null, null);
        }

        Failure(FailureKind kind, int status, String fingerprint) {
            this(kind, status, fingerprint, null);
        }
    }

    private static final class TlsRejectedException extends IOException {
        private static final long serialVersionUID = 1L;
        private final String fingerprint;

        TlsRejectedException(String fingerprint) {
            super("TLS connection was rejected.");
            this.fingerprint = fingerprint;
        }
    }

    static org.apache.hc.client5.http.ssl.TlsSocketStrategy restrictedTlsStrategy(
            org.apache.hc.client5.http.ssl.TlsSocketStrategy delegate) {
        return (socket, hostname, port, attachment, context) -> {
            try {
                return delegate.upgrade(socket, hostname, port, attachment, context);
            } catch (SocketTimeoutException failure) {
                throw new HandshakeReadTimeoutException();
            } catch (javax.net.ssl.SSLException failure) {
                // A typed result per attempt, not a sticky flag: later addresses may succeed or fail differently.
                throw new TlsRejectedException(TlsRejectionFingerprint.fingerprint(failure));
            }
        };
    }

    static final class HandshakeReadTimeoutException extends IOException {
        private static final long serialVersionUID = 1L;

        HandshakeReadTimeoutException() {
            super("TLS handshake exceeded its read deadline.");
        }
    }

    /** Completion and timeout cancellation share one lock; a late timer cannot cancel a completed request. */
    static final class Budget {
        private final long deadline;
        private boolean completed;
        private boolean expired;
        private HttpGet active;

        Budget(long deadline) {
            this.deadline = deadline;
        }

        synchronized void expire(HttpGet request) {
            if (!completed) {
                expired = true;
                HttpGet current = active == null ? request : active;
                if (current != null) current.cancel();
            }
        }

        synchronized void activate(HttpGet next, ProviderRequest request) {
            check(request);
            if (completed) throw unavailable(request, "Raw scalar request has completed.");
            active = next;
        }

        synchronized void deactivate(HttpGet previous) {
            if (active == previous) active = null;
        }

        synchronized void check(ProviderRequest request) {
            if (Thread.currentThread().isInterrupted()) {
                throw unavailable(request, "Raw scalar request was interrupted.");
            }
            if (expired || System.nanoTime() >= deadline) {
                expired = true;
                throw unavailable(request, "Raw scalar request exceeded its total time budget.");
            }
        }

        synchronized Result complete(Result result, ProviderRequest request) {
            check(request);
            completed = true;
            return result;
        }

        synchronized void finish() {
            completed = true;
        }
    }

    static boolean isNativeFixedShortBody(IOException failure, boolean fixedLength, long expected, int received) {
        return fixedLength
                && expected >= 0
                && received < expected
                && failure.getClass() == ConnectionClosedException.class
                && String.format(
                                java.util.Locale.ROOT,
                                "Premature end of Content-Length delimited message body (expected: %d; received: %d)",
                                expected,
                                received)
                        .equals(failure.getMessage());
    }

    private final NetworkPolicy networkPolicy;
    private final SSLContext fixtureTlsContext;

    RawScalarRestTransport(NetworkPolicy networkPolicy) {
        this(networkPolicy, null);
    }

    /** Owned test contexts retain the default hostname verifier; no configurable trust override exists. */
    RawScalarRestTransport(NetworkPolicy networkPolicy, SSLContext fixtureTlsContext) {
        this.networkPolicy = java.util.Objects.requireNonNull(networkPolicy);
        this.fixtureTlsContext = fixtureTlsContext;
    }

    Result get(ConnectionProfile profile, ProviderRequest request, URI uri) {
        return get(profile, request, uri, false);
    }

    Result get(ConnectionProfile profile, ProviderRequest request, URI uri, boolean nativeNonredirectBody) {
        return get(profile, request, uri, nativeNonredirectBody, RawRedirectTargets.freeze(java.util.List.of()));
    }

    Result get(
            ConnectionProfile profile,
            ProviderRequest request,
            URI uri,
            boolean nativeNonredirectBody,
            RawRedirectTargets targets) {
        if (uri == null || uri.getRawUserInfo() != null || uri.getRawFragment() != null) {
            throw unavailable(request, "Raw scalar target must not contain user information or a fragment.");
        }
        profile.validateResolvedUri(uri);
        try {
            if (profile.isFollowRedirects()) RawRedirectTargets.checkedUri(uri.toASCIIString());
            targets.validate(profile);
        } catch (IllegalArgumentException invalid) {
            throw unavailable(request, "Raw scalar target violates its Connection Profile.");
        }
        boolean following = profile.isFollowRedirects();
        if (profile.isUseJenkinsProxy()
                || following && targets.isEmpty()
                || profile.getCredentialId() != null
                        && !profile.getCredentialId().isBlank()
                || !profile.getHeaders().isEmpty()) {
            throw unavailable(request, "Raw scalar transport requires a reviewed direct credential-free profile.");
        }
        long remaining = Math.min(
                TimeUnit.SECONDS.toNanos(profile.getTotalTimeoutSeconds()),
                Duration.between(Instant.now(), request.deadline()).toNanos());
        Budget budget = new Budget(System.nanoTime() + remaining);
        budget.check(request);
        ScheduledFuture<?> cancellation =
                Timer.get().schedule(() -> budget.expire(null), Math.max(0L, remaining), TimeUnit.NANOSECONDS);
        Throwable primary = null;
        try {
            URI current = uri;
            for (int redirects = 0; ; redirects++) {
                Result result = getOnce(profile, request, current, nativeNonredirectBody, following, budget);
                if (result instanceof NextHop next) {
                    if (!targets.permits(next.uri())) {
                        throw unavailable(request, "Raw scalar redirect destination was not declared.");
                    }
                    if (redirects == 19) {
                        return budget.complete(new Failure(FailureKind.REDIRECT_LIMIT, 0, null, current), request);
                    }
                    current = next.uri();
                } else {
                    if (result instanceof Failure failure) {
                        result = new Failure(failure.kind(), failure.status(), failure.fingerprint(), current);
                    }
                    return budget.complete(result, request);
                }
            }
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            budget.finish();
            cleanup(request, primary, () -> cancellation.cancel(false));
        }
    }

    private Result getOnce(
            ConnectionProfile profile,
            ProviderRequest request,
            URI uri,
            boolean nativeNonredirectBody,
            boolean following,
            Budget budget) {
        try {
            profile.validateResolvedUri(uri);
        } catch (IllegalArgumentException invalid) {
            throw unavailable(request, "Raw scalar target violates its Connection Profile.");
        }
        budget.check(request);
        java.util.List<java.net.InetAddress> addresses;
        try {
            addresses = networkPolicy.resolveCheckedAddresses(uri, profile.isAllowPrivateAddresses());
        } catch (UnknownHostException unresolved) {
            // No resolver diagnostic or exception text becomes a selectable value.
            return new Failure(FailureKind.DNS_UNRESOLVED, 0);
        }
        budget.check(request);
        HttpGet get = new HttpGet(uri);
        budget.activate(get, request);
        org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager manager = null;
        CloseableHttpClient client = null;
        CloseableHttpResponse response = null;
        Throwable primary = null;
        try {
            try {
                SSLContext tls = fixtureTlsContext;
                if (tls == null) {
                    tls = SSLContext.getInstance("TLS");
                    tls.init(new KeyManager[0], null, null);
                }
                var tlsStrategy =
                        ClientTlsStrategyBuilder.create().setSslContext(tls).buildClassic();
                manager = PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(new ValidatedDnsResolver(uri, addresses))
                        .setTlsSocketStrategy(restrictedTlsStrategy(tlsStrategy))
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(Timeout.ofSeconds(profile.getConnectTimeoutSeconds()))
                                .setSocketTimeout(Timeout.ofSeconds(profile.getReadTimeoutSeconds()))
                                .build())
                        .setDefaultTlsConfig(TlsConfig.custom()
                                .setHandshakeTimeout(Timeout.ofSeconds(profile.getReadTimeoutSeconds()))
                                .build())
                        .build();
                client = HttpClients.custom()
                        .setConnectionManager(manager)
                        .setRoutePlanner(new DefaultRoutePlanner(DefaultSchemePortResolver.INSTANCE))
                        .setDefaultCredentialsProvider(new BasicCredentialsProvider())
                        .disableAuthCaching()
                        .disableCookieManagement()
                        .disableRedirectHandling()
                        .disableAutomaticRetries()
                        .disableContentCompression()
                        .setDefaultRequestConfig(RequestConfig.custom()
                                .setAuthenticationEnabled(false)
                                .setConnectionRequestTimeout(Timeout.ofSeconds(profile.getConnectTimeoutSeconds()))
                                .setResponseTimeout(Timeout.ofSeconds(profile.getReadTimeoutSeconds()))
                                .build())
                        .build();
                budget.check(request);
                response = CloseableHttpResponse.adapt(client.executeOpen(null, get, null));
                budget.check(request);
                int status = response.getCode();
                if (following && status == 305) {
                    throw unavailable(request, "Raw scalar proxy redirects are not permitted.");
                }
                var location = response.getLastHeader("Location");
                if (following && java.util.Set.of(300, 301, 302, 303, 307).contains(status) && location != null) {
                    URI next;
                    try {
                        String value = location.getValue();
                        if (value == null || value.length() > 8192) throw new IllegalArgumentException();
                        URI reference = URI.create(value);
                        if (reference.getScheme() != null && !uri.getScheme().equalsIgnoreCase(reference.getScheme())) {
                            throw new IllegalArgumentException();
                        }
                        next = new java.net.URL(uri.toURL(), value).toURI();
                        RawRedirectTargets.checkedUri(next.toASCIIString());
                    } catch (IllegalArgumentException
                            | java.net.URISyntaxException
                            | java.net.MalformedURLException invalid) {
                        throw unavailable(request, "Raw scalar redirect target is not permitted.");
                    }
                    get.cancel();
                    return new NextHop(next);
                }
                boolean nonredirectBody = nativeNonredirectBody
                        && (status == 304
                                || status == 306
                                || status == 308
                                || status == 302 && !response.containsHeader("Location"));
                if ((status < 200 || status >= 300) && !nonredirectBody) {
                    get.cancel();
                    return new Failure(FailureKind.HTTP_STATUS, status);
                }
                var entity = response.getEntity();
                if (entity == null) {
                    return new Body(new byte[0]);
                }
                long expected = entity.getContentLength();
                boolean fixedLength = expected >= 0 && !entity.isChunked();
                InputStream input = entity.getContent();
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                int maximum = profile.getMaximumResponseBytes();
                byte[] buffer = new byte[Math.min(8192, maximum + 1)];
                try {
                    while (true) {
                        budget.check(request);
                        int count = input.read(buffer, 0, Math.min(buffer.length, maximum + 1 - output.size()));
                        budget.check(request);
                        if (count < 0) {
                            return new Body(output.toByteArray());
                        }
                        output.write(buffer, 0, count);
                        if (output.size() > maximum) {
                            throw unavailable(request, "Raw scalar response exceeded the configured size limit.");
                        }
                    }
                } catch (ConnectionClosedException failure) {
                    budget.check(request);
                    if (isNativeFixedShortBody(failure, fixedLength, expected, output.size())) {
                        get.cancel();
                        return new Body(output.toByteArray());
                    }
                    if (entity.isChunked()
                            && failure.getClass() == ConnectionClosedException.class
                            && "Premature end of chunk coded message body: closing chunk expected"
                                    .equals(failure.getMessage())) {
                        return new Failure(FailureKind.PREMATURE_EOF, 0);
                    }
                    throw failure;
                }
            } catch (TlsRejectedException failure) {
                return new Failure(FailureKind.TLS_REJECTED, 0, failure.fingerprint);
            } catch (HandshakeReadTimeoutException failure) {
                budget.check(request);
                return new Failure(FailureKind.READ_TIMEOUT, 0);
            } catch (org.apache.hc.client5.http.ConnectTimeoutException failure) {
                budget.check(request);
                return new Failure(FailureKind.CONNECT_TIMEOUT, 0);
            } catch (SocketTimeoutException failure) {
                budget.check(request);
                return new Failure(FailureKind.READ_TIMEOUT, 0);
            } catch (ConnectException failure) {
                budget.check(request);
                if (failure.getMessage() != null && failure.getMessage().contains("Connection refused")) {
                    return new Failure(FailureKind.CONNECTION_REFUSED, 0);
                }
                primary = unavailable(request, "Raw scalar connection failed.");
                throw (ProviderException) primary;
            } catch (NoHttpResponseException failure) {
                budget.check(request);
                return new Failure(FailureKind.EARLY_CLOSE, 0);
            } catch (ProviderException failure) {
                primary = failure;
                throw failure;
            } catch (IOException | java.security.GeneralSecurityException failure) {
                budget.check(request);
                primary = unavailable(request, "Raw scalar request failed.");
                throw (ProviderException) primary;
            }
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            final CloseableHttpResponse ownedResponse = response;
            final CloseableHttpClient ownedClient = client;
            final org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager ownedManager = manager;
            cleanup(
                    request,
                    primary,
                    get::cancel,
                    () -> {
                        if (ownedResponse != null) ownedResponse.close(CloseMode.IMMEDIATE);
                    },
                    () -> {
                        if (ownedClient != null) ownedClient.close(CloseMode.IMMEDIATE);
                    },
                    () -> {
                        if (ownedManager != null) ownedManager.close(CloseMode.IMMEDIATE);
                    },
                    () -> budget.deactivate(get));
        }
    }

    static void cleanup(ProviderRequest request, Throwable primary, Runnable... operations) {
        boolean failed = false;
        Error fatal = null;
        for (Runnable operation : operations) {
            try {
                operation.run();
            } catch (RuntimeException | Error failure) {
                failed = true;
                if (primary != null) primary.addSuppressed(new IllegalStateException("Raw scalar cleanup failed."));
                else if (failure instanceof Error error && fatal == null) fatal = error;
            }
        }
        if (primary == null && failed) {
            if (fatal != null) throw fatal;
            throw unavailable(request, "Raw scalar cleanup failed.");
        }
    }

    private static ProviderException unavailable(ProviderRequest request, String message) {
        return new ProviderException(ErrorCode.PROVIDER_UNAVAILABLE, request.providerId(), message);
    }
}
