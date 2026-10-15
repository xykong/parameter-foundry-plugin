package io.jenkins.plugins.parameterfoundry.provider;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class RawScalarTlsFailureTest {
    @TempDir
    static Path directory;

    private static SSLContext firstServer;
    private static SSLContext secondServer;
    private static SSLContext clientContext;
    private static final String HOSTNAME_LITERAL = (Runtime.version().feature() == 17 ? "" : "(certificate_unknown) ")
            + "No subject alternative DNS name matching wrong-peer.invalid found.";

    @BeforeAll
    static void certificates() throws Exception {
        KeyStore trust = KeyStore.getInstance("PKCS12");
        trust.load(null, null);
        var contexts = new ArrayList<SSLContext>();
        for (String name : List.of("raw-peer.invalid", "wrong-peer.invalid")) {
            Path store = directory.resolve(name + ".p12");
            Process process = new ProcessBuilder(
                            Path.of(System.getProperty("java.home"), "bin", "keytool")
                                    .toString(),
                            "-genkeypair",
                            "-alias",
                            "peer",
                            "-keyalg",
                            "RSA",
                            "-keysize",
                            "2048",
                            "-dname",
                            "CN=" + name,
                            "-ext",
                            "SAN=dns:" + name,
                            "-validity",
                            "2",
                            "-keystore",
                            store.toString(),
                            "-storetype",
                            "PKCS12",
                            "-storepass",
                            "owned-fixture-pass",
                            "-keypass",
                            "owned-fixture-pass",
                            "-noprompt")
                    .redirectErrorStream(true)
                    .redirectOutput(directory.resolve(name + ".log").toFile())
                    .start();
            assertTrue(process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(0, process.exitValue());
            KeyStore keys = KeyStore.getInstance("PKCS12");
            try (var input = Files.newInputStream(store)) {
                keys.load(input, "owned-fixture-pass".toCharArray());
            }
            trust.setCertificateEntry(name, keys.getCertificate("peer"));
            var managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            managers.init(keys, "owned-fixture-pass".toCharArray());
            var context = SSLContext.getInstance("TLS");
            context.init(managers.getKeyManagers(), null, null);
            contexts.add(context);
        }
        firstServer = contexts.get(0);
        secondServer = contexts.get(1);
        var managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        managers.init(trust);
        clientContext = SSLContext.getInstance("TLS");
        clientContext.init(new KeyManager[0], managers.getTrustManagers(), null);
    }

    private HttpsServer server(InetAddress address, int port, SSLContext tls, AtomicInteger calls) throws Exception {
        var server = HttpsServer.create(new InetSocketAddress(address, port), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(tls));
        server.createContext("/value", exchange -> {
            calls.incrementAndGet();
            byte[] body = "release/owned".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    private ConnectionProfile profile(int port) {
        var profile = new ConnectionProfile("tls", "https://wrong-peer.invalid:" + port + "/");
        profile.setAllowPrivateAddresses(true);
        profile.setUseJenkinsProxy(false);
        profile.setConnectTimeoutSeconds(2);
        profile.setReadTimeoutSeconds(2);
        profile.setTotalTimeoutSeconds(10);
        return profile;
    }

    private ProviderLoadContext context(JenkinsRule j, ConnectionProfile profile) throws Exception {
        return new ProviderLoadContext(
                new ProviderRequest(
                        j.createFreeStyleProject(),
                        "tls",
                        "digest",
                        ProviderScope.GLOBAL,
                        "jenkins",
                        Map.of(),
                        ProviderOperation.RESOLVE,
                        Instant.now().plusSeconds(20),
                        "builder"),
                Optional.of(profile));
    }

    private RestRawFailureChoice declaration() {
        var row = new RestRawFailureChoice("TLS_REJECTED", HOSTNAME_LITERAL);
        row.setFingerprint(TlsRejectionFingerprint.fingerprint(new SSLHandshakeException(HOSTNAME_LITERAL)));
        return row;
    }

    private RestProviderDefinition provider(List<InetAddress> addresses, RestRawFailureChoice row) {
        var transport = new RawScalarRestTransport(new NetworkPolicy(host -> addresses), clientContext);
        var provider = new RestProviderDefinition("tls", "tls", "value", null, transport);
        provider.setResponseMode("active-choices-raw-scalar");
        provider.setRawTrailingChoices(List.of(new RestRawLiteral("release/prod")));
        provider.setRawFailureChoices(List.of(row));
        return provider;
    }

    @Test
    void actualTlsRejectionMatchesOnlyExplicitFingerprintAndRecovers(JenkinsRule j) throws Exception {
        var calls = new AtomicInteger();
        var server = server(OwnedHttpPeer.address(), 0, firstServer, calls);
        try {
            var context = context(j, profile(server.getAddress().getPort()));
            try (var tcp = new java.net.Socket(
                            OwnedHttpPeer.address(), server.getAddress().getPort());
                    var socket = (SSLSocket) clientContext
                            .getSocketFactory()
                            .createSocket(
                                    tcp,
                                    "wrong-peer.invalid",
                                    server.getAddress().getPort(),
                                    true)) {
                var parameters = socket.getSSLParameters();
                parameters.setEndpointIdentificationAlgorithm("HTTPS");
                socket.setSSLParameters(parameters);
                var nativeFailure = assertThrows(SSLHandshakeException.class, socket::startHandshake);
                assertEquals(HOSTNAME_LITERAL, nativeFailure.getMessage());
            }
            var provider = provider(List.of(OwnedHttpPeer.address()), declaration());
            assertEquals(
                    List.of(HOSTNAME_LITERAL, "release/prod"),
                    provider.load(context).stream().map(o -> o.getValue()).toList());
            assertEquals(0, calls.get());
            var unknown = declaration();
            unknown.setFingerprint("0".repeat(64));
            provider.setRawFailureChoices(List.of(unknown));
            var failure = assertThrows(ProviderException.class, () -> provider.load(context));
            assertNull(failure.getCause());
            assertFalse(failure.getMessage().contains("wrong-peer")
                    || failure.getMessage().contains("00000000"));
            var valid = new ConnectionProfile(
                    "tls", "https://raw-peer.invalid:" + server.getAddress().getPort() + "/");
            valid.setAllowPrivateAddresses(true);
            valid.setUseJenkinsProxy(false);
            assertEquals(
                    "release/owned",
                    provider.load(new ProviderLoadContext(context.request(), Optional.of(valid)))
                            .get(0)
                            .getValue());
            assertEquals(1, calls.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void addressFallbackUsesFinalSuccessOrRefusalWithoutPreviousFingerprint(JenkinsRule j) throws Exception {
        var addresses = OwnedHttpPeer.addresses();
        Assumptions.assumeTrue(addresses.size() >= 2, "Requires two owned private interface addresses.");
        var calls = new AtomicInteger();
        var first = server(addresses.get(0), 0, firstServer, calls);
        int port = first.getAddress().getPort();
        var second = server(addresses.get(1), port, secondServer, calls);
        try {
            var provider = provider(addresses.subList(0, 2), declaration());
            var context = context(j, profile(port));
            assertEquals("release/owned", provider.load(context).get(0).getValue());
            assertEquals(1, calls.get());
            second.stop(0);
            try (var closed = new ServerSocket(port, 1, addresses.get(1))) {}
            assertThrows(ProviderException.class, () -> provider.load(context));
            assertEquals(1, calls.get());
        } finally {
            first.stop(0);
            second.stop(0);
        }
    }

    @Test
    void runtimeUnknownTlsHasNoImplicitFallbackAndNormalApiStaysSanitized(JenkinsRule j) throws Exception {
        var calls = new AtomicInteger();
        var server = server(OwnedHttpPeer.address(), 0, firstServer, calls);
        try {
            var provider = provider(List.of(OwnedHttpPeer.address()), declaration());
            provider.setCacheTtlSeconds(0);
            provider.setFailurePolicy("fail-closed");
            var configuration = ParameterFoundryGlobalConfiguration.get();
            configuration.setProviders(List.of(provider));
            configuration.setConnectionProfiles(
                    List.of(profile(server.getAddress().getPort())));
            var definition = new io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition("REF");
            definition.setProviderId("tls");
            definition.setRenderer("select");
            definition.setDefaultMode("first");
            definition.setStandardApiInputMode("active-choices");
            var job = j.createFreeStyleProject();
            job.addProperty(new hudson.model.ParametersDefinitionProperty(definition));
            assertEquals(
                    HOSTNAME_LITERAL,
                    assertInstanceOf(hudson.model.StringParameterValue.class, definition.getDefaultParameterValue())
                            .getValue());
            var unknown = declaration();
            unknown.setFingerprint("0".repeat(64));
            provider.setRawFailureChoices(List.of(unknown));
            var located = ProviderRegistry.findProvider(job, "tls").orElseThrow();
            assertThrows(ProviderException.class, () -> ProviderRuntime.get()
                    .resolve(job, located, ProviderOperation.RESOLVE, Map.of(), "builder"));
            assertNull(definition.getDefaultParameterValue());
            var health = ProviderRuntime.get().health("jenkins", "tls").orElseThrow();
            assertFalse(health.healthy());
            assertFalse(health.message().contains("wrong-peer")
                    || health.message().contains("00000000")
                    || health.message().contains("certificate"));
            var request = new org.htmlunit.WebRequest(
                    new java.net.URL(j.getURL(), job.getUrl() + "parameter-foundry/api/v1/resolve"),
                    org.htmlunit.HttpMethod.POST);
            request.setAdditionalHeader("Content-Type", "application/json");
            request.setRequestBody("{\"parameter\":\"REF\",\"version\":1}");
            var issuer = j.jenkins.getCrumbIssuer();
            request.setAdditionalHeader(issuer.getCrumbRequestField(), issuer.getCrumb());
            var web = j.createWebClient();
            web.getOptions().setThrowExceptionOnFailingStatusCode(false);
            String body = web.loadWebResponse(request).getContentAsString();
            assertFalse(body.contains("00000000")
                    || body.contains("wrong-peer.invalid")
                    || body.contains("certificate_unknown"));
            assertFalse(net.sf.json.JSONObject.fromObject(body).getBoolean("ok"));
            provider.setFallbackOptions(
                    List.of(new io.jenkins.plugins.parameterfoundry.model.Option("reviewed/fallback")));
            var fallback = ProviderRuntime.get().resolve(job, located, ProviderOperation.RESOLVE, Map.of(), "builder");
            assertTrue(fallback.fallback());
            assertEquals("reviewed/fallback", fallback.options().get(0).getValue());
            assertEquals(0, calls.get());
            assertEquals(1, job.getNextBuildNumber());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void declarationMutationDuringDnsCannotChangeCurrentMatchAndExpiredRequestsReject(JenkinsRule j) throws Exception {
        var calls = new AtomicInteger();
        var server = server(OwnedHttpPeer.address(), 0, firstServer, calls);
        try {
            var row = declaration();
            var transport = new RawScalarRestTransport(
                    new NetworkPolicy(host -> {
                        row.setFingerprint("0".repeat(64));
                        return List.of(OwnedHttpPeer.address());
                    }),
                    clientContext);
            var provider = new RestProviderDefinition("tls", "tls", "value", null, transport);
            provider.setResponseMode("active-choices-raw-scalar");
            provider.setRawFailureChoices(List.of(row));
            var context = context(j, profile(server.getAddress().getPort()));
            assertEquals(HOSTNAME_LITERAL, provider.load(context).get(0).getValue());
            assertThrows(ProviderException.class, () -> provider.load(context));
            var previous = context.request();
            var expired = new ProviderRequest(
                    previous.job(),
                    previous.providerId(),
                    previous.providerDigest(),
                    previous.scope(),
                    previous.ownerId(),
                    Map.of(),
                    previous.operation(),
                    Instant.now().minusSeconds(1),
                    previous.principal());
            assertThrows(
                    ProviderException.class,
                    () -> provider.load(new ProviderLoadContext(expired, context.connectionProfile())));
            try {
                Thread.currentThread().interrupt();
                assertThrows(ProviderException.class, () -> provider.load(context));
            } finally {
                Thread.interrupted();
            }
            assertEquals(0, calls.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void boundaryDoesNotRetainOriginalExceptionsOrStickyState() throws Exception {
        var calls = new AtomicInteger();
        var strategy = RawScalarRestTransport.restrictedTlsStrategy((a, b, c, d, e) -> {
            int invocation = calls.incrementAndGet();
            if (invocation == 1) throw new SSLHandshakeException(HOSTNAME_LITERAL);
            if (invocation == 3) throw new java.net.ConnectException("owned-refusal");
            return null;
        });
        var captured = assertThrows(IOException.class, () -> strategy.upgrade(null, "owned", 443, null, null));
        assertNull(captured.getCause());
        assertEquals("TLS connection was rejected.", captured.getMessage());
        assertNull(strategy.upgrade(null, "owned", 443, null, null));
        assertThrows(java.net.ConnectException.class, () -> strategy.upgrade(null, "owned", 443, null, null));
    }
}
