package io.jenkins.plugins.parameterfoundry.migration;

import hudson.model.ParameterDefinition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MigrationPlan {

    private final MigrationClassification classification;
    private final MigrationText reason;
    private final ParameterDefinition target;
    private final List<MigrationText> decisions;
    private final String targetSkeleton;
    private final MigrationText verificationFixture;
    private final Map<MigrationText, MigrationText> facts;

    public MigrationPlan(
            MigrationClassification classification,
            MigrationText reason,
            ParameterDefinition target,
            List<MigrationText> decisions) {
        this(classification, reason, target, decisions, null, null, Map.of());
    }

    public MigrationPlan(
            MigrationClassification classification,
            MigrationText reason,
            ParameterDefinition target,
            List<MigrationText> decisions,
            String targetSkeleton,
            MigrationText verificationFixture) {
        this(classification, reason, target, decisions, targetSkeleton, verificationFixture, Map.of());
    }

    public MigrationPlan(
            MigrationClassification classification,
            MigrationText reason,
            ParameterDefinition target,
            List<MigrationText> decisions,
            String targetSkeleton,
            MigrationText verificationFixture,
            Map<MigrationText, MigrationText> facts) {
        this.classification = classification;
        this.reason = reason;
        this.target = target;
        this.decisions = decisions == null ? List.of() : List.copyOf(new ArrayList<>(decisions));
        this.targetSkeleton = targetSkeleton;
        this.verificationFixture = verificationFixture;
        this.facts = facts == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(facts));
    }

    /** Ordered source-versus-target facts shown in the migration preview, labelled in the reader's locale. */
    public Map<String, String> getFacts() {
        Map<String, String> rendered = new LinkedHashMap<>();
        facts.forEach((key, value) -> rendered.put(key.render(), value.render()));
        return Map.copyOf(rendered);
    }

    public MigrationClassification getClassification() {
        return classification;
    }

    public String getReason() {
        return reason == null ? null : reason.render();
    }

    public ParameterDefinition getTarget() {
        return target;
    }

    public List<String> getDecisions() {
        return decisions.stream().map(MigrationText::render).toList();
    }

    public String getTargetSkeleton() {
        return targetSkeleton;
    }

    public String getVerificationFixture() {
        return verificationFixture == null ? null : verificationFixture.render();
    }
}
