package io.jenkins.plugins.unifiedparameters.migration.listgitbranches;

import hudson.Extension;
import hudson.model.ParameterDefinition;
import io.jenkins.plugins.unifiedparameters.migration.MigrationClassification;
import io.jenkins.plugins.unifiedparameters.migration.MigrationPlan;
import io.jenkins.plugins.unifiedparameters.migration.SourceMigrationAdapter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

@Extension
public final class ListGitBranchesMigrationAdapter extends SourceMigrationAdapter {

    private static final String SOURCE_CLASS =
            "com.syhuang.hudson.plugins.listgitbranchesparameter.ListGitBranchesParameterDefinition";
    private static final String PT_BRANCH = "PT_BRANCH";
    private static final String PT_TAG = "PT_TAG";
    private static final String PT_BRANCH_TAG = "PT_BRANCH_TAG";
    private static final String ORG_BRANCH = "ORG_BRANCH";

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
                firstNonEmpty(readString(definition, "getType"), readString(definition, "getBranchType"), PT_BRANCH);
        GitMapping mapping = mapType(sourceType);
        if (mapping == null) {
            return new MigrationPlan(
                    MigrationClassification.D,
                    "List Git Branches mode "
                            + sourceType
                            + " is not a known branch, tag, or combined-ref contract; no target value semantics were inferred.",
                    null,
                    List.of(
                            "Document the source fork's mode and canonical output before selecting a Git refs Provider.",
                            "Do not copy the repository URL or credential ID into the generated target."),
                    null,
                    "Provide empty, populated, invalid, unavailable-authority, browser, Typed API, CLI, trigger, and Rebuild fixtures after the mode contract is documented.");
        }
        String skeleton = gitProviderSkeleton(definition, mapping);
        List<String> decisions = new java.util.ArrayList<>(
                List.of(
                        "Source mode " + mapping.sourceType() + " maps to Git ref type " + mapping.refType() + ".",
                        "Bind the repository authority and credential through an administrator-owned Connection Profile; repository URLs and credential IDs remain redacted.",
                        "The Git refs companion accepts HTTP(S) repositories and username/password credentials; select an equivalent reviewed transport if the source uses SSH or certificate credentials.",
                        "Confirm source default, empty, unavailable-authority, and stale-ref behavior, including whether source SelectedValue.TOP means the first sorted ref.",
                        "Confirm the target emits one canonical value and that branch/tag filters, sorting, and maximum-result policy are equivalent."));
        if (mapping.organizationSpecific()) {
            decisions.add(
                    "ORG_BRANCH is organization-specific: confirm that the build value is one branch string, not an organization, repository, or repository/ref composite.");
            decisions.add(
                    "Confirm the organization authority and repository selection flow before enabling the branch candidate.");
        } else if (PT_BRANCH_TAG.equals(mapping.sourceType())) {
            decisions.add(
                    "PT_BRANCH_TAG maps to full canonical refs; confirm consumers accept refs/heads/... and refs/tags/... rather than the source's shortened names.");
        }
        return new MigrationPlan(
                MigrationClassification.C,
                mapping.organizationSpecific()
                        ? "ORG_BRANCH is not an official List Git Branches mode; it is treated as a branch candidate only after the organization-specific output contract is confirmed."
                        : "List Git Branches "
                                + mapping.sourceType()
                                + " maps to the governed Git refs companion with an explicit canonical value contract.",
                null,
                decisions,
                skeleton,
                "Resolve representative empty, one-ref, many-ref, renamed/deleted-ref, credential failure, API, CLI, browser, trigger, and Rebuild cases; compare the canonical value and default policy.");
    }

    private static String gitProviderSkeleton(ParameterDefinition definition, GitMapping mapping) {
        String providerId = "TODO-git-refs-provider";
        return "unifiedGitRefProvider(\n"
                + "    id: '"
                + providerId
                + "',\n"
                + "    connectionProfileId: '<administrator-selected-connection-profile>',\n"
                + "    refType: "
                + quote(mapping.refType())
                + ",\n"
                + "    includePattern: "
                + quote(mapping.includePattern())
                + ",\n"
                + "    excludePattern: '<reviewed-source-exclusions-or-empty>',\n"
                + "    sortOrder: '<reviewed-source-sort-order>',\n"
                + "    maximumRefs: 1000\n"
                + ")\n"
                + "unifiedChoice(\n"
                + "    name: "
                + quote(definition.getName())
                + ",\n"
                + "    providerId: '"
                + providerId
                + "',\n"
                + "    renderer: 'searchable'\n"
                + ")";
    }

    private static GitMapping mapType(String value) {
        String sourceType = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return switch (sourceType) {
            case PT_BRANCH -> new GitMapping(PT_BRANCH, "branch", "<reviewed-branch-regex-or-empty>", false);
            case PT_TAG -> new GitMapping(PT_TAG, "tag", "<reviewed-tag-regex-or-empty>", false);
            case PT_BRANCH_TAG -> new GitMapping(PT_BRANCH_TAG, "ref", "refs/(heads|tags)/.*", false);
            case ORG_BRANCH -> new GitMapping(ORG_BRANCH, "branch", "<reviewed-branch-regex-or-empty>", true);
            case "BRANCH" -> new GitMapping(PT_BRANCH, "branch", "<reviewed-branch-regex-or-empty>", false);
            case "TAG" -> new GitMapping(PT_TAG, "tag", "<reviewed-tag-regex-or-empty>", false);
            case "BRANCH_TAG" -> new GitMapping(PT_BRANCH_TAG, "ref", "refs/(heads|tags)/.*", false);
            default -> null;
        };
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

    private record GitMapping(String sourceType, String refType, String includePattern, boolean organizationSpecific) {}
}
