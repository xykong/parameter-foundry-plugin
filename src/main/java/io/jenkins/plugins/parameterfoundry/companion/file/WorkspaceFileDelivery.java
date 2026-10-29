package io.jenkins.plugins.parameterfoundry.companion.file;

import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import jenkins.MasterToSlaveFileCallable;

/** Stages bytes on the selected agent and installs only below that workspace. */
final class WorkspaceFileDelivery {
    private WorkspaceFileDelivery() {}

    static void place(FilePath workspace, String relativePath, InputStream content)
            throws IOException, InterruptedException {
        String path = FileUploadService.checkWorkspacePath(relativePath);
        workspace.mkdirs();
        FilePath staging = workspace.createTempFile("foundry-upload-", ".tmp");
        try {
            staging.copyFrom(content);
            workspace.act(new Install(path, staging.getName()));
        } finally {
            staging.delete();
        }
    }

    private static final class Install extends MasterToSlaveFileCallable<Void> {
        private static final long serialVersionUID = 1L;
        private final String relativePath;
        private final String stagingName;

        Install(String relativePath, String stagingName) {
            this.relativePath = relativePath;
            this.stagingName = stagingName;
        }

        @Override
        public Void invoke(File workspace, VirtualChannel channel) throws IOException {
            Path root = workspace.toPath().toRealPath();
            Path target = root.resolve(relativePath).normalize();
            if (!target.startsWith(root) || target.equals(root)) {
                throw new IOException("File destination escapes the workspace.");
            }
            Path cursor = root;
            Path relative = root.relativize(target);
            for (int i = 0; i < relative.getNameCount(); i++) {
                cursor = cursor.resolve(relative.getName(i));
                if (Files.isSymbolicLink(cursor)) {
                    throw new IOException("Symbolic links are not allowed in upload destinations.");
                }
                if (i < relative.getNameCount() - 1) {
                    if (!Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)) {
                        Files.createDirectory(cursor);
                    }
                    if (!Files.isDirectory(cursor, LinkOption.NOFOLLOW_LINKS)
                            || !cursor.toRealPath().startsWith(root)) {
                        throw new IOException("File destination is outside the workspace or not a directory.");
                    }
                }
            }
            if (Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("File destination is a directory.");
            }
            Files.move(
                    root.resolve(stagingName),
                    target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            return null;
        }
    }
}
