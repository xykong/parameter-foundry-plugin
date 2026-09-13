package io.jenkins.plugins.unifiedparameters.migration.activechoices;

import hudson.Extension;
import hudson.model.ParameterDefinition;
import io.jenkins.plugins.unifiedparameters.definition.UnifiedChoiceParameterDefinition;
import io.jenkins.plugins.unifiedparameters.migration.MigrationClassification;
import io.jenkins.plugins.unifiedparameters.migration.MigrationPlan;
import io.jenkins.plugins.unifiedparameters.migration.MigrationText;
import io.jenkins.plugins.unifiedparameters.migration.SourceMigrationAdapter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Extension
public final class ActiveChoicesMigrationAdapter extends SourceMigrationAdapter {

    private static final String CHOICE = "org.biouno.unochoice.ChoiceParameter";
    private static final String CASCADE = "org.biouno.unochoice.CascadeChoiceParameter";
    private static final String REFERENCE = "org.biouno.unochoice.DynamicReferenceParameter";
    private static final String PT_RADIO = "PT_RADIO";
    private static final String PT_MULTI_SELECT = "PT_MULTI_SELECT";
    private static final String TEXT_BOX = "ET_TEXT_BOX";
    private static final String ORDERED_LIST = "ET_ORDERED_LIST";
    private static final String UNORDERED_LIST = "ET_UNORDERED_LIST";
    private static final String FORMATTED_HTML = "ET_FORMATTED_HTML";
    private static final String FORMATTED_HIDDEN_HTML = "ET_FORMATTED_HIDDEN_HTML";
    private static final Set<String> REFERENCE_TYPES =
            Set.of(TEXT_BOX, ORDERED_LIST, UNORDERED_LIST, FORMATTED_HTML, FORMATTED_HIDDEN_HTML);

    @Override
    public String getSourcePluginId() {
        return "uno-choice";
    }

    @Override
    public boolean supports(ParameterDefinition definition) {
        return definition != null
                && List.of(CHOICE, CASCADE, REFERENCE)
                        .contains(definition.getClass().getName());
    }

    @Override
    public MigrationPlan inspect(ParameterDefinition definition) {
        if (!supports(definition)) {
            throw new IllegalArgumentException("Unsupported source definition");
        }
        String className = definition.getClass().getName();
        List<String> dependencies = (CASCADE.equals(className) || REFERENCE.equals(className))
                ? parseDependencies(readString(definition, "getReferencedParameters"))
                : List.of();
        if (REFERENCE.equals(className)) {
            String choiceType = firstNonEmpty(readString(definition, "getChoiceType"), FORMATTED_HTML);
            boolean omitValueField = readBoolean(definition, "getOmitValueField", false);
            ReferenceSemantics semantics = referenceSemantics(choiceType);
            return new MigrationPlan(
                    semantics.classification(),
                    semantics.reason(),
                    null,
                    referenceDecisions(semantics, omitValueField, dependencies),
                    semantics.classification() == MigrationClassification.D
                            ? null
                            : referenceSkeleton(definition, semantics, dependencies),
                    verificationFixture(definition, dependencies, true));
        }
        String script = readString(definition, "getScript");
        String choiceType = firstNonEmpty(readString(definition, "getChoiceType"));
        boolean filterable = Boolean.parseBoolean(firstNonEmpty(readString(definition, "getFilterable")));
        boolean multiple = PT_MULTI_SELECT.equals(choiceType);

        java.util.Optional<ActiveChoicesScriptAnalysis.LiteralChoices> literal =
                script.isBlank() ? java.util.Optional.empty() : ActiveChoicesScriptAnalysis.literalChoices(script);
        if (literal.isPresent() && !choiceType.startsWith("ET_")) {
            return literalPlan(definition, dependencies, literal.get(), choiceType, multiple, filterable);
        }

        ActiveChoicesScriptAnalysis.ScriptShape shape = ActiveChoicesScriptAnalysis.shape(script);
        java.util.Map<MigrationText, MigrationText> facts = new java.util.LinkedHashMap<>();
        facts.put(MigrationText.of("MigrationFactSourceElement"), MigrationText.value(className));
        facts.put(
                MigrationText.of("MigrationFactSourceChoiceType"),
                choiceType.isEmpty() ? MigrationText.of("MigrationValueUnset") : MigrationText.value(choiceType));
        facts.put(
                MigrationText.of("MigrationFactSourceScriptShape"),
                MigrationText.value(shape.name().toLowerCase(Locale.ROOT).replace('_', '-')));
        facts.put(
                MigrationText.of("MigrationFactSourceFilterable"),
                MigrationText.of(filterable ? "MigrationValueEnabled" : "MigrationValueDisabled"));
        ActiveChoicesScriptAnalysis.redactedEndpoint(script)
                .ifPresent(endpoint ->
                        facts.put(MigrationText.of("MigrationFactDetectedEndpoint"), MigrationText.value(endpoint)));
        List<String> detectedFields = ActiveChoicesScriptAnalysis.detectedFields(script);
        if (!detectedFields.isEmpty()) {
            facts.put(
                    MigrationText.of("MigrationFactDetectedResponseFields"),
                    MigrationText.value(String.join(", ", detectedFields)));
        }
        if (shape == ActiveChoicesScriptAnalysis.ScriptShape.CONTROLLER_OR_SHELL) {
            return new MigrationPlan(
                    MigrationClassification.D,
                    MigrationText.of("MigrationReasonActiveChoicesControllerShell"),
                    null,
                    List.of(
                            MigrationText.of("MigrationDecisionActiveChoicesControllerDescribe"),
                            MigrationText.of("MigrationDecisionActiveChoicesControllerReplace"),
                            MigrationText.of("MigrationDecisionActiveChoicesControllerConfirm")),
                    null,
                    verificationFixture(definition, dependencies, false),
                    facts);
        }
        String skeleton = choiceSkeleton(
                definition, dependencies, shape == ActiveChoicesScriptAnalysis.ScriptShape.NETWORK, filterable);
        List<MigrationText> decisions = new java.util.ArrayList<>(List.of(
                MigrationText.of("MigrationDecisionActiveChoicesChooseProvider"),
                MigrationText.of("MigrationDecisionActiveChoicesConfirmBehavior")));
        if (shape == ActiveChoicesScriptAnalysis.ScriptShape.NETWORK) {
            decisions.add(MigrationText.of("MigrationDecisionActiveChoicesRestProvider"));
            if (!detectedFields.isEmpty()) {
                decisions.add(MigrationText.of(
                        "MigrationDecisionActiveChoicesDetectedFields", String.join(", ", detectedFields)));
            }
        } else if (script.strip().length() <= 80) {
            decisions.add(MigrationText.of("MigrationDecisionActiveChoicesShortScript"));
        }
        return new MigrationPlan(
                MigrationClassification.C,
                MigrationText.of("MigrationReasonActiveChoicesScriptRefactor"),
                null,
                decisions,
                skeleton,
                verificationFixture(definition, dependencies, false),
                facts);
    }

    /** Deterministic conversion of a literal-only script into inline choices. */
    private static MigrationPlan literalPlan(
            ParameterDefinition definition,
            List<String> dependencies,
            ActiveChoicesScriptAnalysis.LiteralChoices literal,
            String choiceType,
            boolean multiple,
            boolean filterable) {
        UnifiedChoiceParameterDefinition target = new UnifiedChoiceParameterDefinition(definition.getName());
        target.setSelectionMode(multiple ? "multiple" : "single");
        target.setRenderer(rendererFor(choiceType, multiple, filterable));
        target.setChoices(literal.options());
        target.setDependsOn(dependencies);
        List<String> selected = literal.selectedValues();
        if (!selected.isEmpty()) {
            target.setDefaultMode("explicit");
            if (multiple) {
                target.setDefaultValues(selected);
            } else {
                target.setDefaultValue(selected.get(0));
            }
        }
        java.util.Map<MigrationText, MigrationText> facts = new java.util.LinkedHashMap<>();
        facts.put(
                MigrationText.of("MigrationFactSourceElement"), MigrationText.of("MigrationValueActiveChoicesLiteral"));
        facts.put(
                MigrationText.of("MigrationFactSourceOptions"),
                MigrationText.value(String.valueOf(literal.options().size())));
        facts.put(
                MigrationText.of("MigrationFactSourceSelectionMarkers"),
                selected.isEmpty()
                        ? MigrationText.of("MigrationValueNone")
                        : MigrationText.value(String.join(", ", selected)));
        facts.put(MigrationText.of("MigrationFactTargetRenderer"), MigrationText.value(target.getRenderer()));
        facts.put(MigrationText.of("MigrationFactTargetDefaultMode"), MigrationText.value(target.getDefaultMode()));
        List<MigrationText> decisions = new java.util.ArrayList<>();
        if (selected.isEmpty()) {
            decisions.add(MigrationText.of("MigrationDecisionActiveChoicesLiteralDefault"));
        }
        return new MigrationPlan(
                decisions.isEmpty() ? MigrationClassification.A : MigrationClassification.B,
                decisions.isEmpty()
                        ? MigrationText.of("MigrationReasonActiveChoicesLiteralDirect")
                        : MigrationText.of("MigrationReasonActiveChoicesLiteralDefault"),
                target,
                decisions,
                null,
                verificationFixture(definition, dependencies, false),
                facts);
    }

    private static String rendererFor(String choiceType, boolean multiple, boolean filterable) {
        if (multiple) {
            return "checkbox";
        }
        if (PT_RADIO.equals(choiceType)) {
            return "radio";
        }
        return filterable ? "searchable" : "select";
    }

    private static String choiceSkeleton(
            ParameterDefinition definition, List<String> dependencies, boolean network, boolean filterable) {
        String dependencyLine = dependencies.isEmpty()
                ? ""
                : "    dependsOn: ["
                        + dependencies.stream()
                                .map(ActiveChoicesMigrationAdapter::quote)
                                .reduce((left, right) -> left + ", " + right)
                                .orElse("")
                        + "],\n";
        String providerSkeleton = network
                ? "unifiedRestProvider(\n"
                        + "    id: 'TODO-rest-provider',\n"
                        + "    connectionProfileId: '<administrator-selected-connection-profile>',\n"
                        + "    relativePath: '<reviewed-source-path>',\n"
                        + "    format: 'json',\n"
                        + "    valuePath: '<reviewed-value-path>',\n"
                        + "    labelPath: '<reviewed-label-path>'\n"
                        + ")\n"
                : "unifiedStaticProvider(\n"
                        + "    id: 'TODO-governed-provider',\n"
                        + "    options: [/* reviewed source options */]\n"
                        + ")\n";
        String providerId = network ? "TODO-rest-provider" : "TODO-governed-provider";
        return providerSkeleton
                + "unifiedChoice(\n"
                + "    name: "
                + quote(definition.getName())
                + ",\n"
                + "    providerId: '"
                + providerId
                + "',\n"
                + dependencyLine
                + "    renderer: "
                + quote(filterable ? "searchable" : "select")
                + "\n"
                + ")";
    }

    private static String referenceSkeleton(
            ParameterDefinition definition, ReferenceSemantics semantics, List<String> dependencies) {
        String dependencyComment = dependencies.isEmpty()
                ? ""
                : "    // Preserve source dependencies as field visibility or Provider inputs: "
                        + dependencies.stream()
                                .map(ActiveChoicesMigrationAdapter::quote)
                                .reduce((left, right) -> left + ", " + right)
                                .orElse("")
                        + "\n";
        String fields =
                switch (semantics.type()) {
                    case TEXT_BOX -> "field(" + quote(definition.getName()) + ", 'string')";
                    case ORDERED_LIST, UNORDERED_LIST -> "field(" + quote(definition.getName()) + ", 'content')";
                    case FORMATTED_HTML, FORMATTED_HIDDEN_HTML -> "/* TODO reviewed typed fields */";
                    default -> "/* TODO reviewed typed fields */";
                };
        return "unifiedForm(\n"
                + "    name: "
                + quote(definition.getName())
                + ",\n"
                + dependencyComment
                + "    fields: ["
                + fields
                + "],\n"
                + "    output: 'map',\n"
                + "    renderer: 'schema'\n"
                + ")";
    }

    private static List<MigrationText> referenceDecisions(
            ReferenceSemantics semantics, boolean omitValueField, List<String> dependencies) {
        MigrationText dependencyDecision = dependencies.isEmpty()
                ? MigrationText.of("MigrationDecisionActiveChoicesReferenceNoDependencies")
                : MigrationText.of("MigrationDecisionActiveChoicesReferenceDependencies", String.valueOf(dependencies));
        MigrationText valueDecision =
                switch (semantics.type()) {
                    case TEXT_BOX -> MigrationText.of("MigrationDecisionActiveChoicesReferenceTextBox");
                    case ORDERED_LIST, UNORDERED_LIST ->
                        MigrationText.of("MigrationDecisionActiveChoicesReferenceList");
                    case FORMATTED_HTML -> MigrationText.of("MigrationDecisionActiveChoicesReferenceHtml");
                    case FORMATTED_HIDDEN_HTML -> MigrationText.of("MigrationDecisionActiveChoicesReferenceHiddenHtml");
                    default -> MigrationText.of("MigrationDecisionActiveChoicesReferenceOther");
                };
        MigrationText omitDecision = omitValueField
                ? MigrationText.of("MigrationDecisionActiveChoicesOmitValueField")
                : MigrationText.of("MigrationDecisionActiveChoicesKeepValueField");
        return List.of(
                MigrationText.of("MigrationDecisionActiveChoicesReferenceMode", semantics.type()),
                dependencyDecision,
                valueDecision,
                omitDecision,
                MigrationText.of("MigrationDecisionActiveChoicesVerifyOutput"));
    }

    private static ReferenceSemantics referenceSemantics(String choiceType) {
        String normalized = firstNonEmpty(choiceType, FORMATTED_HTML);
        if (!REFERENCE_TYPES.contains(normalized)) {
            return new ReferenceSemantics(
                    normalized,
                    MigrationClassification.D,
                    MigrationText.of("MigrationReasonActiveChoicesUnknownReference"));
        }
        if (FORMATTED_HIDDEN_HTML.equals(normalized)) {
            return new ReferenceSemantics(
                    normalized, MigrationClassification.D, MigrationText.of("MigrationReasonActiveChoicesHiddenHtml"));
        }
        return new ReferenceSemantics(
                normalized,
                MigrationClassification.C,
                MigrationText.of("MigrationReasonActiveChoicesDynamicReference"));
    }

    private static MigrationText verificationFixture(
            ParameterDefinition definition, List<String> dependencies, boolean form) {
        return MigrationText.of(
                form ? "MigrationCaseActiveChoicesForm" : "MigrationCaseActiveChoicesOptions",
                definition.getName(),
                String.valueOf(dependencies));
    }

    private static String readString(Object source, String methodName) {
        try {
            Method method = source.getClass().getMethod(methodName);
            Object value = method.invoke(source);
            return value == null ? "" : String.valueOf(value);
        } catch (NoSuchMethodException exception) {
            return "";
        } catch (IllegalAccessException | InvocationTargetException exception) {
            return "";
        }
    }

    private static List<String> parseDependencies(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .distinct()
                .toList();
    }

    private static boolean readBoolean(Object source, String methodName, boolean defaultValue) {
        try {
            Method method = source.getClass().getMethod(methodName);
            Object value = method.invoke(source);
            return value instanceof Boolean booleanValue ? booleanValue : defaultValue;
        } catch (NoSuchMethodException exception) {
            return defaultValue;
        } catch (IllegalAccessException | InvocationTargetException exception) {
            return defaultValue;
        }
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String quote(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    private record ReferenceSemantics(String type, MigrationClassification classification, MigrationText reason) {}
}
