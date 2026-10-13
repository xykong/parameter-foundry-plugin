package io.jenkins.plugins.parameterfoundry.companion.history;

import static org.junit.jupiter.api.Assertions.*;

import hudson.util.io.ArchiverFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HistoricalStashArchiveTest {
    @TempDir
    Path temporary;

    private byte[] archive(String[] names, byte type, byte[] data) throws Exception {
        Path source = Files.createTempFile(temporary, "native-source-", ".bin");
        Files.write(source, data);
        var bytes = new ByteArrayOutputStream();
        try (var tar = ArchiverFactory.TARGZ.create(bytes, StandardCharsets.UTF_8)) {
            for (String ignored : names) tar.visit(source.toFile(), "UPLOAD");
        }
        byte[] raw;
        try (var input = new GZIPInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            raw = input.readAllBytes();
        }
        int offset = 0;
        for (String name : names) {
            Arrays.fill(raw, offset, offset + 100, (byte) 0);
            byte[] encoded = name.getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(encoded, 0, raw, offset, encoded.length);
            raw[offset + 156] = type;
            if (type == '1' || type == '2') {
                byte[] link = "/outside".getBytes(StandardCharsets.US_ASCII);
                System.arraycopy(link, 0, raw, offset + 157, link.length);
            }
            Arrays.fill(raw, offset + 148, offset + 156, (byte) 32);
            long checksum = 0;
            for (int i = offset; i < offset + 512; i++) checksum += raw[i] & 255;
            byte[] octal = String.format("%06o", checksum).getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(octal, 0, raw, offset + 148, 6);
            raw[offset + 154] = 0;
            raw[offset + 155] = 32;
            offset += 512 + ((data.length + 511) / 512) * 512;
        }
        Files.delete(source);
        return gzip(raw);
    }

    private byte[] gzip(byte[] raw) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(bytes)) {
            gzip.write(raw);
        }
        return bytes.toByteArray();
    }

    private byte[] append(byte[] first, byte[] last) {
        byte[] bytes = Arrays.copyOf(first, first.length + last.length);
        System.arraycopy(last, 0, bytes, first.length, last.length);
        return bytes;
    }

    private void reject(byte[] data, long file, long expanded, long compressed, long duration) throws Exception {
        Path destination = Files.createTempDirectory(temporary, "rejected-");
        assertThrows(
                IOException.class,
                () -> HistoricalStashArchive.extract(
                        new ByteArrayInputStream(data), destination, "UPLOAD", file, expanded, compressed, duration));
        try (var files = Files.list(destination)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void regularUstarContentRestoresUnderExactFileBudget() throws Exception {
        byte[] data = {0, 1, -1, 0, 10, 13};
        byte[] archive = archive(new String[] {"UPLOAD"}, (byte) '0', data);
        HistoricalStashArchive.extract(
                new ByteArrayInputStream(archive), temporary, "UPLOAD", data.length, 65536, 65536, 1_000_000_000L);
        assertArrayEquals(data, Files.readAllBytes(temporary.resolve("UPLOAD")));
    }

    @Test
    void wrongNamesLinksSpecialTypesAndAdditionalEntriesLeaveNoFile() throws Exception {
        for (String name : new String[] {"../UPLOAD", "/UPLOAD", "other", "dir/UPLOAD"}) {
            reject(archive(new String[] {name}, (byte) '0', new byte[3]), 3, 65536, 65536, 1_000_000_000L);
        }
        for (byte type : new byte[] {'1', '2', '3', '4', '5', '6', '9'}) {
            reject(archive(new String[] {"UPLOAD"}, type, new byte[0]), 3, 65536, 65536, 1_000_000_000L);
        }
        for (String extra : new String[] {"UPLOAD", "other"}) {
            reject(archive(new String[] {"UPLOAD", extra}, (byte) '0', new byte[3]), 3, 65536, 65536, 1_000_000_000L);
        }
    }

    @Test
    void quotasTruncationAndCompressedTrailersAreEnforced() throws Exception {
        byte[] archive = archive(new String[] {"UPLOAD"}, (byte) '0', new byte[3]);
        reject(archive, 2, 65536, 65536, 1_000_000_000L);
        reject(archive, 3, 512, 65536, 1_000_000_000L);
        reject(archive, 3, 65536, 1, 1_000_000_000L);
        reject(archive, 3, 65536, 65536, 1);
        reject(Arrays.copyOf(archive, archive.length / 2), 3, 65536, 65536, 1_000_000_000L);
        reject(Arrays.copyOf(archive, archive.length - 4), 3, 65536, 65536, 1_000_000_000L);
        byte[] corrupt = archive.clone();
        corrupt[corrupt.length - 8] ^= 1;
        reject(corrupt, 3, 65536, 65536, 1_000_000_000L);
        reject(append(archive, new byte[] {1}), 3, 65536, 65536, 1_000_000_000L);
        reject(append(archive, gzip(new byte[1024])), 3, 65536, 65536, 1_000_000_000L);
        byte[] raw;
        try (var input = new GZIPInputStream(new ByteArrayInputStream(archive))) {
            raw = input.readAllBytes();
        }
        reject(gzip(append(raw, new byte[] {1})), 3, 65536, 65536, 1_000_000_000L);
        reject(gzip(append(raw, new byte[100_000])), 3, 65536, 65536, 1_000_000_000L);
    }

    @Test
    void earlyValidationOwnsSourceAndInterruptPreservesFlag() throws Exception {
        class Source extends ByteArrayInputStream {
            boolean closed;

            Source() {
                super(new byte[0]);
            }

            @Override
            public void close() {
                closed = true;
            }
        }
        var source = new Source();
        assertThrows(
                IOException.class,
                () -> HistoricalStashArchive.extract(source, temporary, "../invalid", 10, 10, 10, 100));
        assertTrue(source.closed);
        byte[] archive = archive(new String[] {"UPLOAD"}, (byte) '0', new byte[3]);
        Thread.currentThread().interrupt();
        try {
            reject(archive, 3, 65536, 65536, 1_000_000_000L);
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
}
