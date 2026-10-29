package io.jenkins.plugins.parameterfoundry.companion.history;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import hudson.model.Run;
import hudson.util.io.ArchiverFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.util.HashSet;
import java.util.Set;
import jenkins.model.StandardArtifactManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HistoricalStashFileTest {
    @TempDir
    Path temporary;

    Run<?, ?> run;
    Path root;
    Path extractionParent;

    @BeforeEach
    void setup() throws Exception {
        root = temporary.toRealPath();
        try (var directory = Files.newDirectoryStream(root)) {
            org.junit.jupiter.api.Assumptions.assumeTrue(directory instanceof SecureDirectoryStream<?>);
        }
        Path probe = Files.createTempDirectory("pf-temp-root-probe-");
        extractionParent = probe.getParent();
        Files.delete(probe);
        run = mock(Run.class);
        when(run.getArtifactManager()).thenReturn(new StandardArtifactManager(run));
        when(run.getRootDir()).thenReturn(root.toFile());
        Files.createDirectory(root.resolve("stashes"));
    }

    private Path nativeStash(String key, String entry, byte[] data) throws Exception {
        Path source = Files.write(root.resolve("source"), data);
        var bytes = new ByteArrayOutputStream();
        try (var archive = ArchiverFactory.TARGZ.create(bytes, StandardCharsets.UTF_8)) {
            archive.visit(source.toFile(), entry);
        }
        return Files.write(root.resolve("stashes/" + key + ".tar.gz"), bytes.toByteArray());
    }

    private Set<Path> extractions() throws IOException {
        try (var paths = Files.list(extractionParent)) {
            return new HashSet<>(
                    paths.filter(path -> path.getFileName().toString().startsWith("pf-history-file-"))
                            .toList());
        }
    }

    @Test
    void actualCoreArchivesUseNativeFallbackNameAndCleanupOnClose() throws Exception {
        byte[] expected = {0, 1, -1, 0, 10, 13};
        for (String key : new String[] {"UPLOAD", "F"}) {
            Path archive = nativeStash(key, key.equals("F") ? "fileparam" : key, expected);
            byte[] sourceBytes = Files.readAllBytes(archive);
            Set<Path> before = extractions();
            try (var stream = HistoricalStashFile.open(run, key, expected.length, 65536, 65536, 1_000_000_000L)) {
                assertArrayEquals(expected, stream.readAllBytes());
                Set<Path> added = extractions();
                added.removeAll(before);
                assertEquals(1, added.size());
            }
            assertEquals(before, extractions());
            assertArrayEquals(sourceBytes, Files.readAllBytes(archive));
        }
    }

    @Test
    void malformedOrOversizedStashLeavesNoOwnedExtraction() throws Exception {
        Set<Path> before = extractions();
        Files.write(root.resolve("stashes/UPLOAD.tar.gz"), new byte[] {1, 2, 3});
        assertThrows(IOException.class, () -> HistoricalStashFile.open(run, "UPLOAD", 3, 65536, 65536, 1_000_000_000L));
        assertEquals(before, extractions());
        nativeStash("UPLOAD", "UPLOAD", new byte[4]);
        assertThrows(IOException.class, () -> HistoricalStashFile.open(run, "UPLOAD", 3, 65536, 65536, 1_000_000_000L));
        assertEquals(before, extractions());
    }
}
