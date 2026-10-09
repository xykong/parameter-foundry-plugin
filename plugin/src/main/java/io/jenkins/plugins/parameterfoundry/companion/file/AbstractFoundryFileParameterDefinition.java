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
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;

public abstract class AbstractFoundryFileParameterDefinition extends ParameterDefinition {

    protected AbstractFoundryFileParameterDefinition(String name) {
        super(name);
        FileUploadService.checkName(name);
    }

    protected Object readResolve() {
        FileUploadService.checkName(getName());
        return this;
    }

    protected abstract Class<? extends AbstractFoundryFileParameterValue> valueType();

    protected abstract AbstractFoundryFileParameterValue createValue(String name, InputStream src) throws IOException;

    @Override
    public ParameterValue createValue(StaplerRequest2 req, JSONObject jo) {
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
        Run<?, ?> run = req.findAncestorObject(Run.class);
        if (run == null) {
            return null;
        }
        run.getParent().checkPermission(Item.BUILD);
        var action = run.getAction(ParametersAction.class);
        ParameterValue value = action == null ? null : action.getParameter(getName());
        if (value != null && valueType().isInstance(value)) {
            return ((AbstractFoundryFileParameterValue) value).copyForRebuild(run);
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
        AbstractFoundryFileParameterValue p;
        if (value.isEmpty()) {
            p = createValue(getName(), command.stdin);
        } else {
            byte[] data = FileUploadService.decode(value);
            p = createValue(getName(), new ByteArrayInputStream(data));
        }
        p.setDescription(getDescription());
        return p;
    }

    protected abstract static class AbstractFoundryFileParameterDefinitionDescriptor extends ParameterDescriptor {

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
