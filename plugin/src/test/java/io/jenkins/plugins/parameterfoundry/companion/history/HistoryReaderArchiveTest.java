package io.jenkins.plugins.parameterfoundry.companion.history;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HistoryReaderArchiveTest {
    @TempDir
    Path temporary;

    @Test
    void concurrentFamiliesCanCreateTheFreshManagedDirectory() throws Exception {
        byte[] git = HistoryReaderArchive.packagedBytes();
        byte[] extended = HistoryReaderArchive.packagedBytes(HistoryReaderArchive.Family.EXTENDED_CHOICE);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 10; round++) {
                Path root = temporary.resolve("concurrent-" + round);
                var barrier = new java.util.concurrent.CyclicBarrier(2);
                var gitFuture = executor.submit(() -> {
                    barrier.await();
                    return HistoryReaderArchive.retain(root, git);
                });
                var extendedFuture = executor.submit(() -> {
                    barrier.await();
                    return HistoryReaderArchive.retain(root, extended, HistoryReaderArchive.Family.EXTENDED_CHOICE);
                });
                assertArrayEquals(git, Files.readAllBytes(gitFuture.get(15, java.util.concurrent.TimeUnit.SECONDS)));
                assertArrayEquals(
                        extended, Files.readAllBytes(extendedFuture.get(15, java.util.concurrent.TimeUnit.SECONDS)));
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void incompletePluginManifestIsRejectedBeforeExposure() throws Exception {
        byte[] bytes = HistoryReaderArchive.packagedBytes(HistoryReaderArchive.Family.EXTENDED_CHOICE);
        var rewritten = new ByteArrayOutputStream();
        try (var input = new java.util.jar.JarInputStream(new java.io.ByteArrayInputStream(bytes));
                var output = new ZipOutputStream(rewritten)) {
            var manifest = input.getManifest();
            manifest.getMainAttributes().remove(new java.util.jar.Attributes.Name("Plugin-Version"));
            output.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            manifest.write(output);
            output.closeEntry();
            for (var entry = input.getNextJarEntry(); entry != null; entry = input.getNextJarEntry()) {
                if (entry.isDirectory()) continue;
                output.putNextEntry(new ZipEntry(entry.getName()));
                input.transferTo(output);
                output.closeEntry();
            }
        }
        byte[] incomplete = rewritten.toByteArray();
        assertThrows(
                IOException.class,
                () -> HistoryReaderArchive.verify(
                        incomplete,
                        HistoryReaderArchive.digest(incomplete),
                        HistoryReaderArchive.Family.EXTENDED_CHOICE));
    }

    @Test
    void familyBundlesRejectSubstitutionAndRetainIndependently() throws Exception {
        byte[] git = HistoryReaderArchive.packagedBytes();
        byte[] extended = HistoryReaderArchive.packagedBytes(HistoryReaderArchive.Family.EXTENDED_CHOICE);
        assertThrows(
                IOException.class,
                () -> HistoryReaderArchive.verify(
                        git, HistoryReaderArchive.digest(git), HistoryReaderArchive.Family.EXTENDED_CHOICE));
        assertThrows(
                IOException.class, () -> HistoryReaderArchive.verify(extended, HistoryReaderArchive.digest(extended)));
        Path root = temporary.resolve("families");
        Path gitPath = HistoryReaderArchive.retain(root, git);
        Path extendedPath = HistoryReaderArchive.retain(root, extended, HistoryReaderArchive.Family.EXTENDED_CHOICE);
        assertNotEquals(gitPath, extendedPath);
        assertArrayEquals(git, Files.readAllBytes(gitPath));
        assertArrayEquals(extended, Files.readAllBytes(extendedPath));
    }

    @Test
    void verifiedPackagedJarIsRetainedAndReusedWithoutReplacingAlteredData() throws Exception {
        byte[] bytes = HistoryReaderArchive.packagedBytes();
        Path root = temporary.resolve("managed");
        Path retained = HistoryReaderArchive.retain(root, bytes);
        assertArrayEquals(bytes, Files.readAllBytes(retained));
        assertEquals(retained, HistoryReaderArchive.retain(root, bytes));
        Files.write(retained, new byte[] {1});
        assertThrows(IOException.class, () -> HistoryReaderArchive.retain(root, bytes));
        assertArrayEquals(new byte[] {1}, Files.readAllBytes(retained));
    }

    @Test
    void checksumAndEntryBoundsRejectUnexpectedAndExpandedContent() throws Exception {
        byte[] bytes = HistoryReaderArchive.packagedBytes();
        assertThrows(IOException.class, () -> HistoryReaderArchive.verify(bytes, "0".repeat(64)));
        assertThrows(IOException.class, () -> HistoryReaderArchive.verify(new byte[32769], "0".repeat(64)));
        byte[] unexpected = zip("unexpected.class", new byte[] {1});
        assertThrows(
                IOException.class,
                () -> HistoryReaderArchive.verify(unexpected, HistoryReaderArchive.digest(unexpected)));
        byte[] expanded = zip("META-INF/MANIFEST.MF", new byte[32769]);
        assertTrue(expanded.length < 32768);
        assertThrows(
                IOException.class, () -> HistoryReaderArchive.verify(expanded, HistoryReaderArchive.digest(expanded)));
        byte[] directory = zip("META-INF/", new byte[32769]);
        assertThrows(
                IOException.class,
                () -> HistoryReaderArchive.verify(directory, HistoryReaderArchive.digest(directory)));
    }

    @Test
    void symlinkStorageAndArchiveAreRejectedWithoutChangingTheirTargets() throws Exception {
        byte[] bytes = HistoryReaderArchive.packagedBytes();
        Path real = Files.createDirectory(temporary.resolve("real"));
        Path link = temporary.resolve("link");
        try {
            Files.createSymbolicLink(link, real);
        } catch (UnsupportedOperationException | IOException unavailable) {
            org.junit.jupiter.api.Assumptions.abort("Symbolic links unavailable on this platform");
        }
        assertThrows(IOException.class, () -> HistoryReaderArchive.retain(link, bytes));
        Path root = Files.createDirectory(temporary.resolve("owned"));
        Path victim = temporary.resolve("victim");
        Files.writeString(victim, "unchanged");
        Files.createSymbolicLink(root.resolve(HistoryReaderArchive.digest(bytes) + ".jar"), victim);
        assertThrows(IOException.class, () -> HistoryReaderArchive.retain(root, bytes));
        assertEquals("unchanged", Files.readString(victim));
    }

    private static byte[] zip(String name, byte[] content) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry(name));
            zip.write(content);
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }
}
