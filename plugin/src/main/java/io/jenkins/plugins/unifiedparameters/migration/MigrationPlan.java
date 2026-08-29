package io.jenkins.plugins.unifiedparameters.migration;

import hudson.model.ParameterDefinition;
import java.util.ArrayList;
import java.util.List;

public final class MigrationPlan {

    private final MigrationClassification classification;
    private final String reason;
    private final ParameterDefinition target;
    private final List<String> decisions;
    private final String targetSkeleton;
    private final String verificationFixture;

    public MigrationPlan(
            MigrationClassification classification, String reason, ParameterDefinition target, List<String> decisions) {
        this(classification, reason, target, decisions, null, null);
    }

    public MigrationPlan(
            MigrationClassification classification,
            String reason,
            ParameterDefinition target,
            List<String> decisions,
            String targetSkeleton,
            String verificationFixture) {
        this.classification = classification;
        this.reason = reason;
        this.target = target;
        this.decisions = decisions == null ? List.of() : List.copyOf(new ArrayList<>(decisions));
        this.targetSkeleton = targetSkeleton;
        this.verificationFixture = verificationFixture;
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
