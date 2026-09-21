package io.jenkins.plugins.unifiedparameters.provider;

import hudson.Extension;
import hudson.model.Descriptor;
import io.jenkins.plugins.unifiedparameters.model.Option;
import java.io.Serial;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public final class RestProviderDefinition extends SharedProviderDefinition implements ConnectionProfileConsumer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String connectionProfileId;
    private final String relativePath;
    private List<RestQueryParameter> queryParameters = List.of();
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
    public RestProviderDefinition(String id, String connectionProfileId, String relativePath) {
        super(id);
        if (connectionProfileId == null || connectionProfileId.isBlank()) {
            throw new IllegalArgumentException("Connection Profile ID is required.");
        }
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("REST Provider relative path is required.");
        }
        this.connectionProfileId = connectionProfileId;
        this.relativePath = relativePath;
    }

    @Override
    public String getConnectionProfileId() {
        return connectionProfileId;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public List<RestQueryParameter> getQueryParameters() {
        return queryParameters == null ? List.of() : queryParameters;
    }

    @DataBoundSetter
    public void setQueryParameters(List<RestQueryParameter> queryParameters) {
        this.queryParameters = queryParameters == null ? List.of() : List.copyOf(new ArrayList<>(queryParameters));
    }

    public String getFormat() {
        return format == null || format.isBlank() ? "auto" : format;
    }

    @DataBoundSetter
    public void setFormat(String format) {
        if (!java.util.Set.of("auto", "json", "xml").contains(format)) {
            throw new IllegalArgumentException("REST Provider format must be auto, json, or xml.");
        }
        this.format = format;
    }

    public String getRootPath() {
        return rootPath == null || rootPath.isBlank() ? "$" : rootPath;
    }

    @DataBoundSetter
    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }

    public String getValuePath() {
        return valuePath == null || valuePath.isBlank() ? "value" : valuePath;
    }

    @DataBoundSetter
    public void setValuePath(String valuePath) {
        this.valuePath = valuePath;
    }

    public String getLabelPath() {
        return labelPath == null || labelPath.isBlank() ? "label" : labelPath;
    }

    @DataBoundSetter
    public void setLabelPath(String labelPath) {
        this.labelPath = labelPath;
    }

    public String getDescriptionPath() {
        return descriptionPath == null || descriptionPath.isBlank() ? "description" : descriptionPath;
    }

    @DataBoundSetter
    public void setDescriptionPath(String descriptionPath) {
        this.descriptionPath = descriptionPath;
    }

    public String getGroupPath() {
        return groupPath == null || groupPath.isBlank() ? "group" : groupPath;
    }

    @DataBoundSetter
    public void setGroupPath(String groupPath) {
        this.groupPath = groupPath;
    }

    public String getDisabledPath() {
        return disabledPath == null || disabledPath.isBlank() ? "disabled" : disabledPath;
    }

    @DataBoundSetter
    public void setDisabledPath(String disabledPath) {
        this.disabledPath = disabledPath;
    }

    public String getDisabledReasonPath() {
        return disabledReasonPath == null || disabledReasonPath.isBlank() ? "disabledReason" : disabledReasonPath;
    }

    @DataBoundSetter
    public void setDisabledReasonPath(String disabledReasonPath) {
        this.disabledReasonPath = disabledReasonPath;
    }

    public String getTagsPath() {
        return tagsPath == null || tagsPath.isBlank() ? "tags" : tagsPath;
    }

    @DataBoundSetter
    public void setTagsPath(String tagsPath) {
        this.tagsPath = tagsPath;
    }

    @Override
    public List<Option> load(ProviderLoadContext context) {
        ConnectionProfile profile = context.connectionProfile()
                .orElseThrow(() -> new ProviderException(
                        io.jenkins.plugins.unifiedparameters.evaluation.ErrorCode.PROVIDER_UNAVAILABLE,
                        getId(),
                        "Connection Profile is unavailable."));
        Map<String, String> query = new LinkedHashMap<>();
        for (RestQueryParameter parameter : getQueryParameters()) {
            if (query.put(parameter.getName(), parameter.resolveValue(context.request())) != null) {
                throw new ProviderException(
                        io.jenkins.plugins.unifiedparameters.evaluation.ErrorCode.PROVIDER_UNAVAILABLE,
                        getId(),
                        "REST Provider has a duplicate query parameter.");
            }
        }
        java.net.URI uri = profile.buildUri(relativePath, query);
        RestProviderClient.Response response = new RestProviderClient().get(profile, context.request(), uri);
        String responseFormat = "auto".equals(getFormat()) ? response.format() : getFormat();
        if (!responseFormat.equals(response.format())) {
            throw new ProviderException(
                    io.jenkins.plugins.unifiedparameters.evaluation.ErrorCode.PROVIDER_UNAVAILABLE,
                    getId(),
                    "REST Provider response Content-Type does not match the configured format.");
        }
        return StructuredOptionParser.parse(
                response.body(),
                responseFormat,
                getRootPath(),
                getValuePath(),
                getLabelPath(),
                getDescriptionPath(),
                getGroupPath(),
                getDisabledPath(),
                getDisabledReasonPath(),
                getTagsPath(),
                getId());
    }

    @Override
    protected String providerFingerprint() {
        return connectionProfileId
                + '\u0000'
                + relativePath
                + '\u0000'
                + getQueryParameters().stream()
                        .map(parameter -> parameter.getName()
                                + "="
                                + parameter.getValue()
                                + "@"
                                + parameter.getDependencyParameter())
                        .toList()
                + '\u0000'
                + getFormat()
                + '\u0000'
                + getRootPath()
                + '\u0000'
                + getValuePath()
                + '\u0000'
                + getLabelPath()
                + '\u0000'
                + getDescriptionPath()
                + '\u0000'
                + getGroupPath()
                + '\u0000'
                + getDisabledPath()
                + '\u0000'
                + getDisabledReasonPath()
                + '\u0000'
                + getTagsPath();
    }

    @Extension
    @Symbol("unifiedRestProvider")
    public static final class DescriptorImpl extends Descriptor<SharedProviderDefinition> {
        @Override
        public String getDisplayName() {
            return "Unified Parameters REST JSON/XML Provider";
        }
    }
}
