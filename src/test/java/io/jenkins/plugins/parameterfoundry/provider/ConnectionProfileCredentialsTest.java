package io.jenkins.plugins.parameterfoundry.provider;

import static org.junit.jupiter.api.Assertions.*;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.User;
import hudson.security.ACL;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ConnectionProfileCredentialsTest {
    @Test
    void administratorCanSelectCredentialsAndRetainUnavailableSavedIds(JenkinsRule j) throws Exception {
        SystemCredentialsProvider.getInstance()
                .getCredentials()
                .add(new UsernamePasswordCredentialsImpl(
                        CredentialsScope.GLOBAL, "synthetic-basic", "Synthetic test", "test-user", "test-value"));
        var descriptor = j.jenkins.getDescriptorByType(ConnectionProfile.DescriptorImpl.class);
        var values =
                descriptor.doFillCredentialIdItems(null, "saved-unavailable", "https://example.invalid/api/").stream()
                        .map(option -> option.value)
                        .collect(Collectors.toSet());
        assertTrue(values.containsAll(Set.of("", "synthetic-basic", "saved-unavailable")));
    }

    @Test
    void unauthorizedUsersCannotEnumerateGlobalOrJobCredentials(JenkinsRule j) throws Exception {
        var job = j.createFreeStyleProject();
        SystemCredentialsProvider.getInstance()
                .getCredentials()
                .add(new UsernamePasswordCredentialsImpl(
                        CredentialsScope.GLOBAL, "synthetic-hidden", "Synthetic test", "test-user", "test-value"));
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(jenkins.model.Jenkins.READ)
                .everywhere()
                .to("reader"));
        var descriptor = j.jenkins.getDescriptorByType(ConnectionProfile.DescriptorImpl.class);
        try (var ignored = ACL.as2(User.getById("reader", true).impersonate2())) {
            assertEquals(
                    Set.of("saved-id"),
                    descriptor.doFillCredentialIdItems(null, "saved-id", "https://example.invalid").stream()
                            .map(option -> option.value)
                            .collect(Collectors.toSet()));
            assertTrue(descriptor.doFillCredentialIdItems(job, "", "https://example.invalid").stream()
                    .noneMatch(option -> "synthetic-hidden".equals(option.value)));
        }
    }
}
