package io.jenkins.plugins.unifiedparameters.migration;

import hudson.model.ParameterDefinition;
import io.jenkins.plugins.unifiedparameters.definition.AbstractUnifiedParameterDefinition;
import io.jenkins.plugins.unifiedparameters.definition.UnifiedChoiceParameterDefinition;
import io.jenkins.plugins.unifiedparameters.definition.UnifiedMultiChoiceParameterDefinition;
import io.jenkins.plugins.unifiedparameters.model.Option;
import java.util.List;
import java.util.stream.Collectors;

public final class MigrationPreview {

    private static final List<String> VERIFICATION_CASES = List.of(
            "Explicit value and default/absent input",
            "Explicit empty and invalid value rejection",
            "Standard REST, Typed REST, CLI, and Pipeline build",
            "Environment export and Rebuild");

    private final String sourcePluginId;
    private final String parameterName;
    private final MigrationClassification classification;
    private final String reason;
    private final List<String> decisions;
    private final String targetType;
    private final String targetSummary;
    private final String pipelineSnippet;
    private final List<String> verificationCases;
    private final boolean automaticallyApplicable;

    private MigrationPreview(
            String sourcePluginId,
            String parameterName,
            MigrationClassification classification,
            String reason,
            List<String> decisions,
            String targetType,
            String targetSummary,
            String pipelineSnippet,
            List<String> verificationCases,
            boolean automaticallyApplicable) {
        this.sourcePluginId = sourcePluginId;
        this.parameterName = parameterName;
        this.classification = classification;
        this.reason = reason;
        this.decisions = decisions;
        this.targetType = targetType;
        this.targetSummary = targetSummary;
        this.pipelineSnippet = pipelineSnippet;
        this.verificationCases = verificationCases;
        this.automaticallyApplicable = automaticallyApplicable;
    }

    public static MigrationPreview from(
            SourceMigrationAdapter adapter, ParameterDefinition source, MigrationPlan plan) {
        ParameterDefinition target = plan.getTarget();
        return new MigrationPreview(
                adapter.getSourcePluginId(),
                source.getName(),
                plan.getClassification(),
                plan.getReason(),
                plan.getDecisions(),
                target == null ? "Decision required" : target.getClass().getSimpleName(),
                target == null && plan.getTargetSkeleton() != null
                        ? "A governed Provider/Form refactoring skeleton was generated."
                        : summarize(target),
                plan.getTargetSkeleton() == null ? snippet(target) : plan.getTargetSkeleton(),
                plan.getVerificationFixture() == null ? VERIFICATION_CASES : List.of(plan.getVerificationFixture()),
                target != null);
    }

    public static MigrationPreview failed(SourceMigrationAdapter adapter, ParameterDefinition source) {
        return new MigrationPreview(
                adapter.getSourcePluginId(),
                source.getName(),
                MigrationClassification.D,
                "The source adapter could not produce a safe preview. Review the source definition manually.",
                List.of("Inspect the business requirement without executing source code."),
                "Manual redesign",
                "No target definition was generated.",
                "// No automatic target was generated.",
                VERIFICATION_CASES,
                false);
    }

    public String getSourcePluginId() {
        return sourcePluginId;
    }

    public String getParameterName() {
        return parameterName;
    }

    public String getClassification() {
        return classification.name();
    }

    public String getReason() {
        return reason;
    }

    public List<String> getDecisions() {
        return decisions;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetSummary() {
        return targetSummary;
    }

    public String getPipelineSnippet() {
        return pipelineSnippet;
    }

    public boolean isAutomaticallyApplicable() {
        return automaticallyApplicable;
    }

    public List<String> getVerificationCases() {
        return verificationCases;
    }

    private static String summarize(ParameterDefinition target) {
        if (!(target instanceof AbstractUnifiedParameterDefinition unified)) {
            return "No target definition was generated.";
        }
        String cardinality = unified.isMultiple() ? "multiple choice" : "single choice";
        return unified.getChoices().size()
                + " inline option(s), "
                + cardinality
                + ", renderer "
                + unified.getRenderer()
                + ", default mode "
                + unified.getDefaultMode()
                + ".";
    }

    private static String snippet(ParameterDefinition target) {
        if (target instanceof UnifiedChoiceParameterDefinition single) {
            return "unifiedChoice(\n"
                    + "  name: "
                    + quote(single.getName())
                    + ",\n"
                    + "  choices: "
                    + choices(single.getChoices())
                    + ",\n"
                    + "  renderer: "
                    + quote(single.getRenderer())
                    + ",\n"
                    + "  defaultMode: "
                    + quote(single.getDefaultMode())
                    + ("explicit".equals(single.getDefaultMode())
                            ? ",\n  defaultValue: " + quote(single.getDefaultValue())
                            : "")
                    + "\n)";
        }
        if (target instanceof UnifiedMultiChoiceParameterDefinition multiple) {
            return "unifiedMultiChoice(\n"
                    + "  name: "
                    + quote(multiple.getName())
                    + ",\n"
                    + "  choices: "
                    + choices(multiple.getChoices())
                    + ",\n"
                    + "  renderer: "
                    + quote(multiple.getRenderer())
                    + ",\n"
                    + "  defaultMode: "
                    + quote(multiple.getDefaultMode())
                    + ("explicit".equals(multiple.getDefaultMode())
                            ? ",\n  defaultValues: ["
                                    + multiple.getDefaultValues().stream()
                                            .map(MigrationPreview::quote)
                                            .collect(Collectors.joining(", "))
                                    + "]"
                            : "")
                    + ",\n  standardApiInputMode: "
                    + quote(multiple.getStandardApiInputMode())
                    + ",\n  delimiter: "
                    + quote(multiple.getDelimiter())
                    + "\n)";
        }
        return "// No automatic target was generated.";
    }

    private static String choices(List<Option> options) {
        int limit = Math.min(options.size(), 200);
        String encoded = options.subList(0, limit).stream()
                .map(option -> "[value: " + quote(option.getValue()) + "]")
                .collect(Collectors.joining(", "));
        if (limit < options.size()) {
            encoded += ", /* " + (options.size() - limit) + " more options omitted */";
        }
        return "[" + encoded + "]";
    }

    private static String quote(String value) {
        if (value == null) {
            return "null";
        }
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }
}
