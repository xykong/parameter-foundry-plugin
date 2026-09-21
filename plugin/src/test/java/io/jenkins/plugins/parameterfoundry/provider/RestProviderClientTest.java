package io.jenkins.plugins.parameterfoundry.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.sun.net.httpserver.HttpServer;
import hudson.ProxyConfiguration;
import hudson.model.FreeStyleProject;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class RestProviderClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void enforcesContentTypeResponseSizeAndRedirectProfile(JenkinsRule jenkins) throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/options", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "[{\"value\":\"android\"}]".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/api/text", exchange -> {
            byte[] body = "not structured".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/api/redirect", exchange -> {
            exchange.getResponseHeaders().set("Location", "https://attacker.invalid/options");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();

        int port = server.getAddress().getPort();
        ConnectionProfile profile = new ConnectionProfile("local", "http://127.0.0.1:" + port + "/api/");
        profile.setAllowedPathPrefixes(List.of("/api/"));
        profile.setMaximumResponseBytes(1024);
        profile.setFollowRedirects(true);
        profile.setCredentialId("service-user");
        SystemCredentialsProvider.getInstance()
                .getCredentials()
                .add(new UsernamePasswordCredentialsImpl(
                        CredentialsScope.GLOBAL, "service-user", "Synthetic REST credential", "user", "password"));
        SystemCredentialsProvider.getInstance().save();
        FreeStyleProject job = jenkins.createFreeStyleProject("rest-client");
        ProviderRequest request = new ProviderRequest(
                job,
                "rest",
                "digest",
                ProviderScope.GLOBAL,
                "jenkins",
                Map.of(),
                ProviderOperation.RESOLVE,
                Instant.now().plusSeconds(5),
                "builder");
        RestProviderClient client =
                new RestProviderClient(new NetworkPolicy(host -> List.of(InetAddress.getByName("203.0.113.10"))));

        RestProviderClient.Response response = client.get(profile, request, profile.buildUri("options", Map.of()));
        assertEquals("json", response.format());
        assertEquals("Basic dXNlcjpwYXNzd29yZA==", authorization.get());

        assertThrows(ProviderException.class, () -> client.get(profile, request, profile.buildUri("text", Map.of())));
        assertThrows(
                ProviderException.class, () -> client.get(profile, request, profile.buildUri("redirect", Map.of())));
    }

    @Test
    void jenkinsProxyCannotBypassDestinationPolicy(JenkinsRule jenkins) throws Exception {
        AtomicInteger proxyRequests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            proxyRequests.incrementAndGet();
            byte[] body = "[]".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        jenkins.jenkins.setProxy(
                new ProxyConfiguration("127.0.0.1", server.getAddress().getPort()));
        FreeStyleProject job = jenkins.createFreeStyleProject("proxy-client");
        ProviderRequest request = new ProviderRequest(
                job,
                "proxy",
                "digest",
                ProviderScope.GLOBAL,
                "jenkins",
                Map.of(),
                ProviderOperation.RESOLVE,
                Instant.now().plusSeconds(5),
                "builder");

        ConnectionProfile allowed = new ConnectionProfile("allowed", "http://service.example/api/");
        allowed.setAllowedPathPrefixes(List.of("/api/"));
        RestProviderClient client =
                new RestProviderClient(new NetworkPolicy(host -> List.of(InetAddress.getByName("203.0.113.10"))));
        assertEquals(
                "json",
                client.get(allowed, request, allowed.buildUri("options", Map.of()))
                        .format());
        assertEquals(1, proxyRequests.get());

        ConnectionProfile prohibited = new ConnectionProfile("prohibited", "http://127.0.0.1/api/");
        prohibited.setAllowedPathPrefixes(List.of("/api/"));
        assertThrows(ProviderException.class, () -> new RestProviderClient()
                .get(prohibited, request, prohibited.buildUri("options", Map.of())));
        assertEquals(1, proxyRequests.get());
    }
}
