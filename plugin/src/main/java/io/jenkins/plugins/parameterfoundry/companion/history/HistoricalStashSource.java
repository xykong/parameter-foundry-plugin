package io.jenkins.plugins.parameterfoundry.companion.history;

import hudson.AbortException;
import hudson.model.Run;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import jenkins.model.Jenkins;
import jenkins.model.StandardArtifactManager;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/** Fixed Run-owned local stash source; callers must authorize the Run and reader state first. */
@Restricted(NoExternalUse.class)
public final class HistoricalStashSource {
    private HistoricalStashSource() {}

    /** No remote fallback or serialized temporary path is accepted. The caller owns the returned stream. */
    public static InputStream open(Run<?, ?> run, String name) throws IOException {
        if (run == null) throw new IOException("Build context is required");
        if (run.getArtifactManager().getClass() != StandardArtifactManager.class) {
            throw new IOException("Historical stash backend requires a bounded adapter");
        }
        Jenkins.checkGoodName(name);
        if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) throw new IOException("Invalid stash key");
        Path archive = run.getRootDir()
                .toPath()
                .resolve("stashes")
                .resolve(name + ".tar.gz")
                .toAbsolutePath();
        if (!archive.equals(archive.normalize())) throw new IOException("Non-canonical build storage path");
        Path filesystemRoot = archive.getRoot();
        if (filesystemRoot == null) throw new IOException("Historical storage has no filesystem root");
        List<DirectoryStream<Path>> handles = new ArrayList<>();
        InputStream result = null;
        Throwable failure = null;
        try {
            DirectoryStream<Path> initial = Files.newDirectoryStream(filesystemRoot);
            handles.add(initial);
            if (!(initial instanceof SecureDirectoryStream<Path> directory)) {
                throw new IOException("Secure historical storage lookup unavailable");
            }
            for (int i = 0; i < archive.getNameCount() - 1; i++) {
                directory = directory.newDirectoryStream(archive.getName(i), LinkOption.NOFOLLOW_LINKS);
                handles.add(directory);
            }
            Path leaf = archive.getFileName();
            var view = directory.getFileAttributeView(leaf, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
            if (view == null) throw new IOException("Historical storage attributes are unavailable");
            var attributes = view.readAttributes();
            if (!attributes.isRegularFile()) throw new IOException("Historical stash archive is not a regular file");
            result = Channels.newInputStream(
                    directory.newByteChannel(leaf, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)));
            return result;
        } catch (IOException | RuntimeException | Error error) {
            failure = error;
            if (error instanceof NoSuchFileException) {
                var absent = new AbortException("No such saved stash");
                absent.initCause(error);
                failure = absent;
                throw absent;
            }
            throw error;
        } finally {
            Throwable cleanup = null;
            for (int i = handles.size() - 1; i >= 0; i--) {
                DirectoryStream<Path> handle = handles.get(i);
                cleanup = HistoryStreamCleanup.attempt(cleanup, handle::close);
            }
            if (cleanup != null) {
                if (failure != null) {
                    if (failure != cleanup) failure.addSuppressed(cleanup);
                } else {
                    if (result != null) cleanup = HistoryStreamCleanup.attempt(cleanup, result::close);
                    HistoryStreamCleanup.rethrow(cleanup);
                }
            }
        }
    }
}
