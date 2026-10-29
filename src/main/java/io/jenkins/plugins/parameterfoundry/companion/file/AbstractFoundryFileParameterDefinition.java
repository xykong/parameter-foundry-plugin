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

import hudson.cli.CLICommand;
import hudson.model.Failure;
import hudson.model.Item;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Run;
import hudson.util.FormValidation;
import jakarta.servlet.ServletException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import net.sf.json.JSONObject;
import org.apache.commons.fileupload2.core.FileItem;
import org.apache.commons.fileupload2.core.FileUploadContentTypeException;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;

public abstract class AbstractFoundryFileParameterDefinition extends ParameterDefinition {
    private static final long serialVersionUID = 1L;

    private long maxUploadBytes;

    protected AbstractFoundryFileParameterDefinition(String name) {
        super(name);
        validateParameterName(name);
    }

    protected Object readResolve() {
        validateConfiguration();
        return this;
    }

    public void validateConfiguration() {
        validateParameterName(getName());
        if (maxUploadBytes < 0) {
            throw new Failure("Upload limit cannot be negative.");
        }
        FileUploadService.checkLimit(getMaxUploadBytes(), valueType() == FoundryBase64FileParameterValue.class);
    }

    protected void validateParameterName(String name) {
        FileUploadService.checkName(name);
    }

    public long getMaxUploadBytes() {
        return maxUploadBytes > 0
                ? maxUploadBytes
                : valueType() == FoundryBase64FileParameterValue.class
                        ? FileUploadService.MAX_BASE64_BYTES
                        : FileUploadService.MAX_STASHED_BYTES;
    }

    public long getConfiguredMaxUploadBytes() {
        return maxUploadBytes;
    }

    @DataBoundSetter
    public void setMaxUploadBytes(long maxUploadBytes) {
        this.maxUploadBytes = maxUploadBytes == 0
                ? 0
                : FileUploadService.checkLimit(maxUploadBytes, valueType() == FoundryBase64FileParameterValue.class);
    }

    protected abstract Class<? extends AbstractFoundryFileParameterValue> valueType();

    protected abstract AbstractFoundryFileParameterValue createValue(String name, InputStream src) throws IOException;

    @Override
    public ParameterValue createValue(StaplerRequest2 req, JSONObject jo) {
        validateConfiguration();
        boolean input = req.getAncestors().stream()
                .anyMatch(a -> a.getObject()
                        .getClass()
                        .getName()
                        .equals("org.jenkinsci.plugins.workflow.support.steps.input.InputStepExecution"));
        if (input && valueType() != FoundryBase64FileParameterValue.class) {
            throw new Failure("Only Base64 file delivery supports Pipeline input uploads.");
        }
        if (!getName().equals(jo.optString("name"))) {
            throw new Failure("File parameter name does not match its definition.");
        }
        try {
            FileItem src = req.getFileItem2(jo.optString("file", "file"));
            if (src == null || src.getName() == null || src.getName().isEmpty()) {
                return historicalValue(req);
            }
            return fromUpload(src);
        } catch (ServletException | IOException e) {
            throw new Failure("Cannot read uploaded file: " + e.getMessage());
        }
    }

    /** Only a Rebuild URL's authorized ancestor may supply a historical value. */
    private ParameterValue historicalValue(StaplerRequest2 req) throws IOException {
        boolean rebuild = req.getAncestors().stream()
                .anyMatch(a -> a.getObject().getClass().getName().equals("com.sonyericsson.rebuild.RebuildAction"));
        if (!rebuild) {
            return null;
        }
        Run<?, ?> run = req.findAncestorObject(Run.class);
        if (run == null) {
            return null;
        }
        run.getParent().checkPermission(Item.BUILD);
        var action = run.getAction(ParametersAction.class);
        ParameterValue value = action == null ? null : action.getParameter(getName());
        if (value != null && valueType() == value.getClass()) {
            var copy = ((AbstractFoundryFileParameterValue) value).copyForRebuild(run);
            try (InputStream in = copy.open(run)) {
                FileUploadService.copy(in, java.io.OutputStream.nullOutputStream(), getMaxUploadBytes());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted validating retained upload.", e);
            }
            if (copy instanceof FoundryWorkspaceFileParameterValue workspace
                    && this instanceof FoundryFileParameterDefinition file) {
                workspace.setWorkspacePath(file.getWorkspacePath());
            }
            return copy;
        }
        if (value != null) {
            throw new IOException("The file delivery mode or legacy file type changed; upload a replacement file.");
        }
        return null;
    }

    private ParameterValue fromUpload(FileItem src) throws IOException {
        String filename = src.getName();
        try (InputStream in = src.getInputStream()) {
            AbstractFoundryFileParameterValue value = createValue(getName(), in);
            value.setDescription(getDescription());
            value.setFilename(filename);
            return value;
        } finally {
            src.delete();
        }
    }

    @Override
    public ParameterValue createValue(StaplerRequest2 req) {
        validateConfiguration();
        try {
            FileItem src;
            try {
                src = req.getFileItem2(getName());
            } catch (ServletException e) {
                if (e.getCause() instanceof FileUploadContentTypeException) {
                    return null;
                }
                throw e;
            }
            return src == null || src.getName() == null || src.getName().isEmpty() ? null : fromUpload(src);
        } catch (ServletException | IOException e) {
            throw new Failure("Cannot read uploaded file: " + e.getMessage());
        }
    }

    @Override
    public ParameterValue createValue(CLICommand command, String value) throws IOException, InterruptedException {
        validateConfiguration();
        AbstractFoundryFileParameterValue p;
        if (value.isEmpty()) {
            p = createValue(getName(), command.stdin);
        } else {
            byte[] data = FileUploadService.decode(value, Math.min(getMaxUploadBytes(), (Integer.MAX_VALUE / 4L) * 3));
            p = createValue(getName(), new ByteArrayInputStream(data));
        }
        p.setDescription(getDescription());
        if (p instanceof FoundryWorkspaceFileParameterValue) {
            p.setFilename(value.isEmpty() ? "stdin" : "upload");
        }
        return p;
    }

    protected abstract static class AbstractFoundryFileParameterDefinitionDescriptor extends ParameterDescriptor {

        public FormValidation doCheckMaxUploadBytes(@QueryParameter long value) {
            return value >= 0
                    ? FormValidation.ok()
                    : FormValidation.error("Use 0 for the transport default or a positive byte limit.");
        }

        public FormValidation doCheckName(@QueryParameter String name) {
            try {
                FileUploadService.checkName(name);
                return FormValidation.ok();
            } catch (Failure x) {
                return FormValidation.error(x.getMessage());
            }
        }
    }
}
