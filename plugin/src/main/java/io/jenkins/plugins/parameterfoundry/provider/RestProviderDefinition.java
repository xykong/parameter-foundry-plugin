package io.jenkins.plugins.parameterfoundry.provider;

import hudson.Extension;
import hudson.model.Descriptor;
import io.jenkins.plugins.parameterfoundry.model.Option;
import java.io.Serial;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.sf.json.JSONArray;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public final class RestProviderDefinition extends SharedProviderDefinition
        implements ConnectionProfileConsumer, Cloneable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String connectionProfileId;
    private final String relativePath;
    private List<RestQueryParameter> queryParameters = List.of();
    private String format = "auto";
    private String responseMode = "mapped";
    private String rootPath = "$";
    private String valuePath = "value";
    private String labelPath = "label";
    private String descriptionPath = "description";
    private String groupPath = "group";
    private String disabledPath = "disabled";
    private String disabledReasonPath = "disabledReason";
    private String tagsPath = "tags";
    private transient RestProviderClient client;
    private transient RawScalarRestTransport rawClient;
    private List<RestRawLiteral> rawIgnoredResponses;
    private List<RestRawLiteral> rawTrailingChoices;
    private List<RestRawFailureChoice> rawFailureChoices;
    private transient List<RestRawFailureChoice.Declaration> frozenFailures;
    private transient List<Query> frozenQueries;

    private record Query(String name, String value, String dependency) {
        String resolveValue(ProviderRequest request) {
            if (dependency == null) return value;
            String resolved = request.dependencyValues().get(dependency);
            if (resolved == null) {
                throw new ProviderException(
                        io.jenkins.plugins.parameterfoundry.evaluation.ErrorCode.DEPENDENCY_INVALID,
                        request.providerId(),
                        "REST query dependency is unavailable.");
            }
            return resolved;
        }
    }

    /** Narrow raw-mode runtime copy, never persisted or exposed as a public clone API. */
    RestProviderDefinition snapshotForResolution() {
        if (frozenFailures != null) return this;
        try {
            RestProviderDefinition copy = (RestProviderDefinition) super.clone();
            copy.frozenFailures = List.copyOf(getRawFailureChoices()).stream()
                    .map(RestRawFailureChoice::snapshot)
                    .toList();
            var keys = new java.util.HashSet<String>();
            for (var declaration : copy.frozenFailures) {
                declaration.validate();
                if (!keys.add(declaration.key())) throw new IllegalArgumentException();
            }
            copy.frozenQueries = List.copyOf(getQueryParameters()).stream()
                    .map(row -> new Query(row.getName(), row.getValue(), row.getDependencyParameter()))
                    .toList();
            // Raw sources never execute transforms. Empty transforms and immutable literal rows can be shared.
            if (!getTransforms().isEmpty()) throw new IllegalArgumentException();
            copy.setTransforms(List.of());
            copy.rawIgnoredResponses = List.copyOf(getRawIgnoredResponses());
            copy.rawTrailingChoices = List.copyOf(getRawTrailingChoices());
            copy.setFallbackOptions(getFallbackOptions().stream()
                    .map(RestProviderDefinition::copyOption)
                    .toList());
            return copy;
        } catch (CloneNotSupportedException impossible) {
            throw new AssertionError(impossible);
        } catch (RuntimeException invalid) {
            throw rawUnavailable("Whole-response configuration is invalid or duplicated.");
        }
    }

    private static Option copyOption(Option source) {
        Option copy = source.getSourceChoiceMetadata() == null
                ? new Option(source.getValue())
                : Option.sourceChoice(
                        source.getValue(), source.getLabel(), source.isDisabled(), source.getSourceChoiceMetadata());
        copy.setLabel(source.getLabel());
        copy.setDescription(source.getDescription());
        copy.setGroup(source.getGroup());
        copy.setDisabled(source.isDisabled());
        copy.setDisabledReason(source.getDisabledReason());
        copy.setTags(source.getTags());
        return copy;
    }

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

    RestProviderDefinition(String id, String connectionProfileId, String relativePath, RestProviderClient client) {
        this(id, connectionProfileId, relativePath);
        this.client = java.util.Objects.requireNonNull(client);
    }

    RestProviderDefinition(
            String id,
            String connectionProfileId,
            String relativePath,
            RestProviderClient client,
            RawScalarRestTransport rawClient) {
        this(id, connectionProfileId, relativePath);
        this.client = client;
        this.rawClient = java.util.Objects.requireNonNull(rawClient);
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

    public String getResponseMode() {
        return responseMode == null ? "mapped" : responseMode;
    }

    @DataBoundSetter
    public void setResponseMode(String responseMode) {
        if (!java.util.Set.of("mapped", "active-choices-choice", "active-choices-cascade", "active-choices-raw-scalar")
                .contains(responseMode)) {
            throw new IllegalArgumentException("Unsupported REST response mode.");
        }
        this.responseMode = responseMode;
    }

    public boolean isActiveChoicesResponse() {
        return java.util.Set.of("active-choices-choice", "active-choices-cascade", "active-choices-raw-scalar")
                .contains(getResponseMode());
    }

    public boolean isRawScalarResponse() {
        return "active-choices-raw-scalar".equals(getResponseMode());
    }

    public List<RestRawLiteral> getRawIgnoredResponses() {
        return rawIgnoredResponses == null ? List.of() : rawIgnoredResponses;
    }

    @DataBoundSetter
    public void setRawIgnoredResponses(List<RestRawLiteral> values) {
        rawIgnoredResponses = values == null ? List.of() : List.copyOf(values);
    }

    public List<RestRawLiteral> getRawTrailingChoices() {
        return rawTrailingChoices == null ? List.of() : rawTrailingChoices;
    }

    @DataBoundSetter
    public void setRawTrailingChoices(List<RestRawLiteral> values) {
        rawTrailingChoices = values == null ? List.of() : List.copyOf(values);
    }

    public List<RestRawFailureChoice> getRawFailureChoices() {
        return rawFailureChoices == null ? List.of() : rawFailureChoices;
    }

    @DataBoundSetter
    public void setRawFailureChoices(List<RestRawFailureChoice> values) {
        rawFailureChoices = values == null ? List.of() : List.copyOf(values);
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
        if (isRawScalarResponse() && frozenFailures == null) {
            return snapshotForResolution()
                    .load(new ProviderLoadContext(
                            context.request(),
                            context.connectionProfile().map(ConnectionProfile::snapshotForResolution)));
        }
        if (!java.util.Set.of("mapped", "active-choices-choice", "active-choices-cascade", "active-choices-raw-scalar")
                .contains(getResponseMode())) {
            throw new ProviderException(
                    io.jenkins.plugins.parameterfoundry.evaluation.ErrorCode.PROVIDER_UNAVAILABLE,
                    getId(),
                    "REST Provider response mode is invalid.");
        }
        if (isActiveChoicesResponse() && !getTransforms().isEmpty()) {
            throw new ProviderException(
                    io.jenkins.plugins.parameterfoundry.evaluation.ErrorCode.PROVIDER_UNAVAILABLE,
                    getId(),
                    "Active Choices response modes require an untransformed source response.");
        }
        ConnectionProfile profile = context.connectionProfile()
                .orElseThrow(() -> new ProviderException(
                        io.jenkins.plugins.parameterfoundry.evaluation.ErrorCode.PROVIDER_UNAVAILABLE,
                        getId(),
                        "Connection Profile is unavailable."));
        Map<String, String> query = new LinkedHashMap<>();
        List<Query> queries = frozenQueries != null
                ? frozenQueries
                : getQueryParameters().stream()
                        .map(row -> new Query(row.getName(), row.getValue(), row.getDependencyParameter()))
                        .toList();
        for (Query parameter : queries) {
            if (query.put(parameter.name(), parameter.resolveValue(context.request())) != null) {
                throw rawUnavailable("REST Provider has a duplicate query parameter.");
            }
        }
        java.net.URI uri = profile.buildUri(relativePath, query);
        if (isRawScalarResponse()) {
            return loadRawScalar(profile, context, uri);
        }
        RestProviderClient.Response response =
                (client == null ? new RestProviderClient() : client).get(profile, context.request(), uri);
        String responseFormat = "auto".equals(getFormat()) ? response.format() : getFormat();
        if (!responseFormat.equals(response.format())) {
            throw new ProviderException(
                    io.jenkins.plugins.parameterfoundry.evaluation.ErrorCode.PROVIDER_UNAVAILABLE,
                    getId(),
                    "REST Provider response Content-Type does not match the configured format.");
        }
        if (isActiveChoicesResponse()) {
            if (!"json".equals(responseFormat)) {
                throw new ProviderException(
                        io.jenkins.plugins.parameterfoundry.evaluation.ErrorCode.PROVIDER_UNAVAILABLE,
                        getId(),
                        "Active Choices response modes require JSON.");
            }
            return ActiveChoicesChoiceResponse.options(
                    response.body(), "active-choices-cascade".equals(getResponseMode()), getId());
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

    private List<Option> loadRawScalar(ConnectionProfile profile, ProviderLoadContext context, java.net.URI uri) {
        if (!"auto".equals(getFormat())) {
            throw rawUnavailable("Whole-response mode requires automatic format selection.");
        }
        for (var choice : frozenFailures) {
            if (choice.includesUri() && uri.getRawQuery() != null) {
                throw rawUnavailable("URI-bearing failure choices require a query-free target.");
            }
        }
        List<String> ignored = rawLiteralValues(getRawIgnoredResponses());
        List<String> trailing = rawLiteralValues(getRawTrailingChoices());
        RawScalarRestTransport.Result result = (rawClient == null
                        ? new RawScalarRestTransport(new NetworkPolicy())
                        : rawClient)
                .get(profile, context.request(), uri);
        if (result instanceof RawScalarRestTransport.Body body) {
            return RawScalarChoiceAdapter.options(body.bytes(), ignored, trailing);
        }
        context.checkCancelled();
        var failure = (RawScalarRestTransport.Failure) result;
        // Exact HTTP status overrides a category-wide mapping, regardless of form row order.
        RestRawFailureChoice.Declaration match = frozenFailures.stream()
                .filter(choice -> choice.matches(failure) && choice.status() != 0)
                .findFirst()
                .orElseGet(() -> frozenFailures.stream()
                        .filter(choice -> choice.matches(failure))
                        .findFirst()
                        .orElse(null));
        if (match == null) {
            throw rawUnavailable("Whole-response transport returned an unmapped failure.");
        }
        return RawScalarChoiceAdapter.options(
                match.render(uri, failure.status()).getBytes(StandardCharsets.UTF_8), List.of(), trailing);
    }

    private ProviderException rawUnavailable(String message) {
        return new ProviderException(
                io.jenkins.plugins.parameterfoundry.evaluation.ErrorCode.PROVIDER_UNAVAILABLE, getId(), message);
    }

    private List<String> rawLiteralValues(List<RestRawLiteral> rows) {
        List<String> values = new ArrayList<>();
        for (RestRawLiteral row : rows) {
            if (row == null || row.getValue() == null) {
                throw rawUnavailable("Whole-response literal rows must contain strings.");
            }
            values.add(row.getValue());
        }
        return List.copyOf(values);
    }

    @Override
    protected String providerFingerprint() {
        String structured = connectionProfileId
                + '\u0000'
                + relativePath
                + '\u0000'
                + (frozenQueries != null
                                ? frozenQueries
                                : getQueryParameters().stream()
                                        .map(row ->
                                                new Query(row.getName(), row.getValue(), row.getDependencyParameter()))
                                        .toList())
                        .stream()
                                .map(row -> row.name() + "=" + row.value() + "@" + row.dependency())
                                .toList()
                + '\u0000'
                + getFormat()
                + '\u0000'
                + getResponseMode()
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
        if (!isRawScalarResponse()) return structured;
        return structured
                + "\u0000raw-scalar-v1\u0000"
                + JSONArray.fromObject(rawLiteralValues(getRawIgnoredResponses()))
                + '\u0000'
                + JSONArray.fromObject(rawLiteralValues(getRawTrailingChoices()))
                + '\u0000'
                + JSONArray.fromObject((frozenFailures != null
                                ? frozenFailures
                                : getRawFailureChoices().stream()
                                        .map(RestRawFailureChoice::snapshot)
                                        .toList())
                        .stream()
                                .map(choice -> java.util.Arrays.asList(
                                        choice.kind(), choice.status(), choice.value(), choice.fingerprint()))
                                .toList());
    }

    @Extension
    @Symbol("foundryRestProvider")
    public static final class DescriptorImpl extends Descriptor<SharedProviderDefinition> {
        @Override
        public String getDisplayName() {
            return "Parameter Foundry REST Provider";
        }
    }
}
