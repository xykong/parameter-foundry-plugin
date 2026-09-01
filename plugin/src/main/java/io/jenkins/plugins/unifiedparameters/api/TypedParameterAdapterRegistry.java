package io.jenkins.plugins.unifiedparameters.api;

import hudson.model.BooleanParameterDefinition;
import hudson.model.BooleanParameterValue;
import hudson.model.ChoiceParameterDefinition;
import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import io.jenkins.plugins.unifiedparameters.definition.UnifiedChoiceParameterDefinition;
import io.jenkins.plugins.unifiedparameters.definition.UnifiedChoiceParameterValue;
import io.jenkins.plugins.unifiedparameters.definition.UnifiedMultiChoiceParameterDefinition;
import io.jenkins.plugins.unifiedparameters.definition.UnifiedMultiChoiceParameterValue;
import io.jenkins.plugins.unifiedparameters.evaluation.DecodedInput;
import io.jenkins.plugins.unifiedparameters.evaluation.ErrorCode;
import io.jenkins.plugins.unifiedparameters.evaluation.EvaluationException;
import io.jenkins.plugins.unifiedparameters.evaluation.ParameterEvaluationService;
import io.jenkins.plugins.unifiedparameters.form.UnifiedFormParameterDefinition;
import io.jenkins.plugins.unifiedparameters.provider.ProviderOperation;
import io.jenkins.plugins.unifiedparameters.snapshot.ProviderSnapshotStore;
import io.jenkins.plugins.unifiedparameters.snapshot.ResolvedProviderOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.sf.json.JSONArray;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;

final class TypedParameterAdapterRegistry {

    private TypedParameterAdapterRegistry() {}

    static boolean supports(ParameterDefinition definition) {
        return definition instanceof UnifiedChoiceParameterDefinition
                || definition instanceof UnifiedMultiChoiceParameterDefinition
                || definition instanceof UnifiedFormParameterDefinition
                || definition instanceof ChoiceParameterDefinition
                || definition instanceof BooleanParameterDefinition
                || definition instanceof StringParameterDefinition;
    }

    static JSONObject schema(ParameterDefinition definition) {
        JSONObject schema = new JSONObject();
        schema.put("name", definition.getName());
        schema.put("definitionClass", definition.getClass().getName());
        schema.put("supported", supports(definition));
        schema.put(
                "owner",
                definition instanceof UnifiedChoiceParameterDefinition
                                || definition instanceof UnifiedMultiChoiceParameterDefinition
                                || definition instanceof UnifiedFormParameterDefinition
                        ? "unified-parameters"
                        : "jenkins-core");
        if (definition instanceof UnifiedFormParameterDefinition) {
            schema.put("type", "object");
        } else if (definition instanceof UnifiedMultiChoiceParameterDefinition) {
            schema.put("type", "array");
        } else if (definition instanceof BooleanParameterDefinition) {
            schema.put("type", "boolean");
        } else {
            schema.put("type", "string");
        }
        if (definition instanceof UnifiedChoiceParameterDefinition single) {
            schema.put("allowCustomValue", single.isAllowCustomValue());
            schema.put("customValuePattern", single.getCustomValuePattern());
            schema.put("customValueMaxLength", single.getCustomValueMaxLength());
            schema.put("providerBacked", single.isProviderBacked());
            schema.put("dependsOn", JSONArray.fromObject(single.getDependsOn()));
            schema.put("renderer", single.getRenderer());
            schema.put("availableExternalRenderers", JSONArray.fromObject(single.getAvailableExternalRenderers()));
        } else if (definition instanceof UnifiedMultiChoiceParameterDefinition multiple) {
            schema.put("providerBacked", multiple.isProviderBacked());
            schema.put("dependsOn", JSONArray.fromObject(multiple.getDependsOn()));
            schema.put("renderer", multiple.getRenderer());
            schema.put("availableExternalRenderers", JSONArray.fromObject(multiple.getAvailableExternalRenderers()));
        } else if (definition instanceof UnifiedFormParameterDefinition form) {
            schema.put("outputMode", form.getOutputMode());
            schema.put("renderer", form.getRenderer());
            schema.put("formSchema", JSONObject.fromObject(form.getSchemaJson()));
        }
        return schema;
    }

    static ParameterValue decode(
            Job<?, ?> job,
            ParameterDefinition definition,
            boolean present,
            Object typedValue,
            Map<String, String> dependencies,
            String snapshotId,
            ProviderOperation operation) {
        if (!supports(definition)) {
            throw new EvaluationException(
                    ErrorCode.UNSUPPORTED_DEFINITION,
                    definition.getName(),
                    "Unsupported parameter definition: " + definition.getClass().getName());
        }
        if (definition instanceof UnifiedFormParameterDefinition form) {
            if (snapshotId != null) {
                throw invalidType(definition, "Unified Form does not accept Provider snapshots.");
            }
            if (present && !(typedValue instanceof JSONObject)) {
                throw invalidType(definition, "Expected a JSON object.");
            }
            return form.createTypedValue(typedValue, present);
        }
        if (definition instanceof UnifiedChoiceParameterDefinition single) {
            ResolvedProviderOptions resolved =
                    ProviderSnapshotStore.get().resolveForBuild(job, single, dependencies, snapshotId, operation);
            String value = !present || typedValue == null || typedValue instanceof JSONNull
                    ? null
                    : requireString(definition, typedValue);
            String canonical = ParameterEvaluationService.evaluateSingle(
                    single,
                    present
                            ? (value == null ? DecodedInput.present(List.of()) : DecodedInput.present(List.of(value)))
                            : DecodedInput.absent(),
                    resolved.options());
            return new UnifiedChoiceParameterValue(
                    single.getName(),
                    canonical,
                    single.getDescription(),
                    single.getEnvironmentExport(),
                    resolved.metadata());
        }
        if (definition instanceof UnifiedMultiChoiceParameterDefinition multiple) {
            if (present && !(typedValue instanceof JSONArray)) {
                throw invalidType(definition, "Expected a JSON array.");
            }
            List<String> values = new ArrayList<>();
            if (present) {
                for (Object value : (JSONArray) typedValue) {
                    values.add(requireString(definition, value));
                }
            }
            ResolvedProviderOptions resolved =
                    ProviderSnapshotStore.get().resolveForBuild(job, multiple, dependencies, snapshotId, operation);
            List<String> canonical = ParameterEvaluationService.evaluateMultiple(
                    multiple, present ? DecodedInput.present(values) : DecodedInput.absent(), resolved.options());
            return new UnifiedMultiChoiceParameterValue(
                    multiple.getName(),
                    canonical,
                    multiple.getDescription(),
                    multiple.getEnvironmentExport(),
                    multiple.getDelimiter(),
                    resolved.metadata());
        }
        if (!present) {
            return definition.getDefaultParameterValue();
        }
        if (definition instanceof BooleanParameterDefinition booleanDefinition) {
            if (!(typedValue instanceof Boolean booleanValue)) {
                throw invalidType(definition, "Expected a JSON boolean.");
            }
            return new BooleanParameterValue(
                    booleanDefinition.getName(), booleanValue, booleanDefinition.getDescription());
        }
        if (definition instanceof ChoiceParameterDefinition choiceDefinition) {
            return choiceDefinition.createValue(requireString(definition, typedValue));
        }
        if (definition instanceof StringParameterDefinition stringDefinition) {
            String value = requireString(definition, typedValue);
            StringParameterValue parameterValue = (StringParameterValue) stringDefinition.createValue(value);
            if (stringDefinition.isTrim()) {
                parameterValue.doTrim();
            }
            return parameterValue;
        }
        throw invalidType(definition, "Unsupported typed value.");
    }

    private static String requireString(ParameterDefinition definition, Object value) {
        if (!(value instanceof String stringValue)) {
            throw invalidType(definition, "Expected a JSON string.");
        }
        return stringValue;
    }

    private static EvaluationException invalidType(ParameterDefinition definition, String message) {
        return new EvaluationException(ErrorCode.INVALID_TYPE, definition.getName(), message);
    }
}
