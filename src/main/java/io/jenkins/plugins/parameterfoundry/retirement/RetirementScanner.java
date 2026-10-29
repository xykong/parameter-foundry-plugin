package io.jenkins.plugins.parameterfoundry.retirement;

import hudson.PluginWrapper;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Queue;
import hudson.model.Run;
import io.jenkins.plugins.parameterfoundry.definition.AbstractFoundryParameterDefinition;
import io.jenkins.plugins.parameterfoundry.form.FoundryFormParameterDefinition;
import io.jenkins.plugins.parameterfoundry.migration.SourceMigrationAdapter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jenkins.model.Jenkins;

public final class RetirementScanner {

    private static final int MAX_HISTORICAL_BUILDS_PER_JOB = 100;

    public List<RetirementFinding> scan() {
        return scanAll().findings();
    }

    /** One pass over the controller: parameter findings and the repository-side blind spot. */
    public RetirementReport scanAll() {
        Map<Key, MutableFinding> findings = new LinkedHashMap<>();
        for (Job<?, ?> job : Jenkins.get().getAllItems(Job.class)) {
            scanActive(job, findings);
            scanBuilds(job, findings);
        }
        scanQueue(findings);
        List<RetirementFinding> frozen = findings.values().stream()
                .map(MutableFinding::freeze)
                .sorted(Comparator.comparing(RetirementFinding::sourcePluginId)
                        .thenComparing(RetirementFinding::sourceClass))
                .toList();
        return new RetirementReport(frozen, scanRepository());
    }

    /**
     * Finds the Jobs whose parameters cannot be seen in the Job configuration because the
     * Jenkinsfile lives in a repository, and the multibranch or organization folders that
     * generate them.
     */
    public RepositoryScan scanRepository() {
        List<String> fromScm = new ArrayList<>();
        List<String> containers = new ArrayList<>();
        int materialized = 0;
        for (Item item : Jenkins.get().getAllItems(Item.class)) {
            if (isMultibranchContainer(item)) {
                containers.add(item.getFullName());
                continue;
            }
            if (!(item instanceof Job<?, ?> job)) {
                continue;
            }
            if (definesParametersInRepository(job)) {
                fromScm.add(job.getFullName());
            } else if (job.getProperty(ParametersDefinitionProperty.class) != null) {
                materialized++;
            }
        }
        fromScm.sort(String::compareTo);
        containers.sort(String::compareTo);
        return new RepositoryScan(fromScm, containers, materialized, !fromScm.isEmpty() || !containers.isEmpty());
    }

    /** True when the Job reads its Pipeline definition, including its parameters, from SCM. */
    public static boolean definesParametersInRepository(Job<?, ?> job) {
        Object definition = invoke(job, "getDefinition");
        String className = definition == null ? "" : definition.getClass().getName();
        return className.contains("Scm") || className.contains("SCM");
    }

    private static boolean isMultibranchContainer(Item item) {
        String className = item.getClass().getName();
        return className.contains("MultiBranchProject") || className.contains("OrganizationFolder");
    }

    private static Object invoke(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            return null;
        }
    }

    private static void scanActive(Job<?, ?> job, Map<Key, MutableFinding> findings) {
        ParametersDefinitionProperty property = job.getProperty(ParametersDefinitionProperty.class);
        if (property == null) {
            return;
        }
        boolean generated = isGeneratedSource(job);
        for (ParameterDefinition definition : property.getParameterDefinitions()) {
            Source source = sourceForDefinition(definition);
            if (source == null) {
                continue;
            }
            MutableFinding finding = findings.computeIfAbsent(
                    new Key(source.pluginId(), source.className()),
                    ignored -> new MutableFinding(source.pluginId(), source.className(), source.adapterInstalled()));
            finding.activeJobs.add(job.getFullName());
            if (generated) {
                finding.generatedSources.add(job.getFullName());
            }
        }
    }

    private static void scanBuilds(Job<?, ?> job, Map<Key, MutableFinding> findings) {
        int scanned = 0;
        for (Run<?, ?> run : job.getBuilds()) {
            if (scanned++ >= MAX_HISTORICAL_BUILDS_PER_JOB) {
                break;
            }
            ParametersAction parameters = run.getAction(ParametersAction.class);
            if (parameters == null) {
                continue;
            }
            for (ParameterValue value : parameters.getAllParameters()) {
                Source source = sourceForClass(value.getClass());
                if (source == null) {
                    continue;
                }
                MutableFinding finding = findings.computeIfAbsent(
                        new Key(source.pluginId(), source.className()),
                        ignored ->
                                new MutableFinding(source.pluginId(), source.className(), source.adapterInstalled()));
                if (run.isBuilding()) {
                    finding.runningBuilds++;
                } else {
                    finding.historicalBuilds++;
                }
            }
        }
    }

    private static void scanQueue(Map<Key, MutableFinding> findings) {
        for (Queue.Item item : Jenkins.get().getQueue().getItems()) {
            ParametersAction parameters = item.getAction(ParametersAction.class);
            if (parameters == null) {
                continue;
            }
            for (ParameterValue value : parameters.getAllParameters()) {
                Source source = sourceForClass(value.getClass());
                if (source == null) {
                    continue;
                }
                MutableFinding finding = findings.computeIfAbsent(
                        new Key(source.pluginId(), source.className()),
                        ignored ->
                                new MutableFinding(source.pluginId(), source.className(), source.adapterInstalled()));
                finding.queuedBuilds++;
            }
        }
    }

    private static Source sourceForDefinition(ParameterDefinition definition) {
        if (definition instanceof AbstractFoundryParameterDefinition
                || definition instanceof FoundryFormParameterDefinition
                || definition.getClass().getName().startsWith("hudson.model.")) {
            return null;
        }
        for (SourceMigrationAdapter adapter : SourceMigrationAdapter.all()) {
            if (adapter.supports(definition)) {
                return new Source(
                        adapter.getSourcePluginId(), definition.getClass().getName(), true);
            }
        }
        return sourceForClass(definition.getClass());
    }

    private static Source sourceForClass(Class<?> type) {
        String name = type.getName();
        if (name.startsWith("hudson.model.")
                || name.startsWith("jenkins.model.")
                || name.startsWith("io.jenkins.plugins.parameterfoundry.")) {
            return null;
        }
        PluginWrapper owner = Jenkins.get().getPluginManager().whichPlugin(type);
        String pluginId = owner == null ? "unknown-external" : owner.getShortName();
        boolean adapterInstalled = SourceMigrationAdapter.all().stream()
                .anyMatch(adapter ->
                        adapter.isEnabled() && adapter.getSourcePluginId().equals(pluginId));
        return new Source(pluginId, name, adapterInstalled);
    }

    private static boolean isGeneratedSource(Job<?, ?> job) {
        String className = job.getClass().getName();
        return className.contains("workflow")
                || className.contains("Workflow")
                || className.contains("multibranch")
                || className.contains("MultiBranch");
    }

    private record Key(String pluginId, String className) {}

    private record Source(String pluginId, String className, boolean adapterInstalled) {}

    private static final class MutableFinding {
        private final String sourcePluginId;
        private final String sourceClass;
        private final boolean adapterInstalled;
        private final Set<String> activeJobs = new LinkedHashSet<>();
        private final Set<String> generatedSources = new LinkedHashSet<>();
        private int queuedBuilds;
        private int runningBuilds;
        private int historicalBuilds;

        MutableFinding(String sourcePluginId, String sourceClass, boolean adapterInstalled) {
            this.sourcePluginId = sourcePluginId;
            this.sourceClass = sourceClass;
            this.adapterInstalled = adapterInstalled;
        }

        RetirementFinding freeze() {
            boolean safe = activeJobs.isEmpty()
                    && generatedSources.isEmpty()
                    && queuedBuilds == 0
                    && runningBuilds == 0
                    && historicalBuilds == 0;
            return new RetirementFinding(
                    sourcePluginId,
                    sourceClass,
                    new ArrayList<>(activeJobs),
                    new ArrayList<>(generatedSources),
                    queuedBuilds,
                    runningBuilds,
                    historicalBuilds,
                    adapterInstalled,
                    safe);
        }
    }
}
