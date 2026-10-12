package io.jenkins.plugins.parameterfoundry.evaluation;

import hudson.model.StringParameterValue;
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition;
import io.jenkins.plugins.parameterfoundry.model.Option;
import io.jenkins.plugins.parameterfoundry.snapshot.ProviderSnapshotMetadata;
import io.jenkins.plugins.parameterfoundry.validation.DefinitionValidationService;
import java.util.List;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/** Literal choice source transports, independent of selectable UI defaults. */
public final class ActiveChoicesCompatibility {
    private ActiveChoicesCompatibility() {}

    public static boolean enabled(FoundryChoiceParameterDefinition definition) {
        return "active-choices".equals(definition.getStandardApiInputMode());
    }

    public static void requireCompatible(FoundryChoiceParameterDefinition definition) {
        if (!enabled(definition))
            throw new EvaluationException(
                    ErrorCode.CONFIGURATION_INVALID,
                    definition.getName(),
                    "Active Choices compatibility is not enabled.");
        DefinitionValidationService.requireValid(definition);
    }

    public static String cli(String raw) {
        return raw == null ? "" : raw;
    }

    public static String standard(String[] fields) {
        if (fields == null) return null;
        if (fields.length != 1) throw new IllegalArgumentException("Expected exactly one parameter field.");
        return cli(fields[0]);
    }

    public static String form(JSONObject submitted) {
        if (submitted.containsKey("file"))
            throw new EvaluationException(
                    ErrorCode.UNSUPPORTED_DEFINITION,
                    submitted.optString("name", ""),
                    "File payloads require a Foundry File parameter.");
        Object raw = submitted.get("value");
        return raw instanceof JSONArray array ? array.join(",", true) : raw == null ? "" : String.valueOf(raw);
    }

    public static StringParameterValue defaultValue(
            FoundryChoiceParameterDefinition definition, List<Option> options, ProviderSnapshotMetadata metadata) {
        requireCompatible(definition);
        String literal;
        if ("literal".equals(definition.getActiveChoicesApiDefaultMode())) {
            literal = definition.getActiveChoicesApiDefaultLiteral();
        } else if (options.isEmpty()) {
            literal = "";
        } else if (options.get(0).getSourceChoiceMetadata() != null) {
            List<String> selected = options.stream()
                    .filter(option -> option.getSourceChoiceMetadata() != null
                            && option.getSourceChoiceMetadata().apiSelected())
                    .map(option -> option.getSourceChoiceMetadata().selectedApiValue())
                    .toList();
            literal = selected.isEmpty()
                    ? options.get(0).getSourceChoiceMetadata().apiValue()
                    : String.join(",", selected);
        } else {
            literal = options.get(0).getValue();
        }
        return new StringParameterValue(definition.getName(), cli(literal));
    }

    public static StringParameterValue value(
            FoundryChoiceParameterDefinition definition, String literal, ProviderSnapshotMetadata metadata) {
        requireCompatible(definition);
        return new StringParameterValue(definition.getName(), cli(literal), definition.getDescription());
    }
}
