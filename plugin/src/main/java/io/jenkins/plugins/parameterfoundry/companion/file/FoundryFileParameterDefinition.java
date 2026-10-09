package io.jenkins.plugins.parameterfoundry.companion.file;

import hudson.Extension;
import hudson.model.Failure;
import hudson.util.FormValidation;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

/** One file parameter entry with three explicit delivery contracts. */
public final class FoundryFileParameterDefinition extends AbstractFoundryFileParameterDefinition {
    private static final long serialVersionUID = 1L;
    private String deliveryMode;
    private String workspacePath;

    @DataBoundConstructor
    public FoundryFileParameterDefinition(String name) {
        super(name);
    }

    public String getDeliveryMode() {
        String mode = deliveryMode == null ? "workspace" : deliveryMode;
        if (!Set.of("workspace", "base64", "stash").contains(mode)) {
            throw new Failure("Unsupported file delivery mode.");
        }
        return mode;
    }

    @DataBoundSetter
    public void setDeliveryMode(String deliveryMode) {
        if (deliveryMode == null || !Set.of("workspace", "base64", "stash").contains(deliveryMode)) {
            throw new Failure("File delivery mode must be workspace, base64 or stash.");
        }
        this.deliveryMode = deliveryMode;
        validateParameterName(getName());
        FileUploadService.checkLimit(getMaxUploadBytes(), "base64".equals(deliveryMode));
    }

    public String getWorkspacePath() {
        return workspacePath == null || workspacePath.isBlank() ? getName() : workspacePath;
    }

    @DataBoundSetter
    public void setWorkspacePath(String workspacePath) {
        this.workspacePath = workspacePath == null || workspacePath.isBlank()
                ? null
                : FileUploadService.checkWorkspacePath(workspacePath);
    }

    @Override
    protected void validateParameterName(String name) {
        if ("workspace".equals(getDeliveryMode())) {
            FileUploadService.checkParameterName(name);
            FileUploadService.checkWorkspacePath(getWorkspacePath());
        } else {
            FileUploadService.checkName(name);
        }
    }

    @Override
    protected Class<? extends AbstractFoundryFileParameterValue> valueType() {
        return switch (getDeliveryMode()) {
            case "base64" -> FoundryBase64FileParameterValue.class;
            case "stash" -> FoundryStashedFileParameterValue.class;
            default -> FoundryWorkspaceFileParameterValue.class;
        };
    }

    @Override
    protected AbstractFoundryFileParameterValue createValue(String name, InputStream src) throws IOException {
        return switch (getDeliveryMode()) {
            case "base64" -> new FoundryBase64FileParameterValue(name, src, getMaxUploadBytes());
            case "stash" -> new FoundryStashedFileParameterValue(name, src, getMaxUploadBytes());
            default -> new FoundryWorkspaceFileParameterValue(name, src, getMaxUploadBytes(), getWorkspacePath());
        };
    }

    @Symbol("foundryFile")
    @Extension
    public static final class DescriptorImpl extends AbstractFoundryFileParameterDefinitionDescriptor {
        @Override
        public String getDisplayName() {
            return "Parameter Foundry File";
        }

        @Override
        public FormValidation doCheckName(@QueryParameter String name) {
            try {
                FileUploadService.checkParameterName(name);
                return FormValidation.ok();
            } catch (Failure e) {
                return FormValidation.error(e.getMessage());
            }
        }

        public FormValidation doCheckWorkspacePath(@QueryParameter String value) {
            if (value == null || value.isBlank()) {
                return FormValidation.ok();
            }
            try {
                FileUploadService.checkWorkspacePath(value);
                return FormValidation.ok();
            } catch (Failure e) {
                return FormValidation.error(e.getMessage());
            }
        }
    }
}
