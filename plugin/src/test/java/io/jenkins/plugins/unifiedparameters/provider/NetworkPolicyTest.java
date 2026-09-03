package io.jenkins.plugins.unifiedparameters.provider;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class NetworkPolicyTest {

    @Test
    void blocksLoopbackLinkLocalMetadataPrivateAndUniqueLocalAddresses() throws Exception {
        assertBlocked("127.0.0.1", false);
        assertBlocked("169.254.169.254", true);
        assertBlocked("100.100.100.200", true);
        assertBlocked("10.0.0.1", false);
        assertBlocked("192.168.1.1", false);
        assertBlocked("fc00::1", false);
    }

    @Test
    void administratorPrivateAddressDecisionDoesNotPermitMetadataOrLoopback() throws Exception {
        assertAllowed("10.20.30.40", true);
        assertBlocked("127.0.0.1", true);
        assertBlocked("169.254.169.254", true);
    }

    @Test
    void allResolvedAddressesMustPassPolicy() throws Exception {
        NetworkPolicy policy = new NetworkPolicy(
                host -> List.of(InetAddress.getByName("203.0.113.10"), InetAddress.getByName("10.0.0.5")));
        assertThrows(
                ProviderException.class,
                () -> policy.requireAllowed(URI.create("https://service.example/options"), false));
    }

    @Test
    void connectionProfileExposesTheSameTargetPolicyToCompanions() {
        ConnectionProfile profile = new ConnectionProfile("git", "https://169.254.169.254/repository.git");
        profile.setAllowPrivateAddresses(true);

        assertThrows(ProviderException.class, () -> profile.requireAllowedTarget(profile.getBaseUri()));
    }

    private static void assertBlocked(String address, boolean allowPrivate) throws Exception {
        NetworkPolicy policy = new NetworkPolicy(host -> List.of(InetAddress.getByName(address)));
        assertThrows(
                ProviderException.class,
                () -> policy.requireAllowed(URI.create("https://service.example/options"), allowPrivate));
    }

    private static void assertAllowed(String address, boolean allowPrivate) throws Exception {
        NetworkPolicy policy = new NetworkPolicy(host -> List.of(InetAddress.getByName(address)));
        assertDoesNotThrow(() -> policy.requireAllowed(URI.create("https://service.example/options"), allowPrivate));
    }
}
