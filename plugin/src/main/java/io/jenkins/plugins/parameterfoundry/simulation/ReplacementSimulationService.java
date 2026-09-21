package io.jenkins.plugins.unifiedparameters.simulation;

import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Run;
import io.jenkins.plugins.unifiedparameters.definition.AbstractUnifiedParameterDefinition;
import io.jenkins.plugins.unifiedparameters.definition.UnifiedChoiceParameterDefinition;
import io.jenkins.plugins.unifiedparameters.definition.UnifiedChoiceParameterValue;
import io.jenkins.plugins.unifiedparameters.evaluation.CustomValuePolicy;
import io.jenkins.plugins.unifiedparameters.evaluation.EvaluationException;
import io.jenkins.plugins.unifiedparameters.migration.MigrationClassification;
import io.jenkins.plugins.unifiedparameters.migration.MigrationEvidenceService;
import io.jenkins.plugins.unifiedparameters.migration.MigrationPlan;
import io.jenkins.plugins.unifiedparameters.migration.MigrationSnippets;
import io.jenkins.plugins.unifiedparameters.migration.SourceMigrationAdapter;
import io.jenkins.plugins.unifiedparameters.model.Option;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Read-only replacement simulation for one Job: for every source parameter the service builds the
 * migrated target, takes the values that production builds actually recorded, and compares what the
 * source control produced with what the target control would produce for the same input.
 *
 * <p>The simulation never writes to a Job, so it can be run for every Job on the controller before
 * the migration wave. It answers three questions per parameter: is the recorded value still
 * representable, does the environment export stay identical, and which decisions or redesign work
 * remain.
 */
public final class ReplacementSimulationService {

    /** Equivalence verified against every recorded build of the Job. */
    public static final String EQUIVALENT = "EQUIVALENT";

    /** Equivalence verified, but the target adds an environment variable the source did not export. */
    public static final String EQUIVALENT_ADDED_ENV = "EQUIVALENT_ADDED_ENV";

    /** A recorded value is no longer representable, or the environment export differs. */
    public static final String MISMATCH = "MISMATCH";

    /** The target resolves through a Provider, so the value contract needs the Provider in place. */
    public static final String NEEDS_PROVIDER = "NEEDS_PROVIDER";

    /** The target shape is governed, but its field semantics need a reviewed mapping. */
    public static final String NEEDS_MANUAL_CHECK = "NEEDS_MANUAL_CHECK";

    /** The source cannot be converted automatically and needs a redesign before migration. */
    public static final String MANUAL_REDESIGN = "MANUAL_REDESIGN";

    /** The environment export differs only by the order of the same values. */
    public static final String NEEDS_ORDER_DECISION = "NEEDS_ORDER_DECISION";

    /** The current behavior is equivalent; older builds used values the source no longer offers. */
    public static final String EQUIVALENT_WITH_HISTORY = "EQUIVALENT_WITH_HISTORY";

    /** The migrated option list differs from the source's current option list. */
    public static final String OPTION_SET_MISMATCH = "OPTION_SET_MISMATCH";

    /** No build history, but the source default is still selectable in the target. */
    public static final String EQUIVALENT_DEFAULT_ONLY = "EQUIVALENT_DEFAULT_ONLY";

    /** The recorded value path is equivalent, but the source default is no longer selectable. */
    public static final String NEEDS_DEFAULT_DECISION = "NEEDS_DEFAULT_DECISION";

    /**
     * The stale source default never appeared in any recorded build, so the migration keeps the
     * option list unchanged and drops the unused default. The decision is evidence-based.
     */
    public static final String DECIDED_NO_DEFAULT = "DECIDED_NO_DEFAULT";

    /** Tri-state result of a definition-level comparison. */
    public static final String COMPARISON_MATCH = "MATCH";

    public static final String COMPARISON_MISMATCH = "MISMATCH";

    public static final String COMPARISON_UNKNOWN = "UNKNOWN";

    /** The Job never recorded a value for the parameter, so behavior could not be compared. */
    public static final String UNVERIFIED = "UNVERIFIED";

    /** The adapter failed, so no simulation exists. */
    public static final String BLOCKED = "BLOCKED";

    public static final int DEFAULT_BUILDS = 5;

    private ReplacementSimulationService() {}

    public static ReplacementSimulation simulate(Job<?, ?> job, int maxBuilds) {
        ParametersDefinitionProperty property = job.getProperty(ParametersDefinitionProperty.class);
        List<ReplacementSimulationEntry> entries = new ArrayList<>();
        int observations = 0;
        if (property != null) {
            for (ParameterDefinition definition : property.getParameterDefinitions()) {
                SourceMigrationAdapter adapter = findAdapter(definition);
                if (adapter == null) {
                    continue;
                }
                ReplacementSimulationEntry entry = simulate(job, definition, adapter, maxBuilds);
                entries.add(entry);
                observations += entry.observationCount();
            }
        }
        return new ReplacementSimulation(job.getFullName(), job.getClass().getName(), entries, observations);
    }

    static ReplacementSimulationEntry simulate(
            Job<?, ?> job, ParameterDefinition definition, SourceMigrationAdapter adapter, int maxBuilds) {
        MigrationPlan plan;
        try {
            plan = adapter.inspect(definition);
        } catch (RuntimeException exception) {
            return entry(
                    definition,
                    adapter,
                    MigrationClassification.D.name(),
                    "",
                    BLOCKED,
                    "The source adapter could not inspect the definition.",
                    List.of(),
                    List.of(),
                    "",
                    List.of(),
                    null,
                    null,
                    "");
        }
        List<ParameterObservation> recorded = observe(job, definition.getName(), maxBuilds);
        List<String> sourceValues = adapter.currentSourceValues(definition);
        String sourceDefault = adapter.currentSourceDefault(definition);
        ParameterDefinition target = plan.getTarget();
        String targetType = target == null ? "" : target.getClass().getSimpleName();
        if (target == null) {
            String skeleton = plan.getTargetSkeleton();
            if (skeleton != null && !skeleton.isBlank()) {
                boolean providerSkeleton =
                        skeleton.toLowerCase(java.util.Locale.ROOT).contains("provider");
                return entry(
                        definition,
                        adapter,
                        plan.getClassification().name(),
                        providerSkeleton ? "Provider (skeleton)" : "Unified Form (skeleton)",
                        providerSkeleton ? NEEDS_PROVIDER : NEEDS_MANUAL_CHECK,
                        plan.getReason(),
                        plan.getDecisions(),
                        recorded,
                        skeleton,
                        requiredValues(null, recorded, sourceValues),
                        COMPARISON_UNKNOWN,
                        COMPARISON_UNKNOWN,
                        sourceDefault);
            }
            return entry(
                    definition,
                    adapter,
                    plan.getClassification().name(),
                    targetType,
                    MANUAL_REDESIGN,
                    plan.getReason(),
                    plan.getDecisions(),
                    recorded,
                    "",
                    requiredValues(null, recorded, sourceValues),
                    COMPARISON_UNKNOWN,
                    COMPARISON_UNKNOWN,
                    sourceDefault);
        }
        if (target instanceof UnifiedChoiceParameterDefinition choice && !choice.isProviderBacked()) {
            List<ParameterObservation> observations = compareChoice(job, choice, sourceValues, maxBuilds);
            String optionSetComparison = optionSetComparison(choice, sourceValues);
            String defaultCheck = defaultCheck(choice, sourceDefault);
            String verdict = verdictFor(observations, optionSetComparison, defaultCheck, sourceDefault);
            return entry(
                    definition,
                    adapter,
                    plan.getClassification().name(),
                    targetType,
                    verdict,
                    plan.getReason(),
                    plan.getDecisions(),
                    observations,
                    MigrationSnippets.forTarget(target),
                    requiredValues(choice, observations, sourceValues),
                    optionSetComparison,
                    defaultCheck,
                    sourceDefault);
        }
        String verdict = target instanceof AbstractUnifiedParameterDefinition unified && unified.isProviderBacked()
                ? NEEDS_PROVIDER
                : NEEDS_MANUAL_CHECK;
        return entry(
                definition,
                adapter,
                plan.getClassification().name(),
                targetType,
                verdict,
                plan.getReason(),
                plan.getDecisions(),
                recorded,
                MigrationSnippets.forTarget(target),
                requiredValues(null, recorded, sourceValues),
                COMPARISON_UNKNOWN,
                COMPARISON_UNKNOWN,
                sourceDefault);
    }

    private static ReplacementSimulationEntry entry(
            ParameterDefinition definition,
            SourceMigrationAdapter adapter,
            String classification,
            String targetType,
            String verdict,
            String reason,
            List<String> decisions,
            List<ParameterObservation> observations,
            String targetSnippet,
            List<String> requiredValues,
            String optionSetComparison,
            String defaultCheck,
            String sourceDefault) {
        return new ReplacementSimulationEntry(
                definition.getName(),
                definition.getClass().getName(),
                adapter.getSourcePluginId(),
                classification,
                targetType,
                verdict,
                reason,
                decisions,
                observations,
                targetSnippet,
                requiredValues,
                optionSetComparison,
                defaultCheck,
                sourceDefault);
    }

    /** Values that must stay selectable: the target's options, or the recorded source values. */
    private static List<String> requiredValues(
            UnifiedChoiceParameterDefinition choice,
            List<ParameterObservation> observations,
            List<String> sourceValues) {
        Set<String> values = new LinkedHashSet<>();
        if (choice != null) {
            for (Option option : choice.getChoices()) {
                values.add(option.getValue());
            }
        }
        if (values.isEmpty()) {
            values.addAll(sourceValues);
        }
        if (values.isEmpty()) {
            for (ParameterObservation observation : observations) {
                if (observation.sourceValue() != null
                        && !observation.sourceValue().isBlank()) {
                    values.add(observation.sourceValue());
                }
            }
        }
        return List.copyOf(values);
    }

    /** Compares the source's current option list with the migrated one, when both are readable. */
    private static String optionSetComparison(UnifiedChoiceParameterDefinition choice, List<String> sourceValues) {
        if (sourceValues.isEmpty()) {
            return COMPARISON_UNKNOWN;
        }
        List<String> targetValues =
                choice.getChoices().stream().map(Option::getValue).toList();
        List<String> sortedSource = new ArrayList<>(sourceValues);
        List<String> sortedTarget = new ArrayList<>(targetValues);
        sortedSource.sort(String::compareTo);
        sortedTarget.sort(String::compareTo);
        return sortedSource.equals(sortedTarget) ? COMPARISON_MATCH : COMPARISON_MISMATCH;
    }

    /** Checks that the source default stays selectable, when the source declares one. */
    private static String defaultCheck(UnifiedChoiceParameterDefinition choice, String sourceDefault) {
        if (sourceDefault == null || sourceDefault.isBlank()) {
            return COMPARISON_UNKNOWN;
        }
        return represents(choice, sourceDefault) ? COMPARISON_MATCH : COMPARISON_MISMATCH;
    }

    /** True when a recorded build actually carried the stale default, so the value must survive. */
    private static boolean staleDefaultUsedByBuilds(List<ParameterObservation> observations, String sourceDefault) {
        if (sourceDefault == null || sourceDefault.isBlank()) {
            return false;
        }
        for (ParameterObservation observation : observations) {
            String value = observation.sourceValue();
            if (value == null) {
                continue;
            }
            for (String part : value.split(",", -1)) {
                if (sourceDefault.equals(part.trim())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String verdictFor(
            List<ParameterObservation> observations,
            String optionSetComparison,
            String defaultCheck,
            String sourceDefault) {
        if (COMPARISON_MISMATCH.equals(optionSetComparison)) {
            return OPTION_SET_MISMATCH;
        }
        if (COMPARISON_MISMATCH.equals(defaultCheck)) {
            return staleDefaultUsedByBuilds(observations, sourceDefault) ? NEEDS_DEFAULT_DECISION : DECIDED_NO_DEFAULT;
        }
        if (observations.isEmpty()) {
            return COMPARISON_MATCH.equals(defaultCheck) ? EQUIVALENT_DEFAULT_ONLY : UNVERIFIED;
        }
        return verdictOf(observations);
    }

    /** Values recorded by production builds, without a target to compare against. */
    private static List<ParameterObservation> observe(Job<?, ?> job, String parameterName, int maxBuilds) {
        List<ParameterObservation> observations = new ArrayList<>();
        for (Run<?, ?> run : job.getBuilds()) {
            if (observations.size() >= maxBuilds) {
                break;
            }
            ParameterValue value = recordedValue(run, parameterName);
            if (value == null) {
                continue;
            }
            observations.add(new ParameterObservation(
                    run.getNumber(),
                    MigrationEvidenceService.canonicalValue(value),
                    MigrationEvidenceService.environmentOf(value, run, parameterName),
                    null,
                    null,
                    true,
                    false,
                    false,
                    false,
                    null));
        }
        return List.copyOf(observations);
    }

    private static List<ParameterObservation> compareChoice(
            Job<?, ?> job, UnifiedChoiceParameterDefinition choice, List<String> sourceValues, int maxBuilds) {
        List<ParameterObservation> observations = new ArrayList<>();
        for (Run<?, ?> run : job.getBuilds()) {
            if (observations.size() >= maxBuilds) {
                break;
            }
            ParameterValue value = recordedValue(run, choice.getName());
            if (value == null) {
                continue;
            }
            String sourceValue = MigrationEvidenceService.canonicalValue(value);
            String sourceEnvironment = MigrationEvidenceService.environmentOf(value, run, choice.getName());
            List<String> parts = parts(choice, sourceValue);
            boolean representable = parts.stream().allMatch(part -> represents(choice, part));
            List<String> droppedBySource = sourceValues.isEmpty()
                    ? List.of()
                    : parts.stream()
                            .filter(part -> !sourceValues.contains(part))
                            .toList();
            boolean historical = !representable && !droppedBySource.isEmpty();
            UnifiedChoiceParameterValue targetValue = targetValue(choice, parts);
            String targetEnvironment =
                    representable ? MigrationEvidenceService.environmentOf(targetValue, run, choice.getName()) : null;
            boolean environmentMatches = representable && Objects.equals(sourceEnvironment, targetEnvironment);
            boolean orderOnly = !environmentMatches
                    && representable
                    && sourceEnvironment != null
                    && targetEnvironment != null
                    && sameValues(choice, sourceEnvironment, targetEnvironment);
            observations.add(new ParameterObservation(
                    run.getNumber(),
                    sourceValue,
                    sourceEnvironment,
                    MigrationEvidenceService.canonicalValue(targetValue),
                    targetEnvironment,
                    representable,
                    environmentMatches,
                    orderOnly,
                    historical,
                    note(
                            choice,
                            sourceValue,
                            sourceEnvironment,
                            targetEnvironment,
                            representable,
                            environmentMatches,
                            droppedBySource)));
        }
        return List.copyOf(observations);
    }

    static String verdictOf(List<ParameterObservation> observations) {
        if (observations.isEmpty()) {
            return UNVERIFIED;
        }
        boolean mismatch = false;
        boolean added = false;
        boolean orderOnly = false;
        boolean historical = false;
        for (ParameterObservation observation : observations) {
            if (!observation.representable() || !observation.environmentMatches()) {
                if (observation.representable() && observation.sourceEnvironment() == null) {
                    added = true;
                } else if (observation.orderOnlyDifference()) {
                    orderOnly = true;
                } else if (observation.historicalValue()) {
                    historical = true;
                } else {
                    mismatch = true;
                }
            }
        }
        if (mismatch) {
            return MISMATCH;
        }
        if (orderOnly) {
            return NEEDS_ORDER_DECISION;
        }
        if (historical) {
            return EQUIVALENT_WITH_HISTORY;
        }
        return added ? EQUIVALENT_ADDED_ENV : EQUIVALENT;
    }

    private static String note(
            UnifiedChoiceParameterDefinition choice,
            String sourceValue,
            String sourceEnvironment,
            String targetEnvironment,
            boolean representable,
            boolean environmentMatches,
            List<String> droppedBySource) {
        if (!representable) {
            if (!droppedBySource.isEmpty()) {
                return "The source control no longer offers "
                        + droppedBySource.size()
                        + " of the recorded value(s) ("
                        + String.join(", ", droppedBySource.subList(0, Math.min(3, droppedBySource.size())))
                        + "), so the difference comes from the source's own option list, not from the migration.";
            }
            return "Recorded value '" + sourceValue + "' is not among the migrated options.";
        }
        if (sourceEnvironment == null && targetEnvironment != null) {
            return "The source exported no environment variable; the target exports " + choice.getName() + "='"
                    + targetEnvironment + "'.";
        }
        if (!environmentMatches) {
            return choice.isMultiple()
                    ? "The environment export differs because the target canonicalizes the selection order (source '"
                            + sourceEnvironment + "', target '" + targetEnvironment + "')."
                    : "The environment export differs (source '" + sourceEnvironment + "', target '" + targetEnvironment
                            + "').";
        }
        return null;
    }

    private static boolean represents(UnifiedChoiceParameterDefinition choice, String value) {
        if (value == null) {
            return false;
        }
        for (Option option : choice.getChoices()) {
            if (value.equals(option.getValue())) {
                return true;
            }
        }
        if (!choice.isAllowCustomValue()) {
            return false;
        }
        try {
            CustomValuePolicy.validate(choice, value);
            return true;
        } catch (EvaluationException exception) {
            return false;
        }
    }

    private static List<String> parts(UnifiedChoiceParameterDefinition choice, String sourceValue) {
        if (sourceValue == null || sourceValue.isEmpty()) {
            return List.of();
        }
        if (!choice.isMultiple()) {
            return List.of(sourceValue);
        }
        Set<String> parts = new LinkedHashSet<>();
        for (String part : sourceValue.split(",", -1)) {
            if (!part.isBlank()) {
                parts.add(part.trim());
            }
        }
        return List.copyOf(parts);
    }

    /** True when both exports carry the same values in a different order. */
    private static boolean sameValues(
            UnifiedChoiceParameterDefinition choice, String sourceEnvironment, String targetEnvironment) {
        if (!choice.isMultiple()) {
            return false;
        }
        List<String> source = new ArrayList<>(parts(choice, sourceEnvironment));
        List<String> target = new ArrayList<>(parts(choice, targetEnvironment));
        source.sort(String::compareTo);
        target.sort(String::compareTo);
        return source.equals(target);
    }

    private static UnifiedChoiceParameterValue targetValue(
            UnifiedChoiceParameterDefinition choice, List<String> parts) {
        if (choice.isMultiple()) {
            return UnifiedChoiceParameterValue.multiple(
                    choice.getName(),
                    parts,
                    parts,
                    null,
                    choice.getEnvironmentExport(),
                    choice.getDelimiter(),
                    choice.getEnvironmentExportOrder(),
                    null);
        }
        String value = parts.isEmpty() ? null : parts.get(0);
        return UnifiedChoiceParameterValue.single(choice.getName(), value, null, choice.getEnvironmentExport(), null);
    }

    private static ParameterValue recordedValue(Run<?, ?> run, String parameterName) {
        ParametersAction action = run.getAction(ParametersAction.class);
        return action == null ? null : action.getParameter(parameterName);
    }

    private static SourceMigrationAdapter findAdapter(ParameterDefinition definition) {
        return SourceMigrationAdapter.all().stream()
                .filter(SourceMigrationAdapter::isEnabled)
                .filter(adapter -> adapter.supports(definition))
                .findFirst()
                .orElse(null);
    }
}
