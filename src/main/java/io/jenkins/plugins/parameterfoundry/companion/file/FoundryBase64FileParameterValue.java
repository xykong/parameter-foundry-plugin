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
import hudson.model.Run;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.apache.commons.fileupload2.core.FileItem;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

@Symbol("foundryBase64File")
public final class FoundryBase64FileParameterValue extends AbstractFoundryFileParameterValue {

    private static final long serialVersionUID = 1L;
    private String base64;
    private long ingestionLimit = FileUploadService.MAX_BASE64_BYTES;

    @DataBoundConstructor
    public FoundryBase64FileParameterValue(String name) throws IOException {
        super(name);
    }

    @DataBoundSetter
    public void setFile(FileItem file) throws IOException {
        try (InputStream in = file.getInputStream()) {
            base64 = FileUploadService.encode(in);
            setFilename(file.getName());
        } finally {
            file.delete();
        }
    }

    FoundryBase64FileParameterValue(String name, InputStream src) throws IOException {
        this(name, src, FileUploadService.MAX_BASE64_BYTES);
    }

    FoundryBase64FileParameterValue(String name, InputStream src, long limit) throws IOException {
        super(name);
        ingestionLimit = limit;
        base64 = FileUploadService.encode(src, limit);
    }

    @DataBoundSetter
    public void setBase64(String base64) throws IOException {
        FileUploadService.decode(base64, (Integer.MAX_VALUE / 4L) * 3);
        this.base64 = base64;
        ingestionLimit = (Integer.MAX_VALUE / 4L) * 3;
    }

    @Override
    public void buildEnvironment(Run<?, ?> build, EnvVars env) {
        super.buildEnvironment(build, env);
        try {
            FileUploadService.decode(base64, FileUploadService.limitFor(build, this, effectiveIngestionLimit()));
        } catch (IOException e) {
            throw new IllegalStateException("Base64 file violates the Job upload policy.", e);
        }
        env.put(name, base64);
    }

    @Override
    public Object getValue() {
        return base64;
    }

    @Override
    protected InputStream open(Run<?, ?> build) throws IOException {
        return new ByteArrayInputStream(FileUploadService.decode(base64, effectiveIngestionLimit()));
    }

    @Override
    protected AbstractFoundryFileParameterValue copyForRebuild(Run<?, ?> build) throws IOException {
        var copy = new FoundryBase64FileParameterValue(name);
        copy.setBase64(base64);
        copy.ingestionLimit = effectiveIngestionLimit();
        copy.setFilename(getFilename());
        copy.setDescription(getDescription());
        return copy;
    }

    @Override
    protected void validateForUse(Run<?, ?> build) throws IOException {
        FileUploadService.decode(base64, FileUploadService.limitFor(build, this, effectiveIngestionLimit()));
    }

    private long effectiveIngestionLimit() {
        return ingestionLimit > 0 ? ingestionLimit : FileUploadService.MAX_BASE64_BYTES;
    }

    @Override
    public boolean equals(Object other) {
        return super.equals(other)
                && other instanceof FoundryBase64FileParameterValue value
                && java.util.Objects.equals(base64, value.base64);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(super.hashCode(), base64);
    }
}
