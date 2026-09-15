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
            return new ReplacementSimulationEntry(
                    definition.getName(),
                    definition.getClass().getName(),
                    adapter.getSourcePluginId(),
                    MigrationClassification.D.name(),
                    "",
                    BLOCKED,
                    "The source adapter could not inspect the definition.",
                    List.of(),
                    List.of());
        }
        ParameterDefinition target = plan.getTarget();
        String targetType = target == null ? "" : target.getClass().getSimpleName();
        if (target == null) {
            String skeleton = plan.getTargetSkeleton();
            if (skeleton != null && !skeleton.isBlank()) {
                boolean providerSkeleton =
                        skeleton.toLowerCase(java.util.Locale.ROOT).contains("provider");
                return new ReplacementSimulationEntry(
                        definition.getName(),
                        definition.getClass().getName(),
                        adapter.getSourcePluginId(),
                        plan.getClassification().name(),
                        providerSkeleton ? "Provider (skeleton)" : "Unified Form (skeleton)",
                        providerSkeleton ? NEEDS_PROVIDER : NEEDS_MANUAL_CHECK,
                        plan.getReason(),
                        plan.getDecisions(),
                        observe(job, definition.getName(), maxBuilds));
            }
            return new ReplacementSimulationEntry(
                    definition.getName(),
                    definition.getClass().getName(),
                    adapter.getSourcePluginId(),
                    plan.getClassification().name(),
                    targetType,
                    MANUAL_REDESIGN,
                    plan.getReason(),
                    plan.getDecisions(),
                    observe(job, definition.getName(), maxBuilds));
        }
        if (target instanceof UnifiedChoiceParameterDefinition choice && !choice.isProviderBacked()) {
            List<ParameterObservation> observations =
                    compareChoice(job, choice, adapter.currentSourceValues(definition), maxBuilds);
            return new ReplacementSimulationEntry(
                    definition.getName(),
                    definition.getClass().getName(),
                    adapter.getSourcePluginId(),
                    plan.getClassification().name(),
                    targetType,
                    verdictOf(observations),
                    plan.getReason(),
                    plan.getDecisions(),
                    observations);
        }
        String verdict = target instanceof AbstractUnifiedParameterDefinition unified && unified.isProviderBacked()
                ? NEEDS_PROVIDER
                : NEEDS_MANUAL_CHECK;
        return new ReplacementSimulationEntry(
                definition.getName(),
                definition.getClass().getName(),
                adapter.getSourcePluginId(),
                plan.getClassification().name(),
                targetType,
                verdict,
                plan.getReason(),
                plan.getDecisions(),
                observe(job, definition.getName(), maxBuilds));
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
