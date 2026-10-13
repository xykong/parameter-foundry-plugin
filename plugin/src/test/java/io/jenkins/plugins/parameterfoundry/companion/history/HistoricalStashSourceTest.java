package io.jenkins.plugins.parameterfoundry.companion.history;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import hudson.AbortException;
import hudson.model.Run;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import jenkins.model.ArtifactManager;
import jenkins.model.StandardArtifactManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HistoricalStashSourceTest {
    @TempDir
    Path temporary;

    Path root;
    Run<?, ?> run;

    @BeforeEach
    void setup() throws Exception {
        root = temporary.toRealPath();
        run = mock(Run.class);
        when(run.getArtifactManager()).thenReturn(new StandardArtifactManager(run));
        when(run.getRootDir()).thenReturn(root.toFile());
    }

    private void requireSecureFilesystem() throws Exception {
        try (var stream = Files.newDirectoryStream(root)) {
            org.junit.jupiter.api.Assumptions.assumeTrue(stream instanceof SecureDirectoryStream<?>);
        }
    }

    @Test
    void readsOnlyTheSelectedBuildAndLeavesTheArchiveIntact() throws Exception {
        requireSecureFilesystem();
        Files.createDirectory(root.resolve("stashes"));
        Path archive = root.resolve("stashes/UPLOAD.tar.gz");
        byte[] expected = {0, 1, -1, 10, 13};
        Files.write(archive, expected);
        try (var input = HistoricalStashSource.open(run, "UPLOAD")) {
            assertArrayEquals(expected, input.readAllBytes());
        }
        assertArrayEquals(expected, Files.readAllBytes(archive));
        Files.delete(archive);
        Files.delete(root.resolve("stashes"));
    }

    @Test
    void unknownBackendCannotFallBackToLocalFiles() throws Exception {
        var remote = mock(ArtifactManager.class);
        when(run.getArtifactManager()).thenReturn(remote);
        assertThrows(IOException.class, () -> HistoricalStashSource.open(run, "UPLOAD"));
        verify(run, never()).getRootDir();
        verifyNoInteractions(remote);
    }

    @Test
    void standardBackendSubclassAlsoRequiresAnAdapter() throws Exception {
        var subclass = new StandardArtifactManager(run) {};
        when(run.getArtifactManager()).thenReturn(subclass);
        assertThrows(IOException.class, () -> HistoricalStashSource.open(run, "UPLOAD"));
        verify(run, never()).getRootDir();
    }

    @Test
    void missingStashPreservesAbortAndDoesNotCreateStorage() throws Exception {
        requireSecureFilesystem();
        assertThrows(AbortException.class, () -> HistoricalStashSource.open(run, "UPLOAD"));
        assertFalse(Files.exists(root.resolve("stashes")));
    }

    @Test
    void leafAndParentSymlinksAreRejectedWithoutReadingOrChangingCanary() throws Exception {
        requireSecureFilesystem();
        Path outside = Files.createDirectory(root.resolve("outside"));
        Path canary = Files.writeString(outside.resolve("UPLOAD.tar.gz"), "CANARY");
        Path stashes = Files.createDirectory(root.resolve("stashes"));
        Path leaf = Files.createSymbolicLink(stashes.resolve("UPLOAD.tar.gz"), canary);
        assertThrows(IOException.class, () -> HistoricalStashSource.open(run, "UPLOAD"));
        Files.delete(leaf);
        Files.delete(stashes);
        Files.createSymbolicLink(stashes, outside);
        assertThrows(IOException.class, () -> HistoricalStashSource.open(run, "UPLOAD"));
        assertEquals("CANARY", Files.readString(canary));
    }

    @Test
    void nonregularLeafAndNoncanonicalRootAreRejected() throws Exception {
        requireSecureFilesystem();
        Files.createDirectories(root.resolve("stashes/UPLOAD.tar.gz"));
        assertThrows(IOException.class, () -> HistoricalStashSource.open(run, "UPLOAD"));
        when(run.getRootDir()).thenReturn(root.resolve("other/..").toFile());
        assertThrows(IOException.class, () -> HistoricalStashSource.open(run, "UPLOAD"));
    }

    @Test
    void invalidKeyCannotEscapeTheBuildRoot() {
        for (String name : new String[] {"../UPLOAD", "/UPLOAD", "dir/UPLOAD", "dir\\UPLOAD", ".."}) {
            assertThrows(Exception.class, () -> HistoricalStashSource.open(run, name));
        }
        verify(run, never()).getRootDir();
        assertThrows(IOException.class, () -> HistoricalStashSource.open(null, "UPLOAD"));
    }
}
