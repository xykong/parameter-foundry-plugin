package io.jenkins.plugins.unifiedparameters.migration;

import hudson.model.ParameterDefinition;
import io.jenkins.plugins.unifiedparameters.definition.AbstractUnifiedParameterDefinition;
import io.jenkins.plugins.unifiedparameters.definition.UnifiedChoiceParameterDefinition;
import io.jenkins.plugins.unifiedparameters.form.UnifiedFormParameterDefinition;
import io.jenkins.plugins.unifiedparameters.model.Option;
import java.util.List;
import java.util.stream.Collectors;

/** Pipeline and Job DSL snippet for a migrated definition, shared by the page and the evidence. */
public final class MigrationSnippets {

    private MigrationSnippets() {}

    public static String forTarget(ParameterDefinition target) {
        if (target instanceof UnifiedChoiceParameterDefinition choice && !choice.isMultiple()) {
            return "unifiedChoice(\n"
                    + "  name: "
                    + quote(choice.getName())
                    + ",\n  choices: "
                    + choices(choice.getChoices())
                    + ",\n  renderer: "
                    + quote(choice.getRenderer())
                    + ",\n  defaultMode: "
                    + quote(choice.getDefaultMode())
                    + ("explicit".equals(choice.getDefaultMode())
                            ? ",\n  defaultValue: " + quote(choice.getDefaultValue())
                            : "")
                    + "\n)";
        }
        if (target instanceof AbstractUnifiedParameterDefinition multiple && multiple.isMultiple()) {
            return "unifiedChoice(\n"
                    + "  name: "
                    + quote(multiple.getName())
                    + ",\n  selectionMode: 'multiple',\n  choices: "
                    + choices(multiple.getChoices())
                    + ",\n  renderer: "
                    + quote(multiple.getRenderer())
                    + ",\n  defaultMode: "
                    + quote(multiple.getDefaultMode())
                    + ("explicit".equals(multiple.getDefaultMode())
                            ? ",\n  defaultValues: ["
                                    + multiple.getDefaultValues().stream()
                                            .map(MigrationSnippets::quote)
                                            .collect(Collectors.joining(", "))
                                    + "]"
                            : "")
                    + ",\n  standardApiInputMode: "
                    + quote(multiple.getStandardApiInputMode())
                    + ",\n  delimiter: "
                    + quote(multiple.getDelimiter())
                    + (multiple instanceof UnifiedChoiceParameterDefinition choice
                            ? ",\n  environmentExportOrder: " + quote(choice.getEnvironmentExportOrder())
                            : "")
                    + "\n)";
        }
        if (target instanceof UnifiedFormParameterDefinition form) {
            return "unifiedForm(\n  name: "
                    + quote(form.getName())
                    + ",\n  fields: [/* reviewed typed fields */],\n  renderer: 'schema'\n)";
        }
        return "// No automatic target was generated.";
    }

    public static String choices(List<Option> options) {
        int limit = Math.min(options.size(), 200);
        String encoded = options.subList(0, limit).stream()
                .map(option -> "[value: " + quote(option.getValue()) + "]")
                .collect(Collectors.joining(", "));
        if (limit < options.size()) {
            encoded += ", /* " + (options.size() - limit) + " more options omitted */";
        }
        return "[" + encoded + "]";
    }

    public static String quote(String value) {
        if (value == null) {
            return "null";
        }
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }
}
