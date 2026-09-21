package io.jenkins.plugins.parameterfoundry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

class RequirementManifestTest {

    private static final Set<String> RELEASES = Set.of("0.1", "1.0", "1.1", "1.2", "2.0");

    @Test
    void manifestIsUniquePlannedAndMappedToImplementationStatus() throws IOException {
        Path repository = Path.of("..").toAbsolutePath().normalize();
        Path manifest = repository.resolve("docs/testing/requirements.yaml");
        Path releaseOneStatus = repository.resolve("docs/testing/1.0-implementation-status.md");
        Path releaseOneOneStatus = repository.resolve("docs/testing/1.1-implementation-status.md");
        Path releaseOneTwoStatus = repository.resolve("docs/testing/1.2-implementation-status.md");
        Path releaseTwoStatus = repository.resolve("docs/testing/2.0-implementation-status.md");
        assertTrue(Files.isRegularFile(manifest), manifest.toString());
        assertTrue(Files.isRegularFile(releaseOneStatus), releaseOneStatus.toString());
        assertTrue(Files.isRegularFile(releaseOneOneStatus), releaseOneOneStatus.toString());
        assertTrue(Files.isRegularFile(releaseOneTwoStatus), releaseOneTwoStatus.toString());
        assertTrue(Files.isRegularFile(releaseTwoStatus), releaseTwoStatus.toString());

        LoaderOptions options = new LoaderOptions();
        options.setMaxAliasesForCollections(0);
        options.setNestingDepthLimit(20);
        Map<?, ?> document = new Yaml(new SafeConstructor(options)).load(Files.readString(manifest));
        Object rawRequirements = document.get("requirements");
        assertTrue(rawRequirements instanceof List<?>);

        Set<String> ids = new HashSet<>();
        Set<String> releaseOneIds = new HashSet<>();
        Set<String> releaseOneOneIds = new HashSet<>();
        Set<String> releaseOneTwoIds = new HashSet<>();
        Set<String> releaseTwoIds = new HashSet<>();
        for (Object rawRequirement : (List<?>) rawRequirements) {
            assertTrue(rawRequirement instanceof Map<?, ?>);
            Map<?, ?> requirement = (Map<?, ?>) rawRequirement;
            String id = String.valueOf(requirement.get("id"));
            String release = String.valueOf(requirement.get("release"));
            assertTrue(ids.add(id), "Duplicate requirement ID: " + id);
            assertTrue(RELEASES.contains(release), "Unknown release for " + id + ": " + release);
            assertNotNull(requirement.get("summary"), id);
            Object plannedTests = requirement.get("plannedTests");
            assertTrue(plannedTests instanceof List<?>, id);
            assertFalse(((List<?>) plannedTests).isEmpty(), id);
            if ("1.0".equals(release)) {
                releaseOneIds.add(id);
            } else if ("1.1".equals(release)) {
                releaseOneOneIds.add(id);
            } else if ("1.2".equals(release)) {
                releaseOneTwoIds.add(id);
            } else if ("2.0".equals(release)) {
                releaseTwoIds.add(id);
            }
        }

        String implementationStatus = Files.readString(releaseOneStatus);
        for (String id : releaseOneIds) {
            assertEquals(
                    1,
                    count(implementationStatus, "| " + id + " |"),
                    "Requirement must appear exactly once in the 1.0 status table: " + id);
        }
        String providerStatus = Files.readString(releaseOneOneStatus);
        for (String id : releaseOneOneIds) {
            assertEquals(
                    1,
                    count(providerStatus, "| " + id + " |"),
                    "Requirement must appear exactly once in the 1.1 status table: " + id);
        }
        String reactiveStatus = Files.readString(releaseOneTwoStatus);
        for (String id : releaseOneTwoIds) {
            assertEquals(
                    1,
                    count(reactiveStatus, "| " + id + " |"),
                    "Requirement must appear exactly once in the 1.2 status table: " + id);
        }
        String completeStatus = Files.readString(releaseTwoStatus);
        for (String id : releaseTwoIds) {
            assertEquals(
                    1,
                    count(completeStatus, "| " + id + " |"),
                    "Requirement must appear exactly once in the 2.0 status table: " + id);
        }
    }

    private static int count(String text, String value) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(value, offset)) >= 0) {
            count++;
            offset += value.length();
        }
        return count;
    }
}
