package io.jenkins.plugins.parameterfoundry.api;

import hudson.model.BooleanParameterDefinition;
import hudson.model.BooleanParameterValue;
import hudson.model.ChoiceParameterDefinition;
import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import io.jenkins.plugins.parameterfoundry.companion.file.AbstractFoundryFileParameterDefinition;
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition;
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterValue;
import io.jenkins.plugins.parameterfoundry.evaluation.DecodedInput;
import io.jenkins.plugins.parameterfoundry.evaluation.ErrorCode;
import io.jenkins.plugins.parameterfoundry.evaluation.EvaluationException;
import io.jenkins.plugins.parameterfoundry.evaluation.ParameterEvaluationService;
import io.jenkins.plugins.parameterfoundry.form.FoundryFormParameterDefinition;
import io.jenkins.plugins.parameterfoundry.provider.ProviderOperation;
import io.jenkins.plugins.parameterfoundry.snapshot.ProviderSnapshotStore;
import io.jenkins.plugins.parameterfoundry.snapshot.ResolvedProviderOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.sf.json.JSONArray;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;

final class TypedParameterAdapterRegistry {

    private TypedParameterAdapterRegistry() {}

    static boolean supports(ParameterDefinition definition) {
        return definition instanceof FoundryChoiceParameterDefinition
                || definition instanceof FoundryFormParameterDefinition
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
                definition instanceof AbstractFoundryFileParameterDefinition
                                || definition instanceof FoundryChoiceParameterDefinition
                                || definition instanceof FoundryFormParameterDefinition
                        ? "parameter-foundry"
                        : "jenkins-core");
        if (definition instanceof FoundryFormParameterDefinition) {
            schema.put("type", "object");
        } else if (definition instanceof FoundryChoiceParameterDefinition choice && choice.isMultiple()) {
            schema.put("type", "array");
        } else if (definition instanceof BooleanParameterDefinition) {
            schema.put("type", "boolean");
        } else {
            schema.put("type", "string");
        }
        if (definition instanceof FoundryChoiceParameterDefinition single) {
            schema.put("selectionMode", single.getSelectionMode());
            schema.put("standardApiInputMode", single.getStandardApiInputMode());
            schema.put("gitSelectionMode", single.getGitSelectionMode());
            schema.put("providerBacked", single.isProviderBacked());
            schema.put("dependsOn", JSONArray.fromObject(single.getDependsOn()));
            schema.put("renderer", single.getRenderer());
            schema.put("availableExternalRenderers", JSONArray.fromObject(single.getAvailableExternalRenderers()));
            if (single.isMultiple()) {
                schema.put("selectedDisplay", single.getSelectedDisplay());
                schema.put("table", single.getTable());
            } else {
                schema.put("allowCustomValue", single.isAllowCustomValue());
                schema.put("customValuePattern", single.getCustomValuePattern());
                schema.put("customValueMaxLength", single.getCustomValueMaxLength());
            }
        } else if (definition instanceof FoundryFormParameterDefinition form) {
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
        if (definition instanceof FoundryFormParameterDefinition form) {
            if (snapshotId != null) {
                throw invalidType(definition, "Foundry Form does not accept Provider snapshots.");
            }
            if (present && !(typedValue instanceof JSONObject)) {
                throw invalidType(definition, "Expected a JSON object.");
            }
            return form.createTypedValue(typedValue, present);
        }
        if (definition instanceof FoundryChoiceParameterDefinition choice) {
            if (choice.isMultiple()) {
                return decodeMultiple(job, choice, present, typedValue, dependencies, snapshotId, operation);
            }
            String value = !present || typedValue == null || typedValue instanceof JSONNull
                    ? null
                    : requireString(definition, typedValue);
            DecodedInput input = present
                    ? (value == null ? DecodedInput.present(List.of()) : DecodedInput.present(List.of(value)))
                    : DecodedInput.absent();
            ResolvedProviderOptions resolved = ProviderSnapshotStore.get()
                    .resolveForBuild(job, choice, dependencies, snapshotId, operation, choice.needsOptions(input));
            String canonical = ParameterEvaluationService.evaluateSingle(choice, input, resolved.options());
            return FoundryChoiceParameterValue.single(
                    choice.getName(),
                    canonical,
                    choice.getDescription(),
                    choice.getEnvironmentExport(),
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

    private static ParameterValue decodeMultiple(
            Job<?, ?> job,
            FoundryChoiceParameterDefinition definition,
            boolean present,
            Object typedValue,
            Map<String, String> dependencies,
            String snapshotId,
            ProviderOperation operation) {
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
                ProviderSnapshotStore.get().resolveForBuild(job, definition, dependencies, snapshotId, operation);
        if (!present
                && io.jenkins.plugins.parameterfoundry.evaluation.ExtendedChoiceDefaultCompatibility.enabled(
                        definition)) {
            return io.jenkins.plugins.parameterfoundry.evaluation.ExtendedChoiceDefaultCompatibility.evaluate(
                    (FoundryChoiceParameterDefinition) definition);
        }
        List<String> canonical = ParameterEvaluationService.evaluateMultiple(
                definition,
                present ? DecodedInput.present(values) : DecodedInput.absent(),
                resolved.options(),
                resolved.matrix());
        return definition.createEvaluatedMultipleValue(canonical, values, resolved.metadata());
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
