package io.jenkins.plugins.unifiedparameters.api;

import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Queue;
import hudson.model.queue.ScheduleResult;
import io.jenkins.plugins.unifiedparameters.definition.AbstractUnifiedParameterDefinition;
import io.jenkins.plugins.unifiedparameters.evaluation.DependencyGraph;
import io.jenkins.plugins.unifiedparameters.evaluation.DependencyValues;
import io.jenkins.plugins.unifiedparameters.evaluation.ErrorCode;
import io.jenkins.plugins.unifiedparameters.evaluation.EvaluationException;
import io.jenkins.plugins.unifiedparameters.model.Option;
import io.jenkins.plugins.unifiedparameters.model.OptionImportEndpoint;
import io.jenkins.plugins.unifiedparameters.provider.ProviderException;
import io.jenkins.plugins.unifiedparameters.provider.ProviderHealth;
import io.jenkins.plugins.unifiedparameters.provider.ProviderOperation;
import io.jenkins.plugins.unifiedparameters.provider.ProviderRegistry;
import io.jenkins.plugins.unifiedparameters.provider.ProviderResolution;
import io.jenkins.plugins.unifiedparameters.provider.ProviderResolutionService;
import io.jenkins.plugins.unifiedparameters.provider.ProviderRuntime;
import io.jenkins.plugins.unifiedparameters.snapshot.ProviderSnapshotStore;
import io.jenkins.plugins.unifiedparameters.snapshot.ResolvedProviderOptions;
import io.jenkins.plugins.unifiedparameters.snapshot.SnapshotAudience;
import io.jenkins.plugins.unifiedparameters.validation.DefinitionValidationResult;
import io.jenkins.plugins.unifiedparameters.validation.DefinitionValidationService;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.verb.GET;
import org.kohsuke.stapler.verb.POST;

public final class V1Api {

    private static final int MAX_REQUEST_CHARS = 1024 * 1024;
    private static final Set<String> RESOLVE_FIELDS = Set.of("parameter", "dependencies", "version");
    private static final Set<String> VALIDATE_FIELDS = Set.of("values", "providerSnapshots");
    private static final Set<String> BUILD_FIELDS = Set.of("values", "providerSnapshots", "delaySeconds");

    private final Job<?, ?> job;

    V1Api(Job<?, ?> job) {
        this.job = job;
    }

    @GET
    public void doSchema(StaplerRequest2 request, StaplerResponse2 response) throws IOException {
        checkBuildPermission();
        try {
            ParametersDefinitionProperty property = requireProperty();
            JSONArray parameters = new JSONArray();
            for (ParameterDefinition definition : property.getParameterDefinitions()) {
                parameters.add(TypedParameterAdapterRegistry.schema(definition));
            }
            JSONObject result = ok();
            result.put("job", job.getFullName());
            result.put("parameters", parameters);
            writeJson(response, 200, result);
        } catch (EvaluationException exception) {
            writeError(response, statusFor(exception), exception);
        }
    }

    @POST
    public void doResolve(StaplerRequest2 request, StaplerResponse2 response) throws IOException {
        checkBuildPermission();
        try {
            JSONObject body = readJson(request, RESOLVE_FIELDS);
            String name = body.optString("parameter", null);
            if (name == null || name.isBlank()) {
                throw new EvaluationException(ErrorCode.MALFORMED_INPUT, "parameter", "A parameter name is required.");
            }
            ParameterDefinition definition = requireProperty().getParameterDefinition(name);
            if (!(definition instanceof AbstractUnifiedParameterDefinition unified)) {
                throw new EvaluationException(
                        ErrorCode.UNSUPPORTED_DEFINITION, name, "Only Unified parameter definitions expose options.");
            }
            DefinitionValidationService.requireValid(unified);
            JSONObject submittedDependencies =
                    body.containsKey("dependencies") ? requireObject(body, "dependencies") : null;
            Map<String, String> dependencies = DependencyValues.fromJson(unified, submittedDependencies);
            ProviderResolution resolution =
                    ProviderResolutionService.resolve(job, unified, ProviderOperation.RESOLVE, dependencies);
            ResolvedProviderOptions snapshot = ProviderSnapshotStore.get()
                    .create(job, unified, dependencies, resolution, SnapshotAudience.BUILD_VALUE);
            JSONArray options = new JSONArray();
            for (Option option : resolution.options()) {
                JSONObject encoded = new JSONObject();
                encoded.put("value", option.getValue());
                encoded.put("label", option.getLabel());
                encoded.put("description", option.getDescription());
                encoded.put("group", option.getGroup());
                encoded.put("disabled", option.isDisabled());
                encoded.put("disabledReason", option.getDisabledReason());
                encoded.put("tags", JSONArray.fromObject(option.getTags()));
                options.add(encoded);
            }
            JSONObject result = ok();
            result.put("parameter", name);
            result.put("version", body.containsKey("version") ? body.get("version") : null);
            result.put("options", options);
            Object defaultValue = unified.isMultiple()
                    ? io.jenkins.plugins.unifiedparameters.evaluation.ParameterEvaluationService.evaluateMultiple(
                            (io.jenkins.plugins.unifiedparameters.definition.UnifiedMultiChoiceParameterDefinition)
                                    unified,
                            io.jenkins.plugins.unifiedparameters.evaluation.DecodedInput.absent(),
                            resolution.options())
                    : io.jenkins.plugins.unifiedparameters.evaluation.ParameterEvaluationService.evaluateSingle(
                            (io.jenkins.plugins.unifiedparameters.definition.UnifiedChoiceParameterDefinition) unified,
                            io.jenkins.plugins.unifiedparameters.evaluation.DecodedInput.absent(),
                            resolution.options());
            result.put("default", defaultValue);
            result.put(
                    "defaults",
                    defaultValue instanceof List<?>
                            ? JSONArray.fromObject(defaultValue)
                            : (defaultValue == null ? new JSONArray() : JSONArray.fromObject(List.of(defaultValue))));
            result.put("snapshotId", snapshot.snapshotId());
            result.put("generatedAt", resolution.generatedAt().toString());
            result.put("expiresAt", resolution.expiresAt().toString());
            result.put("providerDigest", resolution.providerDigest());
            result.put("optionSetDigest", resolution.optionSetDigest());
            result.put("cacheHit", resolution.cacheHit());
            result.put("fallback", resolution.fallback());
            result.put("fallbackReason", resolution.fallbackReason());
            JSONArray warnings = new JSONArray();
            if (resolution.fallback()) {
                warnings.add(resolution.fallbackReason());
            }
            result.put("warnings", warnings);
            writeJson(response, 200, result);
        } catch (EvaluationException exception) {
            writeError(response, statusFor(exception), exception);
        }
    }

    @POST
    public void doValidate(StaplerRequest2 request, StaplerResponse2 response) throws IOException {
        checkBuildPermission();
        try {
            JSONObject body = readJson(request, VALIDATE_FIELDS);
            List<ParameterValue> values = decodeAll(body, ProviderOperation.VALIDATE);
            JSONObject result = ok();
            result.put("valid", true);
            result.put("canonicalValues", encodeValues(values));
            writeJson(response, 200, result);
        } catch (EvaluationException exception) {
            writeError(response, statusFor(exception), exception);
        }
    }

    @POST
    public void doBuild(StaplerRequest2 request, StaplerResponse2 response) throws IOException {
        checkBuildPermission();
        JSONObject body;
        List<ParameterValue> values;
        try {
            body = readJson(request, BUILD_FIELDS);
            values = decodeAll(body, ProviderOperation.BUILD);
        } catch (EvaluationException exception) {
            writeError(response, statusFor(exception), exception);
            return;
        }

        if (!(job instanceof ParameterizedJobMixIn.ParameterizedJob<?, ?>) || !(job instanceof Queue.Task task)) {
            writeError(
                    response,
                    409,
                    new EvaluationException(
                            ErrorCode.UNSUPPORTED_DEFINITION,
                            job.getFullName(),
                            "Job type cannot be scheduled through the typed API."));
            return;
        }
        int delay = Math.max(0, Math.min(body.optInt("delaySeconds", 0), 3600));
        checkBuildPermission();
        ScheduleResult scheduled = Jenkins.get()
                .getQueue()
                .schedule2(task, delay, new ParametersAction(values), new CauseAction(new Cause.UserIdCause()));
        Queue.Item item = scheduled.getItem();
        if (item == null) {
            writeError(
                    response,
                    409,
                    new EvaluationException(
                            ErrorCode.CONFIGURATION_INVALID,
                            job.getFullName(),
                            "Jenkins refused to create a queue item."));
            return;
        }

        JSONObject result = ok();
        result.put("queueItem", item.getId());
        result.put("location", request.getContextPath() + "/" + item.getUrl());
        response.setHeader("Location", request.getContextPath() + "/" + item.getUrl());
        writeJson(response, scheduled.isCreated() ? 201 : 303, result);
    }

    public ProviderApi getProviders() {
        return new ProviderApi(job);
    }

    public ImportApi getImport() {
        return new ImportApi(job);
    }

    private List<ParameterValue> decodeAll(JSONObject body, ProviderOperation operation) {
        ParametersDefinitionProperty property = requireProperty();
        DefinitionValidationResult validation =
                DefinitionValidationService.validateJobDefinitions(property.getParameterDefinitions());
        if (!validation.isValid()) {
            throw new EvaluationException(ErrorCode.CONFIGURATION_INVALID, job.getFullName(), validation.summarize());
        }

        Set<String> unsupported = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (ParameterDefinition definition : property.getParameterDefinitions()) {
            if (!names.add(definition.getName())) {
                throw new EvaluationException(
                        ErrorCode.CONFIGURATION_INVALID, definition.getName(), "Duplicate parameter name.");
            }
            if (!TypedParameterAdapterRegistry.supports(definition)) {
                unsupported.add(definition.getName());
            }
        }
        if (!unsupported.isEmpty()) {
            throw new EvaluationException(
                    ErrorCode.UNSUPPORTED_DEFINITION,
                    job.getFullName(),
                    "Unsupported parameter definitions: " + unsupported);
        }

        JSONObject requestValues = objectOrEmpty(body, "values");
        for (Object key : requestValues.keySet()) {
            if (!names.contains(String.valueOf(key))) {
                throw new EvaluationException(
                        ErrorCode.MALFORMED_INPUT, String.valueOf(key), "Unknown parameter name.");
            }
        }
        JSONObject snapshots = objectOrEmpty(body, "providerSnapshots");
        for (Object key : snapshots.keySet()) {
            String name = String.valueOf(key);
            ParameterDefinition definition = property.getParameterDefinition(name);
            if (definition == null) {
                throw new EvaluationException(ErrorCode.MALFORMED_INPUT, name, "Unknown Provider snapshot parameter.");
            }
            if (!(definition instanceof AbstractUnifiedParameterDefinition unified) || !unified.isProviderBacked()) {
                throw new EvaluationException(
                        ErrorCode.MALFORMED_INPUT, name, "Provider snapshot requires a dynamic Unified parameter.");
            }
        }

        Map<String, ParameterValue> decoded = new LinkedHashMap<>();
        for (ParameterDefinition definition : DependencyGraph.topologicalOrder(property.getParameterDefinitions())) {
            boolean present = requestValues.containsKey(definition.getName());
            Map<String, String> dependencies = definition instanceof AbstractUnifiedParameterDefinition unified
                    ? DependencyValues.fromParameterValues(unified, decoded)
                    : Map.of();
            String snapshotId = snapshots.containsKey(definition.getName())
                    ? requireString(snapshots.get(definition.getName()), definition.getName(), "snapshot ID")
                    : null;
            ParameterValue value = TypedParameterAdapterRegistry.decode(
                    job,
                    definition,
                    present,
                    present ? requestValues.get(definition.getName()) : null,
                    dependencies,
                    snapshotId,
                    operation);
            if (value != null) {
                decoded.put(definition.getName(), value);
            }
        }
        List<ParameterValue> result = new ArrayList<>();
        for (ParameterDefinition definition : property.getParameterDefinitions()) {
            ParameterValue value = decoded.get(definition.getName());
            if (value != null) {
                result.add(value);
            }
        }
        return List.copyOf(result);
    }

    private ParametersDefinitionProperty requireProperty() {
        ParametersDefinitionProperty property = job.getProperty(ParametersDefinitionProperty.class);
        if (property == null) {
            throw new EvaluationException(
                    ErrorCode.CONFIGURATION_INVALID, job.getFullName(), "Job is not parameterized.");
        }
        return property;
    }

    private void checkBuildPermission() {
        job.checkPermission(Item.READ);
        job.checkPermission(Item.BUILD);
    }

    private static JSONObject encodeValues(List<ParameterValue> values) {
        JSONObject encoded = new JSONObject();
        for (ParameterValue value : values) {
            encoded.put(value.getName(), value.getValue());
        }
        return encoded;
    }

    private static JSONObject readJson(StaplerRequest2 request, Set<String> allowedFields) throws IOException {
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                body.append(buffer, 0, read);
                if (body.length() > MAX_REQUEST_CHARS) {
                    throw new EvaluationException(
                            ErrorCode.REQUEST_TOO_LARGE, "request", "Request body exceeds 1 MiB.");
                }
            }
        }
        if (body.isEmpty()) {
            return new JSONObject();
        }
        try {
            JSONObject decoded = JSONObject.fromObject(body.toString());
            for (Object key : decoded.keySet()) {
                if (!allowedFields.contains(String.valueOf(key))) {
                    throw new EvaluationException(
                            ErrorCode.MALFORMED_INPUT, String.valueOf(key), "Unknown top-level request field.");
                }
            }
            return decoded;
        } catch (EvaluationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new EvaluationException(ErrorCode.MALFORMED_INPUT, "request", "Request body is not valid JSON.");
        }
    }

    private static int statusFor(EvaluationException exception) {
        return switch (exception.getCode()) {
            case REQUEST_TOO_LARGE -> 413;
            case RATE_LIMITED -> 429;
            case PROVIDER_UNAVAILABLE -> 503;
            case UNSUPPORTED_DEFINITION, CONFIGURATION_INVALID, CONFIGURATION_CHANGED, SNAPSHOT_INVALID -> 409;
            default -> 400;
        };
    }

    private static JSONObject objectOrEmpty(JSONObject parent, String field) {
        if (!parent.containsKey(field)) {
            return new JSONObject();
        }
        return requireObject(parent, field);
    }

    private static JSONObject requireObject(JSONObject parent, String field) {
        Object value = parent.get(field);
        if (value instanceof JSONObject object) {
            return object;
        }
        throw new EvaluationException(ErrorCode.INVALID_TYPE, field, "Expected a JSON object.");
    }

    private static String requireString(Object value, String parameter, String description) {
        if (value instanceof String stringValue) {
            return stringValue;
        }
        throw new EvaluationException(
                ErrorCode.INVALID_TYPE, parameter, "Expected a JSON string for " + description + ".");
    }

    private static JSONObject ok() {
        JSONObject result = new JSONObject();
        result.put("ok", true);
        return result;
    }

    private static void writeError(StaplerResponse2 response, int status, EvaluationException exception)
            throws IOException {
        JSONObject error = new JSONObject();
        error.put("code", exception.getStableCode());
        error.put("parameter", exception.getParameter());
        error.put("message", exception.getMessage());
        JSONArray errors = new JSONArray();
        errors.add(error);
        JSONObject result = new JSONObject();
        result.put("ok", false);
        result.put("errors", errors);
        if (exception instanceof ProviderException providerException && providerException.getRetryAfterSeconds() > 0) {
            response.setHeader("Retry-After", String.valueOf(providerException.getRetryAfterSeconds()));
        }
        writeJson(response, status, result);
    }

    private static void writeJson(StaplerResponse2 response, int status, JSONObject body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(body.toString());
    }

    public static final class ProviderApi {
        private final Job<?, ?> job;

        ProviderApi(Job<?, ?> job) {
            this.job = job;
        }

        @GET
        public void doHealth(StaplerRequest2 request, StaplerResponse2 response) throws IOException {
            job.checkPermission(Item.CONFIGURE);
            JSONObject result = ok();
            JSONArray providers = new JSONArray();
            ParametersDefinitionProperty property = job.getProperty(ParametersDefinitionProperty.class);
            if (property != null) {
                for (ParameterDefinition definition : property.getParameterDefinitions()) {
                    if (!(definition instanceof AbstractUnifiedParameterDefinition unified)
                            || !unified.isProviderBacked()) {
                        continue;
                    }
                    ProviderRegistry.findProvider(job, unified.getProviderId()).ifPresent(located -> {
                        JSONObject provider = new JSONObject();
                        provider.put("id", located.provider().getId());
                        provider.put("scope", located.scope().name());
                        provider.put("owner", located.ownerId());
                        ProviderHealth health = ProviderRuntime.get()
                                .health(located.ownerId(), located.provider().getId())
                                .orElse(null);
                        provider.put("healthy", health == null ? null : health.healthy());
                        provider.put(
                                "checkedAt",
                                health == null ? null : health.checkedAt().toString());
                        provider.put("latencyMillis", health == null ? null : health.latencyMillis());
                        provider.put("optionCount", health == null ? null : health.optionCount());
                        provider.put("message", health == null ? "Not checked" : health.message());
                        providers.add(provider);
                    });
                }
            }
            result.put("providers", providers);
            writeJson(response, 200, result);
        }
    }

    public static final class ImportApi {
        private final Job<?, ?> job;

        ImportApi(Job<?, ?> job) {
            this.job = job;
        }

        @POST
        public void doPreview(StaplerRequest2 request, StaplerResponse2 response) throws IOException {
            OptionImportEndpoint.previewForJob(job, request, response);
        }
    }
}
