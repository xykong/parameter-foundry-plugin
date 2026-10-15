package io.jenkins.plugins.parameterfoundry.provider;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Real owned interfaces: a public-looking DNS assertion must not hide a loopback connection. */
final class OwnedHttpPeer {
    private static final List<InetAddress> ADDRESSES = discover();

    private OwnedHttpPeer() {}

    static InetAddress address() {
        return ADDRESSES.get(0);
    }

    static List<InetAddress> addresses() {
        return ADDRESSES;
    }

    private static List<InetAddress> discover() {
        var addresses = new ArrayList<InetAddress>();
        try {
            for (var network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                // VPN point-to-point addresses need not route back to a server bound on this host.
                if (!network.isUp() || network.isLoopback() || network.isPointToPoint()) continue;
                for (var address : Collections.list(network.getInetAddresses())) {
                    if (address instanceof Inet4Address
                            && address.isSiteLocalAddress()
                            && !address.isLoopbackAddress()
                            && !address.isLinkLocalAddress()
                            && !addresses.contains(address)) addresses.add(address);
                }
            }
        } catch (SocketException exception) {
            throw new IllegalStateException("Owned test interfaces unavailable.", exception);
        }
        if (addresses.isEmpty())
            throw new IllegalStateException("Raw HTTP tests require an owned non-loopback private interface.");
        return List.copyOf(addresses);
    }
}
