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
public final class FoundryStashedFileParameterValue extends AbstractFoundryFileParameterValue {
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(FoundryStashedFileParameterValue.class.getName());
    private String uploadId;
    private String storedBuildId;

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
        super(name);
        store(src);
    }

    private void store(InputStream src) throws IOException {
        uploadId = UUID.randomUUID().toString();
        File dir = uploadDirectory();
        Files.createDirectories(dir.toPath());
        try (OutputStream out = Files.newOutputStream(new File(dir, safeName()).toPath())) {
            FileUploadService.copy(src, out, FileUploadService.MAX_STASHED_BYTES);
        } catch (IOException | RuntimeException e) {
            FileUtils.deleteDirectory(dir);
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
                Files.createDirectories(target.toPath());
                Files.copy(source.toPath(), content.toPath());
            }
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
            if (storedBuildId == null) {
                FileUtils.deleteDirectory(uploadDirectory());
            }
            storedBuildId = build.getExternalizableId();
        } catch (IOException e) {
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
    protected FilePath createTempFile(
            Run<?, ?> build, FilePath tempDir, EnvVars env, Launcher launcher, TaskListener listener)
            throws IOException, InterruptedException {
        FilePath target = tempDir.child(safeName());
        try (InputStream in = open(build)) {
            target.copyFrom(in);
        }
        return target;
    }

    private FoundryStashedFileParameterValue(String name, String uploadId, String storedBuildId) {
        super(name);
        this.uploadId = uploadId;
        this.storedBuildId = storedBuildId;
    }

    @Override
    protected AbstractFoundryFileParameterValue copyForRebuild(Run<?, ?> build) throws IOException {
        if (!new File(retainedDirectory(build), safeName()).isFile()) {
            throw new IOException("The original upload is no longer retained.");
        }
        var copy = new FoundryStashedFileParameterValue(name, uploadId, build.getExternalizableId());
        copy.setFilename(getFilename());
        copy.setDescription(getDescription());
        return copy;
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
                            FileUtils.deleteDirectory(value.uploadDirectory());
                        } catch (IOException | IllegalArgumentException e) {
                            LOGGER.log(Level.WARNING, "Cannot clean cancelled Foundry file upload.", e);
                        }
                    }
                }
            }
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
