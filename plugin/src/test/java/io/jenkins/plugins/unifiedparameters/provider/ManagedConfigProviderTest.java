package io.jenkins.plugins.unifiedparameters.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cloudbees.hudson.plugins.folder.Folder;
import hudson.model.FreeStyleProject;
import io.jenkins.plugins.unifiedparameters.evaluation.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jenkinsci.plugins.configfiles.GlobalConfigFiles;
import org.jenkinsci.plugins.configfiles.folder.FolderConfigFileProperty;
import org.jenkinsci.plugins.configfiles.json.JsonConfig;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ManagedConfigProviderTest {

    @Test
    void resolvesManagedConfigThroughContextApiAndHonorsFolderOverride(JenkinsRule jenkins) throws Exception {
        GlobalConfigFiles.get()
                .save(new JsonConfig(
                        "release-options", "Global releases", "", "[{\"value\":\"global\",\"label\":\"Global\"}]"));

        Folder team = jenkins.createProject(Folder.class, "team");
        team.addProperty(new FolderConfigFileProperty(List.of(
                new JsonConfig("release-options", "Team releases", "", "[{\"value\":\"team\",\"label\":\"Team\"}]"))));
        FreeStyleProject teamJob = team.createProject(FreeStyleProject.class, "build");
        FreeStyleProject rootJob = jenkins.createFreeStyleProject("root-build");
        ManagedConfigProviderDefinition provider = new ManagedConfigProviderDefinition("managed", "release-options");

        assertEquals("team", provider.load(context(teamJob, provider)).get(0).getValue());
        assertEquals("global", provider.load(context(rootJob, provider)).get(0).getValue());

        jenkins.jenkins.save();
        assertEquals("team", provider.load(context(teamJob, provider)).get(0).getValue());
    }

    @Test
    void missingManagedConfigFailsClosedWithoutControllerPathFallback(JenkinsRule jenkins) throws Exception {
        FreeStyleProject job = jenkins.createFreeStyleProject("missing-config");
        ManagedConfigProviderDefinition provider = new ManagedConfigProviderDefinition("managed", "/etc/passwd");

        ProviderException exception =
                assertThrows(ProviderException.class, () -> provider.load(context(job, provider)));

        assertEquals(ErrorCode.PROVIDER_UNAVAILABLE, exception.getCode());
    }

    private static ProviderLoadContext context(FreeStyleProject job, ManagedConfigProviderDefinition provider) {
        return new ProviderLoadContext(
                new ProviderRequest(
                        job,
                        provider.getId(),
                        provider.configurationDigest(),
                        ProviderScope.GLOBAL,
                        "jenkins",
                        Map.of(),
                        ProviderOperation.RESOLVE,
                        Instant.now().plusSeconds(5),
                        "builder"),
                Optional.empty());
    }
}
