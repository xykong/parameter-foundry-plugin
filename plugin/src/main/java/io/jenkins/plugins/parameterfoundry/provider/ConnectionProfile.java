package io.jenkins.plugins.parameterfoundry.provider;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import java.io.Serial;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public final class ConnectionProfile extends AbstractDescribableImpl<ConnectionProfile> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String baseUrl;
    private String displayName;
    private boolean usableByChildren;
    private String credentialId;
    private List<ProfileHeader> headers = List.of();
    private List<String> allowedPathPrefixes = List.of("/");
    private List<String> allowedQueryParameters = List.of();
    private boolean allowPrivateAddresses;
    private boolean followRedirects;
    private boolean useJenkinsProxy = true;
    private int connectTimeoutSeconds = 5;
    private int readTimeoutSeconds = 5;
    private int totalTimeoutSeconds = 10;
    private int maximumResponseBytes = 2 * 1024 * 1024;

    @DataBoundConstructor
    public ConnectionProfile(String id, String baseUrl) {
        if (id == null || !id.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) {
            throw new IllegalArgumentException("Connection Profile ID is invalid.");
        }
        this.id = id;
        this.baseUrl = normalizeBaseUrl(baseUrl).toString();
    }

    public String getId() {
        return id;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getDisplayName() {
        return displayName == null || displayName.isBlank() ? id : displayName;
    }

    @DataBoundSetter
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean isUsableByChildren() {
        return usableByChildren;
    }

    @DataBoundSetter
    public void setUsableByChildren(boolean usableByChildren) {
        this.usableByChildren = usableByChildren;
    }

    public String getCredentialId() {
        return credentialId;
    }

    @DataBoundSetter
    public void setCredentialId(String credentialId) {
        this.credentialId = credentialId;
    }

    public List<ProfileHeader> getHeaders() {
        return headers == null ? List.of() : headers;
    }

    @DataBoundSetter
    public void setHeaders(List<ProfileHeader> headers) {
        this.headers = headers == null ? List.of() : List.copyOf(new ArrayList<>(headers));
    }

    public List<String> getAllowedPathPrefixes() {
        return allowedPathPrefixes == null ? List.of("/") : allowedPathPrefixes;
    }

    public String getAllowedPathPrefixesText() {
        return String.join("\n", getAllowedPathPrefixes());
    }

    @DataBoundSetter
    public void setAllowedPathPrefixes(List<String> allowedPathPrefixes) {
        List<String> normalized = new ArrayList<>();
        for (String prefix : allowedPathPrefixes == null ? List.<String>of() : allowedPathPrefixes) {
            if (prefix == null || !prefix.startsWith("/") || prefix.contains("..") || prefix.contains("?")) {
                throw new IllegalArgumentException("Allowed paths must be normalized absolute path prefixes.");
            }
            normalized.add(prefix);
        }
        this.allowedPathPrefixes = normalized.isEmpty() ? List.of("/") : List.copyOf(normalized);
    }

    @DataBoundSetter
    public void setAllowedPathPrefixesText(String value) {
        setAllowedPathPrefixes(lines(value));
    }

    public List<String> getAllowedQueryParameters() {
        return allowedQueryParameters == null ? List.of() : allowedQueryParameters;
    }

    public String getAllowedQueryParametersText() {
        return String.join("\n", getAllowedQueryParameters());
    }

    @DataBoundSetter
    public void setAllowedQueryParameters(List<String> allowedQueryParameters) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String name : allowedQueryParameters == null ? List.<String>of() : allowedQueryParameters) {
            if (name == null || !name.matches("[A-Za-z0-9._~-]{1,128}")) {
                throw new IllegalArgumentException("Allowed query parameter name is invalid.");
            }
            normalized.add(name);
        }
        this.allowedQueryParameters = List.copyOf(normalized);
    }

    @DataBoundSetter
    public void setAllowedQueryParametersText(String value) {
        setAllowedQueryParameters(lines(value));
    }

    public boolean isAllowPrivateAddresses() {
        return allowPrivateAddresses;
    }

    @DataBoundSetter
    public void setAllowPrivateAddresses(boolean allowPrivateAddresses) {
        this.allowPrivateAddresses = allowPrivateAddresses;
    }

    public boolean isFollowRedirects() {
        return followRedirects;
    }

    @DataBoundSetter
    public void setFollowRedirects(boolean followRedirects) {
        this.followRedirects = followRedirects;
    }

    public boolean isUseJenkinsProxy() {
        return useJenkinsProxy;
    }

    @DataBoundSetter
    public void setUseJenkinsProxy(boolean useJenkinsProxy) {
        this.useJenkinsProxy = useJenkinsProxy;
    }

    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    @DataBoundSetter
    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = boundedTimeout(connectTimeoutSeconds);
    }

    public int getReadTimeoutSeconds() {
        return readTimeoutSeconds;
    }

    @DataBoundSetter
    public void setReadTimeoutSeconds(int readTimeoutSeconds) {
        this.readTimeoutSeconds = boundedTimeout(readTimeoutSeconds);
    }

    public int getTotalTimeoutSeconds() {
        return totalTimeoutSeconds;
    }

    @DataBoundSetter
    public void setTotalTimeoutSeconds(int totalTimeoutSeconds) {
        this.totalTimeoutSeconds = boundedTimeout(totalTimeoutSeconds);
    }

    public int getMaximumResponseBytes() {
        return maximumResponseBytes;
    }

    @DataBoundSetter
    public void setMaximumResponseBytes(int maximumResponseBytes) {
        if (maximumResponseBytes < 1024 || maximumResponseBytes > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("maximumResponseBytes must be between 1 KiB and 10 MiB.");
        }
        this.maximumResponseBytes = maximumResponseBytes;
    }

    public URI buildUri(String relativePath, java.util.Map<String, String> query) {
        if (relativePath == null
                || relativePath.isBlank()
                || relativePath.startsWith("//")
                || relativePath.contains("..")
                || relativePath.contains("#")
                || relativePath.contains("?")
                || relativePath.matches("(?i).*(%2e|%2f|%5c).*")
                || !relativePath.matches("[A-Za-z0-9._~!$&'()*+,;=:@%/-]+")) {
            throw new IllegalArgumentException("Provider path must be a normalized relative path.");
        }
        URI base = URI.create(baseUrl);
        URI resolved = base.resolve(relativePath.startsWith("/") ? relativePath.substring(1) : relativePath)
                .normalize();
        validateResolvedUri(resolved);
        StringBuilder encodedQuery = new StringBuilder();
        for (java.util.Map.Entry<String, String> entry : query.entrySet()) {
            if (!getAllowedQueryParameters().contains(entry.getKey())) {
                throw new IllegalArgumentException("Query parameter is not allowed: " + entry.getKey());
            }
            if (!encodedQuery.isEmpty()) {
                encodedQuery.append('&');
            }
            encodedQuery.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
        }
        return encodedQuery.isEmpty() ? resolved : URI.create(resolved.toASCIIString() + "?" + encodedQuery);
    }

    public URI getBaseUri() {
        return URI.create(baseUrl);
    }

    public void requireAllowedTarget(URI target) {
        try {
            validateResolvedUri(target);
            new NetworkPolicy().requireAllowed(target, allowPrivateAddresses);
        } catch (ProviderException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new ProviderException(
                    io.jenkins.plugins.parameterfoundry.evaluation.ErrorCode.PROVIDER_UNAVAILABLE,
                    id,
                    "Connection Profile target is not allowed.");
        }
    }

    void validateResolvedUri(URI resolved) {
        URI base = URI.create(baseUrl);
        if (!sameAuthority(base, resolved)
                || resolved.getFragment() != null
                || !resolved.getPath().startsWith(base.getPath())) {
            throw new IllegalArgumentException("Provider path escapes the Connection Profile.");
        }
        boolean allowed = getAllowedPathPrefixes().stream()
                .anyMatch(prefix -> resolved.getPath().startsWith(prefix));
        if (!allowed) {
            throw new IllegalArgumentException("Provider path is not allowed by the Connection Profile.");
        }
        if (resolved.getQuery() != null) {
            for (String pair : resolved.getRawQuery().split("&")) {
                String name = pair.contains("=") ? pair.substring(0, pair.indexOf('=')) : pair;
                String decoded = java.net.URLDecoder.decode(name, java.nio.charset.StandardCharsets.UTF_8);
                if (!getAllowedQueryParameters().contains(decoded)) {
                    throw new IllegalArgumentException("Query parameter is not allowed: " + decoded);
                }
            }
        }
    }

    String configurationDigest() {
        return Digests.sha256(id
                + '\u0000'
                + baseUrl
                + '\u0000'
                + credentialId
                + '\u0000'
                + getAllowedPathPrefixes()
                + '\u0000'
                + getAllowedQueryParameters()
                + '\u0000'
                + allowPrivateAddresses
                + '\u0000'
                + followRedirects
                + '\u0000'
                + getHeaders().stream()
                        .map(header -> header.getName() + ":" + header.getValue())
                        .toList());
    }

    private static URI normalizeBaseUrl(String value) {
        try {
            URI uri = new URI(value).normalize();
            if (!Set.of("http", "https").contains(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getFragment() != null
                    || uri.getQuery() != null) {
                throw new IllegalArgumentException("Connection Profile base URL must be an HTTP(S) authority.");
            }
            String path = uri.getPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            if (!path.endsWith("/")) {
                path += "/";
            }
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), path, null, null);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Connection Profile base URL is invalid.", exception);
        }
    }

    private static boolean sameAuthority(URI first, URI second) {
        return java.util.Objects.equals(first.getScheme(), second.getScheme())
                && java.util.Objects.equals(first.getHost(), second.getHost())
                && effectivePort(first) == effectivePort(second);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equals(uri.getScheme()) ? 443 : 80;
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value == null ? "" : value, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private static int boundedTimeout(int value) {
        if (value < 1 || value > 30) {
            throw new IllegalArgumentException("Timeout must be between 1 and 30 seconds.");
        }
        return value;
    }

    private static List<String> lines(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return value.lines().map(String::trim).filter(line -> !line.isEmpty()).toList();
    }

    @Extension
    @Symbol("foundryConnectionProfile")
    public static final class DescriptorImpl extends Descriptor<ConnectionProfile> {
        @Override
        public String getDisplayName() {
            return "Parameter Foundry Connection Profile";
        }
    }
}
