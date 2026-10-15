package io.jenkins.plugins.parameterfoundry.provider;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;

final class NetworkPolicy {

    private final AddressResolver resolver;

    NetworkPolicy() {
        this(host -> List.of(InetAddress.getAllByName(host)));
    }

    NetworkPolicy(AddressResolver resolver) {
        this.resolver = resolver;
    }

    void requireAllowed(URI uri, boolean allowPrivateAddresses) {
        resolveAllowed(uri, allowPrivateAddresses);
    }

    List<InetAddress> resolveAllowed(URI uri, boolean allowPrivateAddresses) {
        if (uri == null
                || uri.getHost() == null
                || !java.util.Set.of("http", "https").contains(uri.getScheme())) {
            throw new ProviderException(
                    io.jenkins.plugins.parameterfoundry.evaluation.ErrorCode.PROVIDER_UNAVAILABLE,
                    "network",
                    "Provider target is not a valid HTTP(S) URI.");
        }
        List<InetAddress> addresses;
        try {
            addresses = List.copyOf(resolver.resolve(uri.getHost()));
        } catch (UnknownHostException exception) {
            throw new ProviderException(
                    io.jenkins.plugins.parameterfoundry.evaluation.ErrorCode.PROVIDER_UNAVAILABLE,
                    "network",
                    "Provider target could not be resolved.");
        }
        if (addresses.isEmpty()) {
            throw new ProviderException(
                    io.jenkins.plugins.parameterfoundry.evaluation.ErrorCode.PROVIDER_UNAVAILABLE,
                    "network",
                    "Provider target did not resolve to an address.");
        }
        for (InetAddress address : addresses) {
            if (isAlwaysBlocked(address) || (!allowPrivateAddresses && isPrivate(address))) {
                throw new ProviderException(
                        io.jenkins.plugins.parameterfoundry.evaluation.ErrorCode.PROVIDER_UNAVAILABLE,
                        "network",
                        "Provider target resolves to a prohibited address.");
            }
        }
        return addresses;
    }

    private static boolean isAlwaysBlocked(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            return (first == 100 && second == 100) || (first == 168 && second == 63) || (first == 169 && second == 254);
        }
        return false;
    }

    private static boolean isPrivate(InetAddress address) {
        if (address.isSiteLocalAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            return (first == 100 && second >= 64 && second <= 127);
        }
        if (address instanceof Inet6Address) {
            return (Byte.toUnsignedInt(bytes[0]) & 0xfe) == 0xfc;
        }
        return true;
    }

    interface AddressResolver {
        List<InetAddress> resolve(String host) throws UnknownHostException;
    }
}
