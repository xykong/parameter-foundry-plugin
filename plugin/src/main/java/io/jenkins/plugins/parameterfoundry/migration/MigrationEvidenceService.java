package io.jenkins.plugins.parameterfoundry.migration;

import hudson.EnvVars;
import hudson.model.Action;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Run;
import io.jenkins.plugins.parameterfoundry.Messages;
import io.jenkins.plugins.parameterfoundry.evaluation.ErrorCode;
import io.jenkins.plugins.parameterfoundry.evaluation.EvaluationException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import jenkins.model.Jenkins;

/**
 * A/B verification of a migrated parameter: it compares the last build that used the source
 * definition with a build that used the target definition and stores the outcome as the
 * migration record's evidence.
 *
 * <p>The comparison covers the canonical value, the environment export, and the downstream value
 * (the build parameter and the environment variable of the same name). The service never
 * schedules a build: the operator runs the two builds, this command proves they agree.
 */
public final class MigrationEvidenceService {

    private static final int MAX_RUNS_SCANNED = 200;
    private static final String WORKFLOW_ENV_ACTION = "org.jenkinsci.plugins.workflow.cps.EnvActionImpl";

    private MigrationEvidenceService() {}

    public static MigrationEvidence verify(Job<?, ?> job, String recordId) {
        return verify(job, recordId, null, null);
    }

    /**
     * Verifies one migration record. Without explicit run numbers the service selects the newest
     * build at or before the migration for the source side and the newest build after it for the
     * target side.
     */
    public static MigrationEvidence verify(
            Job<?, ?> job, String recordId, Integer sourceRunNumber, Integer targetRunNumber) {
        job.checkPermission(Item.READ);
        job.checkPermission(Item.CONFIGURE);
        MigrationRecord record = MigrationRecordStore.get().findRecord(recordId);
        if (!record.getTargetJob().equals(job.getFullName())) {
            throw new EvaluationException(
                    ErrorCode.CONFIGURATION_INVALID, job.getFullName(), Messages.MigrationErrorRecordConflict());
        }
        if (!"APPLIED".equals(record.getStatus())) {
            throw new EvaluationException(
                    ErrorCode.CONFIGURATION_INVALID, record.getParameterName(), Messages.MigrationEvidenceNotApplied());
        }
        String parameterName = record.getParameterName();
        Instant migratedAt = Instant.parse(record.getCreatedAt());
        Run<?, ?> sourceRun = sourceRunNumber == null
                ? latestRunWithParameter(job, parameterName, migratedAt, true)
                : job.getBuildByNumber(sourceRunNumber);
        Run<?, ?> targetRun = targetRunNumber == null
                ? latestRunWithParameter(job, parameterName, migratedAt, false)
                : job.getBuildByNumber(targetRunNumber);
        if (sourceRunNumber != null && sourceRun == null) {
            throw new EvaluationException(
                    ErrorCode.CONFIGURATION_INVALID,
                    parameterName,
                    Messages.MigrationEvidenceUnknownRun(String.valueOf(sourceRunNumber)));
        }
        if (targetRunNumber != null && targetRun == null) {
            throw new EvaluationException(
                    ErrorCode.CONFIGURATION_INVALID,
                    parameterName,
                    Messages.MigrationEvidenceUnknownRun(String.valueOf(targetRunNumber)));
        }

        String actor = Jenkins.getAuthentication2().getName();
        String recordedAt = Instant.now().toString();
        MigrationEvidence evidence;
        if (sourceRun == null || targetRun == null) {
            String summary = sourceRun == null
                    ? Messages.MigrationEvidencePendingSource(parameterName)
                    : Messages.MigrationEvidencePending(parameterName);
            evidence = MigrationEvidence.of(
                    MigrationEvidence.PENDING,
                    summary,
                    runLabel(sourceRun),
                    runLabel(targetRun),
                    canonicalValue(sourceRun, parameterName),
                    canonicalValue(targetRun, parameterName),
                    "",
                    recordedAt,
                    actor);
        } else {
            String sourceValue = canonicalValue(sourceRun, parameterName);
            String targetValue = canonicalValue(targetRun, parameterName);
            Map<String, String> sourceEnvironment = environment(sourceRun);
            Map<String, String> targetEnvironment = environment(targetRun);
            List<String> differences = compareEnvironment(parameterName, sourceEnvironment, targetEnvironment);
            List<String> siblings = siblingInputDifferences(sourceRun, targetRun, parameterName);
            boolean valueMatches = sourceValue != null && sourceValue.equals(targetValue);
            String status = valueMatches && differences.isEmpty() ? MigrationEvidence.PASS : MigrationEvidence.FAIL;
            String summary;
            if (!valueMatches) {
                summary = Messages.MigrationEvidenceValueDiffers(
                        runNumber(sourceRun),
                        String.valueOf(sourceValue),
                        runNumber(targetRun),
                        String.valueOf(targetValue));
            } else if (!differences.isEmpty()) {
                summary = Messages.MigrationEvidenceEnvironmentDiffers(String.join("; ", differences));
            } else {
                summary = Messages.MigrationEvidencePass(runNumber(sourceRun), runNumber(targetRun))
                        + (siblings.isEmpty()
                                ? " " + Messages.MigrationEvidenceSiblingsIdentical()
                                : " " + Messages.MigrationEvidenceSiblingsDiffer(String.join(", ", siblings)));
            }
            evidence = MigrationEvidence.of(
                    status,
                    summary,
                    runLabel(sourceRun),
                    runLabel(targetRun),
                    sourceValue,
                    targetValue,
                    String.join("; ", differences),
                    recordedAt,
                    actor);
        }
        record.setEvidence(evidence);
        MigrationRecordStore.get().update(record);
        return evidence;
    }

    private static Run<?, ?> latestRunWithParameter(
            Job<?, ?> job, String parameterName, Instant migratedAt, boolean beforeMigration) {
        int scanned = 0;
        for (Run<?, ?> run : job.getBuilds()) {
            if (scanned++ >= MAX_RUNS_SCANNED) {
                break;
            }
            Instant startedAt = run.getTime().toInstant();
            boolean onTheWrongSide = beforeMigration ? startedAt.isAfter(migratedAt) : startedAt.isBefore(migratedAt);
            if (onTheWrongSide) {
                continue;
            }
            if (canonicalValue(run, parameterName) != null) {
                return run;
            }
        }
        return null;
    }

    /** Canonical string of the value this build received for the parameter, or {@code null}. */
    static String canonicalValue(Run<?, ?> run, String parameterName) {
        if (run == null) {
            return null;
        }
        ParametersAction action = run.getAction(ParametersAction.class);
        ParameterValue value = action == null ? null : action.getParameter(parameterName);
        if (value == null) {
            return null;
        }
        return canonicalValue(value);
    }

    /** Canonical string of a recorded value: the scalar, or the sorted set for a multi-selection. */
    public static String canonicalValue(ParameterValue value) {
        if (value == null) {
            return null;
        }
        Object raw = invoke(value, "getValue");
        return canonical(raw == null ? value : raw);
    }

    /** Environment export of one value for one build, or {@code null} when the value exports none. */
    public static String environmentOf(ParameterValue value, Run<?, ?> run, String parameterName) {
        if (value == null) {
            return null;
        }
        try {
            EnvVars environment = new EnvVars();
            value.buildEnvironment(run, environment);
            return environment.get(parameterName);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static String canonical(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Collection<?> collection) {
            List<String> values = new ArrayList<>();
            for (Object item : collection) {
                values.add(String.valueOf(item));
            }
            values.sort(String::compareTo);
            return String.join(",", values);
        }
        return String.valueOf(raw);
    }

    private static List<String> compareEnvironment(
            String parameterName, Map<String, String> sourceEnvironment, Map<String, String> targetEnvironment) {
        TreeSet<String> keys = new TreeSet<>();
        for (String key : sourceEnvironment.keySet()) {
            if (isRelevantKey(key, parameterName)) {
                keys.add(key);
            }
        }
        for (String key : targetEnvironment.keySet()) {
            if (isRelevantKey(key, parameterName)) {
                keys.add(key);
            }
        }
        List<String> differences = new ArrayList<>();
        for (String key : keys) {
            String sourceValue = sourceEnvironment.get(key);
            String targetValue = targetEnvironment.get(key);
            if (sourceValue == null || targetValue == null || !sourceValue.equals(targetValue)) {
                differences.add(key + " '" + sourceValue + "' -> '" + targetValue + "'");
            }
        }
        return differences;
    }

    private static boolean isRelevantKey(String key, String parameterName) {
        String upperKey = key.toUpperCase(java.util.Locale.ROOT);
        String upperName = parameterName.toUpperCase(java.util.Locale.ROOT);
        return upperKey.equals(upperName) || upperKey.contains(upperName);
    }

    private static List<String> siblingInputDifferences(
            Run<?, ?> sourceRun, Run<?, ?> targetRun, String parameterName) {
        Map<String, String> source = parameterValues(sourceRun, parameterName);
        Map<String, String> target = parameterValues(targetRun, parameterName);
        TreeSet<String> names = new TreeSet<>(source.keySet());
        names.addAll(target.keySet());
        List<String> differences = new ArrayList<>();
        for (String name : names) {
            String sourceValue = source.get(name);
            String targetValue = target.get(name);
            if (sourceValue == null ? targetValue != null : !sourceValue.equals(targetValue)) {
                differences.add(name);
            }
        }
        return differences;
    }

    private static Map<String, String> parameterValues(Run<?, ?> run, String excludedParameter) {
        Map<String, String> values = new TreeMap<>();
        if (run == null) {
            return values;
        }
        ParametersAction action = run.getAction(ParametersAction.class);
        if (action == null) {
            return values;
        }
        for (ParameterValue value : action.getAllParameters()) {
            if (value.getName() == null || value.getName().equals(excludedParameter)) {
                continue;
            }
            values.put(value.getName(), canonicalValue(run, value.getName()));
        }
        return values;
    }

    /** Environment variables of the build, when the build type exposes them. */
    static Map<String, String> environment(Run<?, ?> run) {
        if (run == null) {
            return Map.of();
        }
        for (Action action : run.getActions()) {
            if (WORKFLOW_ENV_ACTION.equals(action.getClass().getName())) {
                Map<String, String> environment = toMap(invoke(action, "getEnvironment"));
                if (!environment.isEmpty()) {
                    return environment;
                }
            }
        }
        return toMap(invoke(run, "getEnvironment"));
    }

    private static Map<String, String> toMap(Object environment) {
        if (environment instanceof EnvVars vars) {
            return new TreeMap<>(vars);
        }
        if (environment instanceof Map<?, ?> map) {
            Map<String, String> converted = new TreeMap<>();
            map.forEach((key, value) -> {
                if (key != null && value != null) {
                    converted.put(String.valueOf(key), String.valueOf(value));
                }
            });
            return converted;
        }
        return Map.of();
    }

    private static Object invoke(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            return null;
        }
    }

    private static String runLabel(Run<?, ?> run) {
        return run == null ? null : run.getFullDisplayName();
    }

    private static String runNumber(Run<?, ?> run) {
        return run == null ? "?" : String.valueOf(run.getNumber());
    }
}
