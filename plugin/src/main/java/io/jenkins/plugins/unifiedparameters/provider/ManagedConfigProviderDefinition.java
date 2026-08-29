package io.jenkins.plugins.unifiedparameters.provider;

import hudson.Extension;
import hudson.model.Descriptor;
import io.jenkins.plugins.unifiedparameters.evaluation.ErrorCode;
import io.jenkins.plugins.unifiedparameters.model.Option;
import io.jenkins.plugins.unifiedparameters.model.OptionImportService;
import java.io.Serial;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.jenkinsci.Symbol;
import org.jenkinsci.lib.configprovider.model.Config;
import org.jenkinsci.plugins.configfiles.ConfigFiles;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public final class ManagedConfigProviderDefinition extends SharedProviderDefinition {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String configId;
    private String format = "auto";
    private String rootPath = "$";
    private String valuePath = "value";
    private String labelPath = "label";
    private String descriptionPath = "description";
    private String groupPath = "group";
    private String disabledPath = "disabled";
    private String disabledReasonPath = "disabledReason";
    private String tagsPath = "tags";

    @DataBoundConstructor
    public ManagedConfigProviderDefinition(String id, String configId) {
        super(id);
        if (configId == null || configId.isBlank()) {
            throw new IllegalArgumentException("Managed Config ID is required.");
        }
        this.configId = configId;
    }

    public String getConfigId() {
        return configId;
    }

    public String getFormat() {
        return format;
    }

    @DataBoundSetter
    public void setFormat(String format) {
        if (!java.util.Set.of("auto", "yaml", "json", "xml").contains(format)) {
            throw new IllegalArgumentException("Managed Config format must be auto, yaml, json, or xml.");
        }
        this.format = format;
    }

    public String getRootPath() {
        return rootPath;
    }

    @DataBoundSetter
    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }

    public String getValuePath() {
        return valuePath;
    }

    @DataBoundSetter
    public void setValuePath(String valuePath) {
        this.valuePath = valuePath;
    }

    public String getLabelPath() {
        return labelPath;
    }

    @DataBoundSetter
    public void setLabelPath(String labelPath) {
        this.labelPath = labelPath;
    }

    public String getDescriptionPath() {
        return descriptionPath;
    }

    @DataBoundSetter
    public void setDescriptionPath(String descriptionPath) {
        this.descriptionPath = descriptionPath;
    }

    public String getGroupPath() {
        return groupPath;
    }

    @DataBoundSetter
    public void setGroupPath(String groupPath) {
        this.groupPath = groupPath;
    }

    public String getDisabledPath() {
        return disabledPath;
    }

    @DataBoundSetter
    public void setDisabledPath(String disabledPath) {
        this.disabledPath = disabledPath;
    }

    public String getDisabledReasonPath() {
        return disabledReasonPath;
    }

    @DataBoundSetter
    public void setDisabledReasonPath(String disabledReasonPath) {
        this.disabledReasonPath = disabledReasonPath;
    }

    public String getTagsPath() {
        return tagsPath;
    }

    @DataBoundSetter
    public void setTagsPath(String tagsPath) {
        this.tagsPath = tagsPath;
    }

    @Override
    public List<Option> load(ProviderLoadContext context) {
        context.checkCancelled();
        Config config = ConfigFiles.getByIdOrNull(context.request().job(), configId);
        if (config == null) {
            throw new ProviderException(
                    ErrorCode.PROVIDER_UNAVAILABLE, getId(), "Managed Config is not visible to this Job.");
        }
        String content = config.content;
        String resolvedFormat = resolveFormat(content);
        if ("yaml".equals(resolvedFormat)) {
            return OptionImportService.parse(content);
        }
        return StructuredOptionParser.parse(
                content.getBytes(StandardCharsets.UTF_8),
                resolvedFormat,
                rootPath,
                valuePath,
                labelPath,
                descriptionPath,
                groupPath,
                disabledPath,
                disabledReasonPath,
                tagsPath,
                getId());
    }

    @Override
    protected String providerFingerprint() {
        return String.join(
                "\u0000",
                configId,
                format,
                rootPath,
                valuePath,
                labelPath,
                descriptionPath,
                groupPath,
                disabledPath,
                disabledReasonPath,
                tagsPath);
    }

    private String resolveFormat(String content) {
        if (!"auto".equals(format)) {
            return format;
        }
        String trimmed = content == null ? "" : content.stripLeading();
        if (trimmed.startsWith("<")) {
            return "xml";
        }
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            return "json";
        }
        return "yaml";
    }

    @Extension
    @Symbol("unifiedManagedConfigProvider")
    public static final class DescriptorImpl extends Descriptor<SharedProviderDefinition> {
        @Override
        public String getDisplayName() {
            return "Unified Parameters Managed Config Provider";
        }
    }
}
