package io.jenkins.plugins.parameterfoundry.companion.history;

import hudson.model.Run;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/** Run-bound extraction with a private single-file lifetime; never consumes serialized temporary paths. */
@Restricted(NoExternalUse.class)
public final class HistoricalStashFile {
    private HistoricalStashFile() {}

    /** Call only after authorization and live reader-state checks. Closing deletes only the owned extraction. */
    public static InputStream open(
            Run<?, ?> run, String key, long fileLimit, long expandedLimit, long compressedLimit, long durationNanos)
            throws IOException {
        String entry = key != null && key.matches("[A-Za-z][A-Za-z0-9._-]{2,50}") ? key : "fileparam";
        Path directory = null;
        InputStream archive = HistoricalStashSource.open(run, key);
        boolean handedToParser = false;
        try {
            directory = Files.createTempDirectory("pf-history-file-");
            Path file = directory.resolve(entry);
            handedToParser = true;
            HistoricalStashArchive.extract(
                    archive, directory, entry, fileLimit, expandedLimit, compressedLimit, durationNanos);
            InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS);
            Path ownedDirectory = directory;
            return new FilterInputStream(input) {
                private boolean closed;

                @Override
                public synchronized void close() throws IOException {
                    if (closed) return;
                    closed = true;
                    HistoryStreamCleanup.close(in, () -> cleanup(file, ownedDirectory));
                }
            };
        } catch (IOException | RuntimeException | Error failure) {
            if (!handedToParser) HistoryStreamCleanup.attempt(failure, archive::close);
            if (directory != null) {
                Path ownedDirectory = directory;
                HistoryStreamCleanup.attempt(failure, () -> cleanup(ownedDirectory.resolve(entry), ownedDirectory));
            }
            throw failure;
        }
    }

    private static void cleanup(Path file, Path directory) throws IOException {
        Throwable failure = HistoryStreamCleanup.attempt(null, () -> Files.deleteIfExists(file));
        HistoryStreamCleanup.rethrow(HistoryStreamCleanup.attempt(failure, () -> Files.deleteIfExists(directory)));
    }
}
