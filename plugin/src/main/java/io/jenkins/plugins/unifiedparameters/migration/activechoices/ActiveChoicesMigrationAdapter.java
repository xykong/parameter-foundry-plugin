package io.jenkins.plugins.unifiedparameters.migration.activechoices;

import hudson.Extension;
import hudson.model.ParameterDefinition;
import io.jenkins.plugins.unifiedparameters.definition.UnifiedChoiceParameterDefinition;
import io.jenkins.plugins.unifiedparameters.migration.MigrationClassification;
import io.jenkins.plugins.unifiedparameters.migration.MigrationPlan;
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
        java.util.Map<String, String> facts = new java.util.LinkedHashMap<>();
        facts.put("Source element", className);
        facts.put("Source choice type", choiceType.isEmpty() ? "(unset)" : choiceType);
        facts.put("Source script shape", shape.name().toLowerCase(Locale.ROOT).replace('_', '-'));
        facts.put("Source filterable", filterable ? "enabled" : "disabled");
        ActiveChoicesScriptAnalysis.redactedEndpoint(script)
                .ifPresent(endpoint -> facts.put("Detected endpoint", endpoint));
        List<String> detectedFields = ActiveChoicesScriptAnalysis.detectedFields(script);
        if (!detectedFields.isEmpty()) {
            facts.put("Detected response fields", String.join(", ", detectedFields));
        }
        if (shape == ActiveChoicesScriptAnalysis.ScriptShape.CONTROLLER_OR_SHELL) {
            return new MigrationPlan(
                    MigrationClassification.D,
                    "The source script touches the controller or a shell command, so no governed Provider equivalent can be inferred without a business redesign.",
                    null,
                    List.of(
                            "Describe the business input the script produces; do not copy controller, file, or shell access into the target.",
                            "Replace the script with a governed Provider (REST, Managed Config, or a reviewed domain Provider) or move the work into a Pipeline step.",
                            "Confirm default, empty, failure, and authorization behavior with the owning team before scheduling the migration."),
                    null,
                    verificationFixture(definition, dependencies, false),
                    facts);
        }
        String skeleton = choiceSkeleton(
                definition, dependencies, shape == ActiveChoicesScriptAnalysis.ScriptShape.NETWORK, filterable);
        List<String> decisions = new java.util.ArrayList<>(List.of(
                "Choose a reviewed Script, REST, Managed Config, or domain Provider without executing the source script during migration.",
                "Confirm default, empty, fallback, and error behavior for the normalized option schema."));
        if (shape == ActiveChoicesScriptAnalysis.ScriptShape.NETWORK) {
            decisions.add(
                    "Replace the script's HTTP call with a governed REST Provider: bind the authority through an administrator-owned Connection Profile and map the response fields to value, label, description, and group paths.");
            if (!detectedFields.isEmpty()) {
                decisions.add("The script reads these response fields: " + String.join(", ", detectedFields)
                        + "; confirm each target path mapping.");
            }
        } else if (script.strip().length() <= 80) {
            decisions.add(
                    "The source script is short but not a literal list; confirm whether it computes a static list that can become inline choices.");
        }
        return new MigrationPlan(
                MigrationClassification.C,
                "Active Choices script intent requires a governed Provider/Form refactor; source code was not executed.",
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
        java.util.Map<String, String> facts = new java.util.LinkedHashMap<>();
        facts.put("Source element", "Active Choices literal option list");
        facts.put("Source options", String.valueOf(literal.options().size()));
        facts.put("Source selection markers", selected.isEmpty() ? "(none)" : String.join(", ", selected));
        facts.put("Target renderer", target.getRenderer());
        facts.put("Target default mode", target.getDefaultMode());
        List<String> decisions = new java.util.ArrayList<>();
        if (selected.isEmpty()) {
            decisions.add(
                    "The source script marks no selected value while the source form preselects the first option; choose defaultMode first to keep that behavior or none to require an explicit choice.");
        }
        return new MigrationPlan(
                decisions.isEmpty() ? MigrationClassification.A : MigrationClassification.B,
                decisions.isEmpty()
                        ? "The source script is a literal option list, so the target choices and defaults are equivalent without executing Groovy."
                        : "The source script is a literal option list; only the default policy needs an explicit decision.",
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

    private static List<String> referenceDecisions(
            ReferenceSemantics semantics, boolean omitValueField, List<String> dependencies) {
        String dependencyDecision = dependencies.isEmpty()
                ? "Confirm that the Dynamic Reference has no source parameter dependencies."
                : "Preserve Dynamic Reference dependencies "
                        + dependencies
                        + " as typed field visibility or governed Provider inputs; do not execute the source script.";
        String valueDecision =
                switch (semantics.type()) {
                    case TEXT_BOX ->
                        "Confirm the string default, required/empty behavior, and whether the build receives a raw string or a named form field.";
                    case ORDERED_LIST, UNORDERED_LIST ->
                        "Confirm whether the rendered list is presentation-only; if it is a build input, map it explicitly to a typed choice or multi-choice field.";
                    case FORMATTED_HTML ->
                        "Identify business fields in the formatted HTML and replace them with typed Unified Form fields or a reviewed Renderer companion.";
                    case FORMATTED_HIDDEN_HTML ->
                        "Confirm whether the hidden HTML carries a real build value; without an explicit field mapping it remains a manual class D redesign.";
                    default ->
                        "Confirm the business output and renderer mapping for the unsupported source element type.";
                };
        String omitDecision = omitValueField
                ? "The source omits its value field; do not infer a build value from the rendered markup."
                : "Confirm whether the source value field is a business input; do not copy generated markup as a value.";
        return List.of(
                "Source Dynamic Reference mode: " + semantics.type() + ".",
                dependencyDecision,
                valueDecision,
                omitDecision,
                "Verify the canonical output across browser, Typed API, standard build, CLI, remote triggers, and Rebuild.");
    }

    private static ReferenceSemantics referenceSemantics(String choiceType) {
        String normalized = firstNonEmpty(choiceType, FORMATTED_HTML);
        if (!REFERENCE_TYPES.contains(normalized)) {
            return new ReferenceSemantics(
                    normalized,
                    MigrationClassification.D,
                    "Unknown Active Choices Dynamic Reference element type has no proven typed contract; retain it or document an explicit business-field redesign.");
        }
        if (FORMATTED_HIDDEN_HTML.equals(normalized)) {
            return new ReferenceSemantics(
                    normalized,
                    MigrationClassification.D,
                    "Formatted hidden HTML has no portable typed value contract; retain it or redesign it with an explicit business-field mapping.");
        }
        return new ReferenceSemantics(
                normalized,
                MigrationClassification.C,
                "Active Choices Dynamic Reference presentation requires a governed typed Form refactor; source HTML, JavaScript, and scripts were not copied.");
    }

    private static String verificationFixture(ParameterDefinition definition, List<String> dependencies, boolean form) {
        return "For "
                + definition.getName()
                + ", compare each canonical dependency tuple "
                + dependencies
                + " across browser resolve, Typed API, standard build, CLI, fallback/error, and Rebuild; "
                + (form
                        ? "verify CSP-safe typed rendering and structured output."
                        : "compare normalized option values and defaults.");
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

    private record ReferenceSemantics(String type, MigrationClassification classification, String reason) {}
}
