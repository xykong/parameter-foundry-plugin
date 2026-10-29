package io.jenkins.plugins.parameterfoundry.companion.history;

import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

/** Bounded parser for StashManager's fixed native ustar/GZIP single-file format. */
final class HistoricalStashArchive {
    static long octal(byte[] b, int start, int length) throws IOException {
        long value = 0;
        boolean ended = false, started = false;
        for (int i = start; i < start + length; i++) {
            int c = b[i] & 255;
            if (c == 0 || c == 32) {
                if (started) ended = true;
                continue;
            }
            if (ended || c < '0' || c > '7' || value > (Long.MAX_VALUE - 7) / 8)
                throw new IOException("Invalid TAR numeric field");
            started = true;
            value = value * 8 + c - '0';
        }
        return value;
    }

    static void extract(
            InputStream source,
            Path destination,
            String expected,
            long fileLimit,
            long expandedLimit,
            long compressedLimit,
            long durationNanos)
            throws IOException {
        Path target = null;
        boolean created = false;
        try (InputStream owned = source) {
            if (expected == null || !expected.matches("[A-Za-z][A-Za-z0-9._-]{2,50}"))
                throw new IOException("Invalid expected stash entry");
            if (fileLimit < 0 || expandedLimit < 0 || compressedLimit < 0 || durationNanos <= 0)
                throw new IllegalArgumentException("Invalid archive budget");
            if (!Files.isDirectory(destination, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Owned destination required");
            long started = System.nanoTime();
            InputStream timed = new FilterInputStream(owned) {
                void check() throws IOException {
                    if (Thread.currentThread().isInterrupted())
                        throw new InterruptedIOException("Interrupted archive read");
                    if (System.nanoTime() - started > durationNanos)
                        throw new IOException("Archive time budget exceeded");
                }

                @Override
                public int read() throws IOException {
                    check();
                    return in.read();
                }

                @Override
                public int read(byte[] b, int o, int n) throws IOException {
                    check();
                    return in.read(b, o, n);
                }
            };
            try (InputStream expanded = HistoricalFileStreams.limit(
                    new StrictGzip(HistoricalFileStreams.limit(timed, compressedLimit)), expandedLimit)) {
                byte[] header = expanded.readNBytes(512);
                if (header.length != 512) throw new IOException("Truncated TAR header");
                long checksum = 0;
                for (int i = 0; i < 512; i++) checksum += (i >= 148 && i < 156) ? 32 : (header[i] & 255);
                if (checksum != octal(header, 148, 8)) throw new IOException("TAR header checksum mismatch");
                byte[] name = expected.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
                for (int i = 0; i < 100; i++)
                    if (header[i] != (i < name.length ? name[i] : 0))
                        throw new IOException("Unexpected TAR entry path");
                if (header[156] != 0 && header[156] != '0') throw new IOException("Non-regular TAR entry");
                for (int i = 157; i < 257; i++) if (header[i] != 0) throw new IOException("Unexpected TAR link target");
                byte[] magic = {'u', 's', 't', 'a', 'r', 0, '0', '0'};
                for (int i = 0; i < magic.length; i++)
                    if (header[257 + i] != magic[i]) throw new IOException("Unsupported TAR format");
                for (int i = 345; i < 512; i++)
                    if (header[i] != 0) throw new IOException("Unsupported TAR prefix/extension");
                long size = octal(header, 124, 12);
                if (size > fileLimit) throw new IOException("Stash file budget exceeded");
                target = destination.resolve(expected);
                try (OutputStream out =
                        Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                    created = true;
                    byte[] buffer = new byte[8192];
                    long remaining = size;
                    while (remaining > 0) {
                        int n = expanded.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                        if (n < 0) throw new IOException("Truncated stash file");
                        out.write(buffer, 0, n);
                        remaining -= n;
                    }
                }
                long padding = (512 - size % 512) % 512;
                for (long i = 0; i < padding; i++)
                    if (expanded.read() != 0) throw new IOException("Invalid TAR padding");
                long tail = 0;
                byte[] tailBuffer = new byte[8192];
                for (int n; (n = expanded.read(tailBuffer)) != -1; ) {
                    for (int i = 0; i < n; i++)
                        if (tailBuffer[i] != 0) throw new IOException("Unexpected TAR tail or extra entry");
                    tail += n;
                }
                if (tail < 1024 || tail % 512 != 0) throw new IOException("Incomplete TAR end records");
            }
        } catch (IOException | RuntimeException | Error failure) {
            if (created) {
                Path ownedTarget = target;
                HistoryStreamCleanup.attempt(failure, () -> Files.deleteIfExists(ownedTarget));
            }
            throw failure;
        }
    }

    /** One member only, native fixed header, complete CRC/ISIZE and physical EOF. */
    private static final class StrictGzip extends InputStream {
        final PushbackInputStream input;
        final Inflater inflater;
        final CRC32 crc = new CRC32();
        final byte[] compressed = new byte[8192];
        long count;
        int last;
        boolean finished;

        StrictGzip(InputStream source) throws IOException {
            input = new PushbackInputStream(source, compressed.length);
            inflater = new Inflater(true);
            try {
                byte[] h = input.readNBytes(10);
                if (h.length != 10 || (h[0] & 255) != 31 || (h[1] & 255) != 139 || h[2] != 8 || h[3] != 0)
                    throw new IOException("Unsupported native GZIP header");
            } catch (IOException | RuntimeException | Error failure) {
                inflater.end();
                throw failure;
            }
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            return read(one, 0, 1) < 0 ? -1 : one[0] & 255;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            java.util.Objects.checkFromIndexSize(off, len, b.length);
            if (len == 0) return 0;
            if (finished) return -1;
            try {
                while (true) {
                    int n = inflater.inflate(b, off, len);
                    if (n > 0) {
                        crc.update(b, off, n);
                        count += n;
                        return n;
                    }
                    if (inflater.finished()) {
                        int remaining = inflater.getRemaining();
                        if (remaining > 0) input.unread(compressed, last - remaining, remaining);
                        byte[] trailer = input.readNBytes(8);
                        if (trailer.length != 8
                                || little(trailer, 0) != crc.getValue()
                                || little(trailer, 4) != (count & 0xffffffffL))
                            throw new IOException("GZIP trailer mismatch");
                        if (input.read() != -1) throw new IOException("Trailing compressed data or extra GZIP member");
                        finished = true;
                        return -1;
                    }
                    if (inflater.needsDictionary()) throw new IOException("Unsupported GZIP dictionary");
                    if (inflater.needsInput()) {
                        last = input.read(compressed);
                        if (last < 0) throw new IOException("Truncated GZIP stream");
                        inflater.setInput(compressed, 0, last);
                    } else throw new IOException("GZIP decoder made no progress");
                }
            } catch (DataFormatException error) {
                throw new IOException("Invalid GZIP stream", error);
            }
        }

        static long little(byte[] b, int o) {
            return (b[o] & 255L) | ((b[o + 1] & 255L) << 8) | ((b[o + 2] & 255L) << 16) | ((b[o + 3] & 255L) << 24);
        }

        @Override
        public void close() throws IOException {
            HistoryStreamCleanup.close(input, inflater::end);
        }
    }
}
