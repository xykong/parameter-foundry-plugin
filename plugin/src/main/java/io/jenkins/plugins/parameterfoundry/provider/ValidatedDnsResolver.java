package io.jenkins.plugins.parameterfoundry.provider;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import org.apache.hc.client5.http.DnsResolver;

/** Connects to the checked address snapshot while retaining the original HTTP/TLS host. */
final class ValidatedDnsResolver implements DnsResolver {
    private final String host;
    private final List<InetAddress> addresses;

    ValidatedDnsResolver(URI target, List<InetAddress> addresses) {
        this.host = bare(java.util.Objects.requireNonNull(target.getHost()));
        this.addresses = List.copyOf(addresses);
        if (this.addresses.isEmpty()) throw new IllegalArgumentException("A checked address snapshot is required.");
    }

    @Override
    public InetAddress[] resolve(String requestedHost) throws UnknownHostException {
        requireHost(requestedHost);
        return addresses.toArray(InetAddress[]::new);
    }

    @Override
    public String resolveCanonicalHostname(String requestedHost) throws UnknownHostException {
        requireHost(requestedHost);
        return host;
    }

    private void requireHost(String requestedHost) throws UnknownHostException {
        if (requestedHost == null || !host.equalsIgnoreCase(bare(requestedHost))) {
            throw new UnknownHostException("The connection host differs from the checked target.");
        }
    }

    private static String bare(String name) {
        return name.startsWith("[") && name.endsWith("]") ? name.substring(1, name.length() - 1) : name;
    }
}
