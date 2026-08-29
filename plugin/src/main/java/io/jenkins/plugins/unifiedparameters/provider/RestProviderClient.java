package io.jenkins.plugins.unifiedparameters.provider;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import hudson.ProxyConfiguration;
import hudson.security.ACL;
import io.jenkins.plugins.unifiedparameters.evaluation.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;

final class RestProviderClient {

    private static final int MAX_REDIRECTS = 3;

    private final NetworkPolicy networkPolicy;

    RestProviderClient() {
        this(new NetworkPolicy());
    }

    RestProviderClient(NetworkPolicy networkPolicy) {
        this.networkPolicy = networkPolicy;
    }

    Response get(ConnectionProfile profile, ProviderRequest providerRequest, URI initialUri) {
        HttpClient.Builder clientBuilder =
                profile.isUseJenkinsProxy() ? ProxyConfiguration.newHttpClientBuilder() : HttpClient.newBuilder();
        HttpClient client = clientBuilder
                .connectTimeout(Duration.ofSeconds(profile.getConnectTimeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        URI uri = initialUri;
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            try {
                profile.validateResolvedUri(uri);
                networkPolicy.requireAllowed(uri, profile.isAllowPrivateAddresses());
            } catch (ProviderException exception) {
                throw exception;
            } catch (IllegalArgumentException exception) {
                throw unavailable(providerRequest, "REST Provider target violates its Connection Profile.");
            }
            HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(profile.getTotalTimeoutSeconds()))
                    .GET()
                    .header("Accept", "application/json, application/xml, text/xml");
            for (ProfileHeader header : profile.getHeaders()) {
                request.header(header.getName(), header.getValue());
            }
            addCredential(profile, providerRequest, uri, request);
            HttpResponse<InputStream> response;
            try {
                response = client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw unavailable(providerRequest, "REST Provider request was interrupted.");
            } catch (IOException | RuntimeException exception) {
                throw unavailable(providerRequest, "REST Provider request failed.");
            }
            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                close(response.body());
                if (!profile.isFollowRedirects() || redirect == MAX_REDIRECTS) {
                    throw unavailable(providerRequest, "REST Provider redirect is not permitted.");
                }
                String location = response.headers()
                        .firstValue("Location")
                        .orElseThrow(() -> unavailable(providerRequest, "REST Provider redirect has no location."));
                uri = uri.resolve(location).normalize();
                continue;
            }
            if (status < 200 || status >= 300) {
                close(response.body());
                throw unavailable(providerRequest, "REST Provider returned HTTP " + status + ".");
            }
            String contentType =
                    response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
            String format;
            if (contentType.contains("json")) {
                format = "json";
            } else if (contentType.contains("xml")) {
                format = "xml";
            } else {
                close(response.body());
                throw unavailable(providerRequest, "REST Provider returned an unsupported Content-Type.");
            }
            byte[] body = readBounded(response.body(), profile.getMaximumResponseBytes(), providerRequest);
            return new Response(body, format, uri);
        }
        throw unavailable(providerRequest, "REST Provider redirect limit was exceeded.");
    }

    private static void addCredential(
            ConnectionProfile profile, ProviderRequest request, URI uri, HttpRequest.Builder builder) {
        if (profile.getCredentialId() == null || profile.getCredentialId().isBlank()) {
            return;
        }
        StandardUsernamePasswordCredentials credential = CredentialsProvider.findCredentialByIdInItem(
                profile.getCredentialId(),
                StandardUsernamePasswordCredentials.class,
                request.job(),
                ACL.SYSTEM2,
                URIRequirementBuilder.fromUri(uri.toString()).build());
        if (credential == null) {
            throw unavailable(request, "Connection Profile credential is unavailable.");
        }
        String token = credential.getUsername() + ":" + credential.getPassword().getPlainText();
        builder.header(
                "Authorization", "Basic " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] readBounded(InputStream input, int maximum, ProviderRequest request) {
        try (InputStream body = input) {
            byte[] value = body.readNBytes(maximum + 1);
            if (value.length > maximum) {
                throw unavailable(request, "REST Provider response exceeded the configured size limit.");
            }
            return value;
        } catch (IOException exception) {
            throw unavailable(request, "REST Provider response could not be read.");
        }
    }

    private static void close(InputStream input) {
        try {
            input.close();
        } catch (IOException ignored) {
            // The response is already being discarded.
        }
    }

    private static ProviderException unavailable(ProviderRequest request, String message) {
        return new ProviderException(ErrorCode.PROVIDER_UNAVAILABLE, request.providerId(), message);
    }

    record Response(byte[] body, String format, URI uri) {}
}
