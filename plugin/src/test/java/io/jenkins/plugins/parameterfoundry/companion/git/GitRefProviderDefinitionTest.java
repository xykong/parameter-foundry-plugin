package io.jenkins.plugins.parameterfoundry.companion.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jenkins.plugins.parameterfoundry.evaluation.ErrorCode;
import io.jenkins.plugins.parameterfoundry.provider.ConnectionProfile;
import io.jenkins.plugins.parameterfoundry.provider.ProfileHeader;
import io.jenkins.plugins.parameterfoundry.provider.ProviderException;
import io.jenkins.plugins.parameterfoundry.provider.ProviderLoadContext;
import io.jenkins.plugins.parameterfoundry.provider.ProviderOperation;
import io.jenkins.plugins.parameterfoundry.provider.ProviderRequest;
import io.jenkins.plugins.parameterfoundry.provider.ProviderScope;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GitRefProviderDefinitionTest {

    private static final Map<String, String> REFS = refs();

    @Test
    void branchesAreShortFilteredSortedAndBounded() {
        GitRefProviderDefinition provider = new GitRefProviderDefinition("branches", "git", ignored -> REFS);
        provider.setRefType("branch");
        provider.setIncludePattern("(main|release/.+)");
        provider.setExcludePattern("release/old");
        provider.setSortOrder("descending");

        assertEquals(
                List.of("release/2.0", "main"),
                provider.load(context(provider)).stream()
                        .map(option -> option.getValue())
                        .toList());
    }

    @Test
    void tagsAndFullRefsUseExplicitCanonicalValues() {
        GitRefProviderDefinition tags = new GitRefProviderDefinition("tags", "git", ignored -> REFS);
        tags.setRefType("tag");
        assertEquals(
                List.of("v1.0.0", "v2.0.0"),
                tags.load(context(tags)).stream()
                        .map(option -> option.getValue())
                        .toList());

        GitRefProviderDefinition refs = new GitRefProviderDefinition("refs", "git", ignored -> REFS);
        refs.setRefType("ref");
        refs.setIncludePattern("refs/changes/.+");
        assertEquals(
                List.of("refs/changes/42"),
                refs.load(context(refs)).stream()
                        .map(option -> option.getValue())
                        .toList());
    }

    @Test
    void smartSortOrdersCompareNumericRunsByValue() {
        Map<String, String> versions = new LinkedHashMap<>();
        versions.put("refs/heads/1.9", "a");
        versions.put("refs/heads/1.10", "b");
        versions.put("refs/heads/1.2", "c");

        GitRefProviderDefinition ascending = new GitRefProviderDefinition("versions", "git", ignored -> versions);
        ascending.setRefType("branch");
        ascending.setSortOrder("smart-ascending");
        assertEquals(
                List.of("1.2", "1.9", "1.10"),
                ascending.load(context(ascending)).stream()
                        .map(option -> option.getValue())
                        .toList());

        GitRefProviderDefinition descending = new GitRefProviderDefinition("versions", "git", ignored -> versions);
        descending.setRefType("branch");
        descending.setSortOrder("smart-descending");
        assertEquals(
                List.of("1.10", "1.9", "1.2"),
                descending.load(context(descending)).stream()
                        .map(option -> option.getValue())
                        .toList());

        assertTrue(GitRefProviderDefinition.compareSmart("release-01", "release-1") > 0);
        assertEquals(0, GitRefProviderDefinition.compareSmart("release-01", "release-1a"));
        assertEquals(0, GitRefProviderDefinition.compareSmart("release-١", "release-1"));
        assertThrows(IllegalArgumentException.class, () -> ascending.setSortOrder("sideways"));
    }

    @Test
    void rejectsInvalidConfigurationAndOversizedResults() {
        GitRefProviderDefinition provider = new GitRefProviderDefinition("branches", "git", ignored -> REFS);
        assertThrows(IllegalArgumentException.class, () -> provider.setRefType("commit"));
        assertThrows(IllegalArgumentException.class, () -> provider.setIncludePattern("["));
        assertThrows(IllegalArgumentException.class, () -> provider.setMaximumRefs(0));

        provider.setMaximumRefs(1);
        ProviderException exception = assertThrows(ProviderException.class, () -> provider.load(context(provider)));
        assertEquals(ErrorCode.PROVIDER_UNAVAILABLE, exception.getCode());
    }

    @Test
    void transportRejectsRedirectsCustomHeadersAndMetadataTargetsBeforeGitRuns() {
        JenkinsGitReferenceSource source = new JenkinsGitReferenceSource("branch");

        ConnectionProfile redirect = new ConnectionProfile("redirect", "https://example.test/repository.git");
        redirect.setFollowRedirects(true);
        assertThrows(ProviderException.class, () -> source.fetch(context("redirect", redirect)));

        ConnectionProfile headers = new ConnectionProfile("headers", "https://example.test/repository.git");
        headers.setHeaders(List.of(new ProfileHeader("X-Example", "value")));
        assertThrows(ProviderException.class, () -> source.fetch(context("headers", headers)));

        ConnectionProfile metadata = new ConnectionProfile("metadata", "https://169.254.169.254/repository.git");
        metadata.setAllowPrivateAddresses(true);
        assertThrows(ProviderException.class, () -> source.fetch(context("metadata", metadata)));
    }

    private static ProviderLoadContext context(GitRefProviderDefinition provider) {
        return new ProviderLoadContext(
                new ProviderRequest(
                        null,
                        provider.getId(),
                        provider.configurationDigest(),
                        ProviderScope.GLOBAL,
                        "jenkins",
                        Map.of(),
                        ProviderOperation.RESOLVE,
                        Instant.now().plusSeconds(5),
                        "test"),
                Optional.empty());
    }

    private static ProviderLoadContext context(String providerId, ConnectionProfile profile) {
        return new ProviderLoadContext(
                new ProviderRequest(
                        null,
                        providerId,
                        "sha256:test",
                        ProviderScope.GLOBAL,
                        "jenkins",
                        Map.of(),
                        ProviderOperation.RESOLVE,
                        Instant.now().plusSeconds(5),
                        "test"),
                Optional.of(profile));
    }

    private static Map<String, String> refs() {
        Map<String, String> refs = new LinkedHashMap<>();
        refs.put("HEAD", "1");
        refs.put("refs/heads/main", "1");
        refs.put("refs/heads/release/2.0", "2");
        refs.put("refs/heads/release/old", "3");
        refs.put("refs/tags/v1.0.0", "4");
        refs.put("refs/tags/v2.0.0", "5");
        refs.put("refs/tags/v2.0.0^{}", "5");
        refs.put("refs/changes/42", "6");
        return refs;
    }
}
