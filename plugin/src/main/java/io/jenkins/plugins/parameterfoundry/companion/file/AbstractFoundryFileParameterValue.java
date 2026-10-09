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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.ParameterValue;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.apache.commons.io.IOUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.StaplerResponse2;

/**
 * Implement either {@link #open} and/or {@link #createTempFile}.
 */
public abstract class AbstractFoundryFileParameterValue extends ParameterValue {

    private @CheckForNull String filename;

    protected AbstractFoundryFileParameterValue(String name) {
        super(name);
        FileUploadService.checkName(name);
    }

    public final String getFilename() {
        return filename;
    }

    final void setFilename(String filename) {
        // FileItem.getName Javadoc claims Opera might pass a full path, so strip to just the name.
        // This is a best effort anyway (scripts should be written to tolerate an undefined name).
        this.filename = filename == null ? null : new File(filename.replace('\\', '/')).getName();
    }

    protected abstract InputStream open(@CheckForNull Run<?, ?> build) throws IOException, InterruptedException;

    protected abstract AbstractFoundryFileParameterValue copyForRebuild(Run<?, ?> build) throws IOException;

    protected FilePath createTempFile(
            @NonNull Run<?, ?> build,
            @NonNull FilePath tempDir,
            @NonNull EnvVars env,
            @NonNull Launcher launcher,
            @NonNull TaskListener listener)
            throws IOException, InterruptedException {
        FilePath f = tempDir.child(safeName());
        try (InputStream is = open(build)) {
            f.copyFrom(is);
        }
        return f;
    }

    protected String safeName() {
        FileUploadService.checkName(name);
        return name;
    }

    public void doDownload(@AncestorInPath Run<?, ?> build, StaplerResponse2 rsp) throws Exception {
        if (build == null) {
            throw new IOException("A build is required to download a file parameter.");
        }
        build.getParent().checkPermission(hudson.model.Item.READ);
        rsp.setHeader("Content-Disposition", "attachment");
        rsp.setHeader("X-Content-Type-Options", "nosniff");
        rsp.setContentType("application/octet-stream");
        try (InputStream is = open(build);
                OutputStream os = rsp.getOutputStream()) {
            IOUtils.copy(is, os);
        }
    }

    @Override
    public void buildEnvironment(Run<?, ?> build, EnvVars env) {
        String fname = getFilename();
        if (fname != null) {
            env.put(name + "_FILENAME", fname);
        }
    }
}
