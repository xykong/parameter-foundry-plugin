package io.jenkins.plugins.unifiedparameters.migration;

import hudson.model.ParameterDefinition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MigrationPlan {

    private final MigrationClassification classification;
    private final String reason;
    private final ParameterDefinition target;
    private final List<String> decisions;
    private final String targetSkeleton;
    private final String verificationFixture;
    private final Map<String, String> facts;

    public MigrationPlan(
            MigrationClassification classification, String reason, ParameterDefinition target, List<String> decisions) {
        this(classification, reason, target, decisions, null, null, Map.of());
    }

    public MigrationPlan(
            MigrationClassification classification,
            String reason,
            ParameterDefinition target,
            List<String> decisions,
            String targetSkeleton,
            String verificationFixture) {
        this(classification, reason, target, decisions, targetSkeleton, verificationFixture, Map.of());
    }

    public MigrationPlan(
            MigrationClassification classification,
            String reason,
            ParameterDefinition target,
            List<String> decisions,
            String targetSkeleton,
            String verificationFixture,
            Map<String, String> facts) {
        this.classification = classification;
        this.reason = reason;
        this.target = target;
        this.decisions = decisions == null ? List.of() : List.copyOf(new ArrayList<>(decisions));
        this.targetSkeleton = targetSkeleton;
        this.verificationFixture = verificationFixture;
        this.facts = facts == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(facts));
    }

    /** Ordered source-versus-target facts shown in the migration preview. */
    public Map<String, String> getFacts() {
        return facts;
    }

    public MigrationClassification getClassification() {
        return classification;
    }

    public String getReason() {
        return reason;
    }

    public ParameterDefinition getTarget() {
        return target;
    }

    public List<String> getDecisions() {
        return decisions;
    }

    public String getTargetSkeleton() {
        return targetSkeleton;
    }

    public String getVerificationFixture() {
        return verificationFixture;
    }
}
