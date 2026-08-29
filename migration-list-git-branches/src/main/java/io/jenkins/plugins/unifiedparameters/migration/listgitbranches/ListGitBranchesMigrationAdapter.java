package io.jenkins.plugins.unifiedparameters.migration.listgitbranches;

import hudson.Extension;
import hudson.model.ParameterDefinition;
import io.jenkins.plugins.unifiedparameters.migration.MigrationClassification;
import io.jenkins.plugins.unifiedparameters.migration.MigrationPlan;
import io.jenkins.plugins.unifiedparameters.migration.SourceMigrationAdapter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

@Extension
public final class ListGitBranchesMigrationAdapter extends SourceMigrationAdapter {

    private static final String SOURCE_CLASS =
            "com.syhuang.hudson.plugins.listgitbranchesparameter.ListGitBranchesParameterDefinition";

    @Override
    public String getSourcePluginId() {
        return "list-git-branches-parameter";
    }

    @Override
    public boolean supports(ParameterDefinition definition) {
        return definition != null && SOURCE_CLASS.equals(definition.getClass().getName());
    }

    @Override
    public MigrationPlan inspect(ParameterDefinition definition) {
        if (!supports(definition)) {
            throw new IllegalArgumentException("Unsupported source definition");
        }
        String sourceType =
                firstNonEmpty(readString(definition, "getType"), readString(definition, "getBranchType"), "branch");
        String skeleton = "unifiedChoice(\n"
                + "    name: "
                + quote(definition.getName())
                + ",\n"
                + "    providerId: 'TODO-governed-git-refs-provider',\n"
                + "    renderer: 'searchable'\n"
                + ")";
        return new MigrationPlan(
                MigrationClassification.C,
                "Git reference discovery requires a governed REST or Git companion Provider; repository and credential values are not copied into the Job target.",
                null,
                List.of(
                        "Bind the repository authority and credential through an administrator-owned Connection Profile.",
                        "Confirm branch/tag/ref filtering, sorting, default, empty repository, and unavailable credential behavior.",
                        "Confirm source mode " + sourceType + " maps to a single canonical ref value."),
                skeleton,
                "Resolve representative empty, one-ref, many-ref, renamed/deleted-ref, credential failure, API, CLI, browser, and Rebuild cases.");
    }

    private static String readString(Object source, String methodName) {
        try {
            Method method = source.getClass().getMethod(methodName);
            Object value = method.invoke(source);
            return value == null ? "" : String.valueOf(value);
        } catch (NoSuchMethodException exception) {
            return "";
        } catch (IllegalAccessException | InvocationTargetException exception) {
            return "";
        }
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String quote(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }
}
