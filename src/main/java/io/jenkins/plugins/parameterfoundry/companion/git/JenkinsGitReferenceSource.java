package io.jenkins.plugins.parameterfoundry.companion.git;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import hudson.EnvVars;
import hudson.model.TaskListener;
import hudson.security.ACL;
import io.jenkins.plugins.parameterfoundry.evaluation.ErrorCode;
import io.jenkins.plugins.parameterfoundry.provider.ConnectionProfile;
import io.jenkins.plugins.parameterfoundry.provider.ProviderException;
import io.jenkins.plugins.parameterfoundry.provider.ProviderLoadContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.GitClient.ConfigLevel;

final class JenkinsGitReferenceSource implements GitReferenceSource {

    private final String refType;

    JenkinsGitReferenceSource(String refType) {
        this.refType = refType;
    }

    @Override
    public Map<String, String> fetch(ProviderLoadContext context) {
        ConnectionProfile profile = context.connectionProfile()
                .orElseThrow(() -> unavailable(context, "Git ref Connection Profile is unavailable."));
        if (!profile.getHeaders().isEmpty()) {
            throw unavailable(context, "Git ref Provider does not accept custom HTTP headers.");
        }
        if (profile.isFollowRedirects()) {
            throw unavailable(context, "Git ref Provider requires redirects to be disabled.");
        }
        java.net.URI repository = profile.getBaseUri();
        profile.requireAllowedTarget(repository);
        context.checkCancelled();
        Path worktree = null;
        try {
            Path temporaryRoot = Jenkins.get().getRootDir().toPath().resolve("parameter-foundry/git-refs");
            Files.createDirectories(temporaryRoot);
            worktree = Files.createTempDirectory(temporaryRoot, "ls-remote-");
            GitClient client = Git.with(TaskListener.NULL, new EnvVars())
                    .in(worktree.toFile())
                    .using("jgit")
                    .getClient();
            client.init();
            client.config(ConfigLevel.LOCAL, "http.followRedirects", "false");
            if (profile.isUseJenkinsProxy()) {
                client.setProxy(Jenkins.get().proxy);
            }
            StandardUsernamePasswordCredentials credentials = findCredentials(profile, context, repository);
            if (credentials != null) {
                client.addCredentials(repository.toASCIIString(), credentials);
            }
            boolean headsOnly = "branch".equals(refType);
            boolean tagsOnly = "tag".equals(refType);
            Map<String, org.eclipse.jgit.lib.ObjectId> refs =
                    client.getRemoteReferences(repository.toASCIIString(), null, headsOnly, tagsOnly);
            return refs.entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> entry.getValue() == null
                                    ? ""
                                    : entry.getValue().name(),
                            (left, right) -> left,
                            java.util.LinkedHashMap::new));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable(context, "Git ref resolution was interrupted.");
        } catch (ProviderException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw unavailable(context, "Git ref resolution failed.");
        } finally {
            deleteTree(worktree);
        }
    }

    private static StandardUsernamePasswordCredentials findCredentials(
            ConnectionProfile profile, ProviderLoadContext context, java.net.URI repository) {
        if (profile.getCredentialId() == null || profile.getCredentialId().isBlank()) {
            return null;
        }
        StandardUsernamePasswordCredentials credential = CredentialsProvider.findCredentialByIdInItem(
                profile.getCredentialId(),
                StandardUsernamePasswordCredentials.class,
                context.request().job(),
                ACL.SYSTEM2,
                URIRequirementBuilder.fromUri(repository.toString()).build());
        if (credential == null) {
            throw unavailable(context, "Git ref Connection Profile credential is unavailable.");
        }
        return credential;
    }

    private static void deleteTree(Path root) {
        if (root == null) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Temporary cleanup is best effort and never leaks repository content to the response.
                }
            });
        } catch (IOException ignored) {
            // Temporary cleanup is best effort.
        }
    }

    private static ProviderException unavailable(ProviderLoadContext context, String message) {
        return new ProviderException(
                ErrorCode.PROVIDER_UNAVAILABLE, context.request().providerId(), message);
    }
}
