package io.jenkins.plugins.parameterfoundry.migration;

import hudson.model.Action;
import hudson.model.Job;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/**
 * Parameter names a Declarative Pipeline applied to its Job at the last build.
 *
 * <p>Declarative re-applies the parameters its Jenkinsfile declares, so a Job whose Pipeline
 * declares a parameter cannot be migrated through its configuration: the next build restores the
 * declared definition. The tracker action is read reflectively because the plugin does not depend
 * on pipeline-model-definition at runtime.
 */
final class DeclaredPipelineParameters {

    private static final String TRACKER_CLASS =
            "org.jenkinsci.plugins.pipeline.modeldefinition.actions.DeclarativeJobPropertyTrackerAction";

    private DeclaredPipelineParameters() {}

    static Set<String> of(Job<?, ?> job) {
        for (Action action : job.getActions()) {
            if (!TRACKER_CLASS.equals(action.getClass().getName())) {
                continue;
            }
            Object declared = invoke(action, "getParameters");
            if (declared instanceof Collection<?> names) {
                Set<String> result = new TreeSet<>();
                for (Object name : names) {
                    if (name != null) {
                        result.add(String.valueOf(name));
                    }
                }
                return Set.copyOf(result);
            }
        }
        return Set.of();
    }

    private static Object invoke(Object target, String method) {
        try {
            return target.getClass().getMethod(method).invoke(target);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }
}
