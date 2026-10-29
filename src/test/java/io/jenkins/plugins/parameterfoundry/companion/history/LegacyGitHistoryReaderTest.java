package io.jenkins.plugins.parameterfoundry.companion.history;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import hudson.ExtensionList;
import hudson.PluginManager;
import io.jenkins.plugins.parameterfoundry.provider.ParameterFoundryGlobalConfiguration;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class LegacyGitHistoryReaderTest {
    @Test
    void detectorFailureInvalidatesAndRetainsAcceptanceFailure(JenkinsRule j) throws Exception {
        ParameterFoundryGlobalConfiguration.get().setLegacyGitHistoryEnabled(true);
        var reader = spy(new LegacyGitHistoryReader());
        set(reader, "initialized", true);
        set(reader, "bootEnabled", true);
        set(reader, "state", LegacyGitHistoryReader.State.ACTIVE);
        set(reader, "readerClass", String.class);
        doThrow(new NoClassDefFoundError("Synthetic exporter linkage failure"))
                .when(reader)
                .hasOtherExporter(any(PluginManager.class));
        assertEquals(LegacyGitHistoryReader.State.ERROR, reader.getState());
        assertFalse(reader.isCompatibilityAccepted());
        assertTrue(reader.isActivated());
        assertEquals(
                "ERROR\n",
                Files.readString(
                        j.jenkins.getRootDir().toPath().resolve("parameter-foundry-history/git-reader-state.txt")));
        assertEquals(LegacyGitHistoryReader.State.ERROR, reader.getState());
    }

    // Populate only our own component state to exercise an already-activated
    // reader. No Core manager or classloader is reflected into or replaced.
    private static void set(LegacyGitHistoryReader reader, String name, Object value) throws Exception {
        var field = LegacyGitHistoryReader.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(reader, value);
    }

    @Test
    void freshInstallationKeepsReaderDisabledAndOptInRequiresRestart(JenkinsRule j) {
        var configuration = ParameterFoundryGlobalConfiguration.get();
        var reader = ExtensionList.lookupSingleton(LegacyGitHistoryReader.class);
        assertFalse(configuration.isLegacyGitHistoryEnabled());
        assertEquals(LegacyGitHistoryReader.State.DISABLED, reader.getState());
        assertFalse(reader.isActivated());
        assertFalse(reader.isCompatibilityAccepted());
        configuration.setLegacyGitHistoryEnabled(true);
        assertEquals(LegacyGitHistoryReader.State.RESTART_REQUIRED, reader.getState());
        assertTrue(reader.isActivated());
        assertFalse(reader.isCompatibilityAccepted());
        assertFalse(j.jenkins
                .getRootDir()
                .toPath()
                .resolve("parameter-foundry-history")
                .toFile()
                .exists());
        configuration.load();
        assertTrue(configuration.isLegacyGitHistoryEnabled());
        configuration.setLegacyGitHistoryEnabled(false);
        assertEquals(LegacyGitHistoryReader.State.RESTART_REQUIRED, reader.getState());
    }
}
