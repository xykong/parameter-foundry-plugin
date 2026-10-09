package io.jenkins.plugins.parameterfoundry.snapshot;

import hudson.cli.BuildCommand;
import hudson.cli.CLICommand;
import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersDefinitionProperty;
import io.jenkins.plugins.parameterfoundry.definition.AbstractFoundryParameterDefinition;
import io.jenkins.plugins.parameterfoundry.evaluation.DependencyValues;
import io.jenkins.plugins.parameterfoundry.evaluation.ErrorCode;
import io.jenkins.plugins.parameterfoundry.evaluation.EvaluationException;
import io.jenkins.plugins.parameterfoundry.provider.ProviderOperation;
import io.jenkins.plugins.parameterfoundry.provider.ProviderResolutionService;
import java.util.Map;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest2;

public final class ProviderSubmission {

    private ProviderSubmission() {}

    public static ResolvedProviderOptions resolve(
            Job<?, ?> job,
            AbstractFoundryParameterDefinition definition,
            ProviderOperation operation,
            StaplerRequest2 request,
            JSONObject submitted) {
        JSONObject dependencyObject = submitted == null
                ? dependenciesFromRequest(definition, request)
                : DependencyValues.parseJsonObject(definition, submitted.optString("dependenciesJson", null));
        if (dependencyObject == null) {
            dependencyObject = dependenciesFromRequest(definition, request);
        }
        Map<String, String> dependencies = DependencyValues.fromJson(definition, dependencyObject);
        String snapshotId = submitted == null ? null : submitted.optString("snapshotId", null);
        if (snapshotId == null || snapshotId.isBlank()) {
            snapshotId = request.getParameter(snapshotRequestField(definition));
        }
        return ProviderSnapshotStore.get().resolveForBuild(job, definition, dependencies, snapshotId, operation);
    }

    public static ResolvedProviderOptions resolveWithoutSubmission(
            Job<?, ?> job, AbstractFoundryParameterDefinition definition, ProviderOperation operation) {
        if (!definition.getDependsOn().isEmpty()) {
            throw new EvaluationException(
                    ErrorCode.DEPENDENCY_INVALID,
                    definition.getName(),
                    "Reactive parameter requires canonical dependency values: " + definition.getDependsOn());
        }
        return ProviderSnapshotStore.get().resolveForBuild(job, definition, Map.of(), null, operation);
    }

    public static ResolvedProviderOptions resolveForDefault(AbstractFoundryParameterDefinition definition) {
        if (!definition.isProviderBacked()) {
            return new ResolvedProviderOptions(
                    definition.getSelectableOptions(), definition.getTable(), null, null, false);
        }
        Job<?, ?> owningJob = requireOwningJob(definition);
        CliBuildContext context = ParameterFoundryCliListener.currentBuild();
        if (context != null && context.job().getFullName().equals(owningJob.getFullName())) {
            try {
                return resolveForCliParameters(
                        context.job(), definition, ProviderOperation.BUILD, context.parameters());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new EvaluationException(
                        ErrorCode.PROVIDER_UNAVAILABLE,
                        definition.getName(),
                        "CLI default Provider resolution was interrupted.");
            } catch (java.io.IOException exception) {
                throw new EvaluationException(
                        ErrorCode.PROVIDER_UNAVAILABLE,
                        definition.getName(),
                        "CLI default Provider resolution failed.");
            }
        }
        return resolveWithoutSubmission(owningJob, definition, ProviderOperation.RESOLVE);
    }

    public static ResolvedProviderOptions resolveForCli(
            Job<?, ?> job,
            AbstractFoundryParameterDefinition definition,
            ProviderOperation operation,
            CLICommand command)
            throws java.io.IOException, InterruptedException {
        if (definition.getDependsOn().isEmpty()) {
            return ProviderSnapshotStore.get().resolveForBuild(job, definition, Map.of(), null, operation);
        }
        if (!(command instanceof BuildCommand buildCommand)) {
            throw new EvaluationException(
                    ErrorCode.DEPENDENCY_INVALID,
                    definition.getName(),
                    "Reactive CLI evaluation requires the Jenkins build command.");
        }
        // BuildCommand replaces consumed file arguments with null, even when unrelated to this Provider.
        Map<String, String> parameters = buildCommand.parameters == null
                ? Map.of()
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(buildCommand.parameters));
        return resolveForCliParameters(job, definition, operation, parameters);
    }

    private static ResolvedProviderOptions resolveForCliParameters(
            Job<?, ?> job,
            AbstractFoundryParameterDefinition definition,
            ProviderOperation operation,
            Map<String, String> parameters)
            throws java.io.IOException, InterruptedException {
        if (definition.getDependsOn().isEmpty()) {
            return ProviderSnapshotStore.get().resolveForBuild(job, definition, Map.of(), null, operation);
        }
        ParametersDefinitionProperty property = job.getProperty(ParametersDefinitionProperty.class);
        if (property == null) {
            throw new EvaluationException(
                    ErrorCode.CONFIGURATION_INVALID, job.getFullName(), "Job is not parameterized.");
        }
        io.jenkins.plugins.parameterfoundry.validation.DefinitionValidationResult validation =
                io.jenkins.plugins.parameterfoundry.validation.DefinitionValidationService.validateJobDefinitions(
                        property.getParameterDefinitions());
        if (!validation.isValid()) {
            throw new EvaluationException(ErrorCode.CONFIGURATION_INVALID, job.getFullName(), validation.summarize());
        }
        Map<String, ParameterValue> values = new java.util.LinkedHashMap<>();
        BuildCommand cliCommand = new BuildCommand();
        cliCommand.job = job;
        cliCommand.parameters = parameters;
        for (String dependency : definition.getDependsOn()) {
            ParameterDefinition dependencyDefinition = property.getParameterDefinition(dependency);
            if (dependencyDefinition == null) {
                throw new EvaluationException(
                        ErrorCode.DEPENDENCY_INVALID, definition.getName(), "Unknown dependency: " + dependency);
            }
            String raw = parameters.get(dependency);
            ParameterValue value = raw == null
                    ? dependencyDefinition.getDefaultParameterValue()
                    : dependencyDefinition.createValue(cliCommand, raw);
            if (value == null) {
                throw new EvaluationException(
                        ErrorCode.DEPENDENCY_INVALID,
                        definition.getName(),
                        "Dependency has no canonical value: " + dependency);
            }
            values.put(dependency, value);
        }
        Map<String, String> dependencies = DependencyValues.fromParameterValues(definition, values);
        return ProviderSnapshotStore.get().resolveForBuild(job, definition, dependencies, null, operation);
    }

    public static String snapshotRequestField(AbstractFoundryParameterDefinition definition) {
        return "__up_snapshot_" + definition.getName();
    }

    public static String dependenciesRequestField(AbstractFoundryParameterDefinition definition) {
        return "__up_dependencies_" + definition.getName();
    }

    private static JSONObject dependenciesFromRequest(
            AbstractFoundryParameterDefinition definition, StaplerRequest2 request) {
        String encoded = request.getParameter(dependenciesRequestField(definition));
        if (encoded != null && !encoded.isBlank()) {
            JSONObject submitted = DependencyValues.parseJsonObject(definition, encoded);
            if (submitted != null) {
                return submitted;
            }
        }
        JSONObject dependencies = new JSONObject();
        for (String dependency : definition.getDependsOn()) {
            String[] values = request.getParameterValues(dependency);
            if (values == null) {
                throw new EvaluationException(
                        ErrorCode.DEPENDENCY_INVALID, definition.getName(), "Missing dependency: " + dependency);
            }
            if (values.length == 1) {
                dependencies.put(dependency, values[0]);
            } else {
                dependencies.put(dependency, JSONArray.fromObject(values));
            }
        }
        return dependencies;
    }

    public static Job<?, ?> requireOwningJob(AbstractFoundryParameterDefinition definition) {
        return ProviderResolutionService.findOwningJob(definition)
                .orElseThrow(() -> new EvaluationException(
                        ErrorCode.CONFIGURATION_INVALID,
                        definition.getName(),
                        "Provider-backed parameter is not attached to a Job."));
    }
}
