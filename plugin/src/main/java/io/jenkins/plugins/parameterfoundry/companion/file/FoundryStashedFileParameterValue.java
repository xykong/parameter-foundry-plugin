/*
 * The MIT License
 *
 * Copyright 2020 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package io.jenkins.plugins.parameterfoundry.companion.file;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.ParametersAction;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.queue.QueueListener;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.apache.commons.fileupload2.core.FileItem;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.workflow.flow.StashManager;
import org.kohsuke.stapler.DataBoundConstructor;

/** Uploads have opaque IDs; retained content belongs to a build, never a caller-supplied path. */
public class FoundryStashedFileParameterValue extends AbstractFoundryFileParameterValue {
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(FoundryStashedFileParameterValue.class.getName());
    private long ingestionLimit = FileUploadService.MAX_STASHED_BYTES;
    private volatile String uploadId;
    private volatile String storedBuildId;
    private transient String preparedStashBuildId;
    private transient volatile boolean queueOwned;

    @DataBoundConstructor
    public FoundryStashedFileParameterValue(String name, FileItem file) throws IOException {
        super(name);
        try (InputStream in = file.getInputStream()) {
            store(in);
            setFilename(file.getName());
        } finally {
            file.delete();
        }
    }

    FoundryStashedFileParameterValue(String name, InputStream src) throws IOException {
        this(name, src, FileUploadService.MAX_STASHED_BYTES);
    }

    protected FoundryStashedFileParameterValue(String name, InputStream src, long limit) throws IOException {
        super(name);
        ingestionLimit = FileUploadService.checkLimit(limit, false);
        store(src);
    }

    private void store(InputStream src) throws IOException {
        uploadId = UUID.randomUUID().toString();
        FileUploadOwnership.track(this);
        File dir = uploadDirectory();
        Files.createDirectories(dir.toPath());
        try (OutputStream out = Files.newOutputStream(new File(dir, safeName()).toPath())) {
            FileUploadService.copy(src, out, effectiveIngestionLimit());
        } catch (IOException | RuntimeException e) {
            try {
                FileUtils.deleteDirectory(dir);
            } finally {
                FileUploadOwnership.released(uploadId);
            }
            throw e;
        }
    }

    private File uploadDirectory() throws IOException {
        if (uploadId == null || !UUID.fromString(uploadId).toString().equals(uploadId)) {
            throw new IOException("Invalid upload identifier.");
        }
        return new File(new File(Jenkins.get().getRootDir(), "parameter-foundry-uploads"), uploadId);
    }

    private File retainedDirectory(Run<?, ?> build) throws IOException {
        if (uploadId == null || !UUID.fromString(uploadId).toString().equals(uploadId)) {
            throw new IOException("Invalid upload identifier.");
        }
        return new File(new File(build.getRootDir(), "parameter-foundry-files"), uploadId);
    }

    @Override
    public synchronized void buildEnvironment(Run<?, ?> build, EnvVars env) {
        super.buildEnvironment(build, env);
        try {
            long limit = FileUploadService.limitFor(build, this, effectiveIngestionLimit());
            File target = retainedDirectory(build);
            File content = new File(target, safeName());
            if (!content.isFile()) {
                File source;
                if (storedBuildId == null) {
                    source = new File(uploadDirectory(), safeName());
                } else {
                    Run<?, ?> original = Run.fromExternalizableId(storedBuildId);
                    if (original == null || original.getParent() != build.getParent()) {
                        throw new IOException("The original upload build is unavailable or belongs to another Job.");
                    }
                    source = new File(retainedDirectory(original), safeName());
                }
                if (Files.size(source.toPath()) > limit) {
                    throw new IOException("Retained upload exceeds the current Job limit.");
                }
                copyRetained(source, target, content);
            }
            if (Files.size(content.toPath()) > limit) {
                throw new IOException("Upload exceeds the current Job limit.");
            }
            if (usesStash() && !build.getExternalizableId().equals(preparedStashBuildId)) {
                StashManager.stash(
                        build,
                        name,
                        new FilePath(target),
                        new Launcher.LocalLauncher(TaskListener.NULL),
                        env,
                        TaskListener.NULL,
                        safeName(),
                        null,
                        false,
                        false);
                preparedStashBuildId = build.getExternalizableId();
            }
            if (storedBuildId == null) {
                discardPending();
            }
            storedBuildId = build.getExternalizableId();
        } catch (IOException e) {
            if (storedBuildId == null) {
                try {
                    discardPending();
                } catch (IOException cleanup) {
                    e.addSuppressed(cleanup);
                }
            }
            throw new IllegalStateException("Cannot prepare uploaded file for the build.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted preparing uploaded file.", e);
        }
    }

    @Override
    protected InputStream open(Run<?, ?> build) throws IOException {
        if (build == null) {
            throw new IOException("A build is required to read a stashed upload.");
        }
        return Files.newInputStream(new File(retainedDirectory(build), safeName()).toPath());
    }

    @Override
    protected void validateForUse(Run<?, ?> build) throws IOException {
        if (build == null
                || Files.size(new File(retainedDirectory(build), safeName()).toPath())
                        > FileUploadService.limitFor(build, this, effectiveIngestionLimit())) {
            throw new IOException("Upload exceeds the current Job limit or has no build context.");
        }
    }

    protected FoundryStashedFileParameterValue(String name, String uploadId, String storedBuildId) {
        super(name);
        this.uploadId = uploadId;
        this.storedBuildId = storedBuildId;
    }

    @Override
    protected AbstractFoundryFileParameterValue copyForRebuild(Run<?, ?> build) throws IOException {
        if (!new File(retainedDirectory(build), safeName()).isFile()) {
            throw new IOException("The original upload is no longer retained.");
        }
        var copy = newRebuildValue(uploadId, build.getExternalizableId());
        copy.ingestionLimit = effectiveIngestionLimit();
        copy.setFilename(getFilename());
        copy.setDescription(getDescription());
        return copy;
    }

    protected boolean usesStash() {
        return true;
    }

    protected FoundryStashedFileParameterValue newRebuildValue(String id, String buildId) {
        return new FoundryStashedFileParameterValue(name, id, buildId);
    }

    private long effectiveIngestionLimit() {
        return ingestionLimit > 0 ? ingestionLimit : FileUploadService.MAX_STASHED_BYTES;
    }

    @Extension
    public static final class CancelledQueueListener extends QueueListener {
        @Override
        public void onLeft(Queue.LeftItem item) {
            if (!item.isCancelled()) {
                return;
            }
            for (ParametersAction action : item.getActions(ParametersAction.class)) {
                for (var parameter : action.getAllParameters()) {
                    if (parameter instanceof FoundryStashedFileParameterValue value && value.storedBuildId == null) {
                        try {
                            value.discardPending();
                        } catch (IOException | IllegalArgumentException e) {
                            LOGGER.log(Level.WARNING, "Cannot clean cancelled Foundry file upload.", e);
                        }
                    }
                }
            }
        }
    }

    String pendingUploadId() {
        return uploadId;
    }

    void markQueued() {
        queueOwned = true;
        if (storedBuildId == null) FileUploadOwnership.claimed(uploadId);
    }

    synchronized void discardIfUnclaimed() throws IOException {
        if (!queueOwned && storedBuildId == null) discardPending();
    }

    synchronized void discardPending() throws IOException {
        try {
            FileUtils.deleteDirectory(uploadDirectory());
        } finally {
            FileUploadOwnership.released(uploadId);
        }
    }

    synchronized boolean retainUnusedUpload(Run<?, ?> run) throws IOException {
        if (storedBuildId != null) return false;
        File target = retainedDirectory(run);
        File content = new File(target, safeName());
        if (!content.isFile()) copyRetained(new File(uploadDirectory(), safeName()), target, content);
        storedBuildId = run.getExternalizableId();
        discardPending();
        return true;
    }

    private static void copyRetained(File source, File directory, File content) throws IOException {
        Files.createDirectories(directory.toPath());
        var staging = Files.createTempFile(directory.toPath(), "retaining-", ".tmp");
        try {
            Files.copy(source.toPath(), staging, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Files.move(
                    staging,
                    content.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(staging);
        }
    }

    @Override
    public boolean equals(Object other) {
        return super.equals(other)
                && other instanceof FoundryStashedFileParameterValue value
                && java.util.Objects.equals(uploadId, value.uploadId);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(super.hashCode(), uploadId);
    }
}
