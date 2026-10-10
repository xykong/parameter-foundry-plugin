package io.jenkins.plugins.parameterfoundry.migration;

import hudson.model.ParameterDefinition;
import io.jenkins.plugins.parameterfoundry.companion.file.AbstractFoundryFileParameterDefinition;
import io.jenkins.plugins.parameterfoundry.companion.file.FoundryFileParameterDefinition;
import io.jenkins.plugins.parameterfoundry.companion.file.FoundryStashedFileParameterDefinition;
import io.jenkins.plugins.parameterfoundry.definition.AbstractFoundryParameterDefinition;
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition;
import io.jenkins.plugins.parameterfoundry.form.FoundryFormParameterDefinition;
import io.jenkins.plugins.parameterfoundry.model.Option;
import java.util.List;
import java.util.stream.Collectors;

/** Pipeline and Job DSL snippet for a migrated definition, shared by the page and the evidence. */
public final class MigrationSnippets {

    private MigrationSnippets() {}

    public static String forTarget(ParameterDefinition target) {
        if (target instanceof FoundryFileParameterDefinition file) {
            return "foundryFile(name: " + quote(file.getName()) + ", deliveryMode: " + quote(file.getDeliveryMode())
                    + ("workspace".equals(file.getDeliveryMode())
                            ? ", workspacePath: " + quote(file.getWorkspacePath())
                            : "")
                    + ", maxUploadBytes: " + file.getConfiguredMaxUploadBytes() + ")";
        }
        if (target instanceof AbstractFoundryFileParameterDefinition) {
            String symbol = target instanceof FoundryStashedFileParameterDefinition
                    ? "foundryStashedFile"
                    : "foundryBase64File";
            return symbol + "(name: " + quote(target.getName()) + ")";
        }
        if (target instanceof FoundryChoiceParameterDefinition choice && !choice.isMultiple()) {
            return "foundryChoice(\n"
                    + "  name: "
                    + quote(choice.getName())
                    + (choice.getDescription() == null ? "" : ",\n  description: " + quote(choice.getDescription()))
                    + ",\n  choices: "
                    + choices(choice.getChoices())
                    + ",\n  renderer: "
                    + quote(choice.getRenderer())
                    + ",\n  standardApiInputMode: " + quote(choice.getStandardApiInputMode())
                    + (choice.isProviderBacked() ? ",\n  providerId: " + quote(choice.getProviderId()) : "")
                    + (choice.isActiveChoicesSingleInput()
                            ? ",\n  activeChoicesApiDefaultMode: " + quote(choice.getActiveChoicesApiDefaultMode())
                                    + ",\n  activeChoicesApiDefaultLiteral: "
                                    + quote(choice.getActiveChoicesApiDefaultLiteral())
                                    + ",\n  activeChoicesFilterLength: " + choice.getActiveChoicesFilterLength()
                                    + ",\n  dependsOn: ["
                                    + choice.getDependsOn().stream()
                                            .map(MigrationSnippets::quote)
                                            .collect(Collectors.joining(", "))
                                    + "]"
                            : "")
                    + ("list-git-branches".equals(choice.getStandardApiInputMode())
                            ? ",\n  gitSelectionMode: " + quote(choice.getGitSelectionMode())
                            : "")
                    + ",\n  defaultMode: "
                    + quote(choice.getDefaultMode())
                    + ("explicit".equals(choice.getDefaultMode())
                                    || "list-git-branches".equals(choice.getStandardApiInputMode())
                            ? ",\n  defaultValue: " + quote(choice.getDefaultValue())
                            : "")
                    + (choice.isExtendedChoiceSingleInput()
                            ? ",\n  delimiter: " + quote(choice.getDelimiter())
                                    + (choice.isLegacyEmptyChoiceAllowed() ? ",\n  legacyEmptyChoiceAllowed: true" : "")
                                    + (choice.getLegacyDefaultLiteral() != null
                                            ? ",\n  legacyDefaultLiteral: " + quote(choice.getLegacyDefaultLiteral())
                                            : "")
                            : "")
                    + "\n)";
        }
        if (target instanceof AbstractFoundryParameterDefinition multiple && multiple.isMultiple()) {
            return "foundryChoice(\n"
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
                    + ",\n  duplicatePolicy: "
                    + quote(multiple.getDuplicatePolicy())
                    + ",\n  delimiter: "
                    + quote(multiple.getDelimiter())
                    + (multiple instanceof FoundryChoiceParameterDefinition choice
                            ? ",\n  environmentExportOrder: " + quote(choice.getEnvironmentExportOrder())
                                    + (choice.isLegacyEmptyChoiceAllowed() ? ",\n  legacyEmptyChoiceAllowed: true" : "")
                                    + (choice.getLegacyDefaultLiteral() != null
                                            ? ",\n  legacyDefaultLiteral: " + quote(choice.getLegacyDefaultLiteral())
                                            : "")
                            : "")
                    + "\n)";
        }
        if (target instanceof FoundryFormParameterDefinition form) {
            return "foundryForm(\n  name: "
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
        return "'"
                + value.replace("\\", "\\\\")
                        .replace("'", "\\'")
                        .replace("\r", "\\r")
                        .replace("\n", "\\n") + "'";
    }
}
