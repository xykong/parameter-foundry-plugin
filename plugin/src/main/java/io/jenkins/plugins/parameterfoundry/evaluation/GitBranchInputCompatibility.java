package io.jenkins.plugins.parameterfoundry.evaluation;

import io.jenkins.plugins.parameterfoundry.definition.AbstractFoundryParameterDefinition;
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition;
import java.util.ArrayList;
import java.util.List;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/** Source transport rules for the List Git Branches parameter migration. */
public final class GitBranchInputCompatibility {

    private GitBranchInputCompatibility() {}

    public static boolean enabled(AbstractFoundryParameterDefinition definition) {
        return "list-git-branches".equals(definition.getStandardApiInputMode());
    }

    public static DecodedInput fromCli(String value) {
        return value == null || value.isEmpty() ? DecodedInput.absent() : literal(value);
    }

    public static DecodedInput fromStandardFields(String[] values) {
        if (values != null && values.length > 0 && values[0] != null && !values[0].isBlank()) {
            return literal(values[0]);
        }
        return DecodedInput.absent();
    }

    public static DecodedInput fromForm(FoundryChoiceParameterDefinition definition, JSONObject submitted) {
        Object raw = submitted.opt("value");
        if (raw instanceof String value && !value.isEmpty()) {
            return literal(value);
        }
        if (raw instanceof JSONArray array && !array.isEmpty()) {
            List<String> values = new ArrayList<>();
            for (int index = 0; index < array.size(); index++) {
                values.add(array.getString(index));
            }
            String joined = String.join(",", values);
            if (!joined.isEmpty()) {
                return literal(joined);
            }
        }
        // The source form uses the stored literal, even when TOP would resolve a Git default.
        return literal(String.valueOf(definition.getDefaultValue()));
    }

    public static boolean requiresOptions(FoundryChoiceParameterDefinition definition, DecodedInput input) {
        return !input.present() && "first".equals(definition.getDefaultMode());
    }

    private static DecodedInput literal(String value) {
        return DecodedInput.present(List.of(value));
    }
}
