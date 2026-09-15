package io.jenkins.plugins.unifiedparameters.migration;

import hudson.model.ParameterDefinition;
import io.jenkins.plugins.unifiedparameters.Messages;
import io.jenkins.plugins.unifiedparameters.definition.AbstractUnifiedParameterDefinition;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MigrationPreview {

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
    private final Map<String, String> facts;

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
            boolean automaticallyApplicable,
            Map<String, String> facts) {
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
        this.facts = facts == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(facts));
    }

    /** Ordered source-versus-target facts rendered next to the classification. */
    public Map<String, String> getFacts() {
        return facts;
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
                target == null
                        ? Messages.MigrationPreviewDecisionRequired()
                        : target.getClass().getSimpleName(),
                target == null && plan.getTargetSkeleton() != null
                        ? Messages.MigrationPreviewSkeletonSummary()
                        : summarize(target),
                plan.getTargetSkeleton() == null ? MigrationSnippets.forTarget(target) : plan.getTargetSkeleton(),
                plan.getVerificationFixture() == null ? verificationCases() : List.of(plan.getVerificationFixture()),
                target != null,
                plan.getFacts());
    }

    public static MigrationPreview failed(SourceMigrationAdapter adapter, ParameterDefinition source) {
        return new MigrationPreview(
                adapter.getSourcePluginId(),
                source.getName(),
                MigrationClassification.D,
                Messages.MigrationPreviewFailureReason(),
                List.of(Messages.MigrationPreviewFailureDecision()),
                Messages.MigrationPreviewManualRedesign(),
                Messages.MigrationPreviewNoTarget(),
                Messages.MigrationPreviewNoSnippet(),
                verificationCases(),
                false,
                Map.of());
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
            return Messages.MigrationPreviewNoTarget();
        }
        String cardinality =
                unified.isMultiple() ? Messages.MigrationValueMultipleChoice() : Messages.MigrationValueSingleChoice();
        return Messages.MigrationPreviewInlineSummary(
                String.valueOf(unified.getChoices().size()),
                cardinality,
                unified.getRenderer(),
                unified.getDefaultMode());
    }

    private static List<String> verificationCases() {
        return List.of(
                Messages.MigrationCaseDefault1(),
                Messages.MigrationCaseDefault2(),
                Messages.MigrationCaseDefault3(),
                Messages.MigrationCaseDefault4());
    }
}
