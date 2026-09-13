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
        String branchFilter = firstNonEmpty(readString(definition, "getBranchFilter"));
        String tagFilter = firstNonEmpty(readString(definition, "getTagFilter"));
        String sourceSort = firstNonEmpty(readString(definition, "getSortMode"));
        String sourceDefault = firstNonEmpty(readString(definition, "getDefaultValue"));
        String selectedValue = firstNonEmpty(readString(definition, "getSelectedValue"));
        boolean quickFilter = Boolean.parseBoolean(firstNonEmpty(readString(definition, "getQuickFilterEnabled")));
        String sourceFilter = "tag".equals(mapping.refType()) ? tagFilter : branchFilter;
        String translatedFilter = translateFilter(sourceFilter, mapping.refType());
        String resolvedInclude = translatedFilter != null
                ? translatedFilter
                : (mapping.includePattern().startsWith("<") ? null : mapping.includePattern());
        String targetSort = mapSortOrder(sourceSort);
        String skeleton = gitProviderSkeleton(definition, mapping, resolvedInclude, targetSort, quickFilter);

        java.util.Map<String, String> facts = new java.util.LinkedHashMap<>();
        facts.put("Source mode", mapping.sourceType());
        facts.put("Source sort", sourceSort.isEmpty() ? "(default)" : sourceSort);
        facts.put("Target sort", targetSort == null ? "review required" : targetSort);
        facts.put("Source filter", sourceFilter.isEmpty() ? "(none)" : sourceFilter);
        facts.put("Target include regex", resolvedInclude == null ? "(none or review required)" : resolvedInclude);
        facts.put("Source default", sourceDefault.isEmpty() ? "(none)" : sourceDefault);
        facts.put("Source selected value", selectedValue.isEmpty() ? "(default)" : selectedValue);
        facts.put("Source quick filter", quickFilter ? "enabled" : "disabled");

        List<String> decisions = new java.util.ArrayList<>(
                List.of(
                        "Source mode " + mapping.sourceType() + " maps to Git ref type " + mapping.refType() + ".",
                        "Bind the repository authority and credential through an administrator-owned Connection Profile; repository URLs and credential IDs remain redacted.",
                        "The Git refs companion accepts HTTP(S) repositories and username/password credentials; select an equivalent reviewed transport if the source uses SSH or certificate credentials.",
                        "Confirm source default, empty, unavailable-authority, and stale-ref behavior, including whether source SelectedValue.TOP means the first sorted ref.",
                        "Confirm the target emits one canonical value and that branch/tag filters, sorting, and maximum-result policy are equivalent."));
        if (!sourceSort.isEmpty()) {
            decisions.add(
                    targetSort == null
                            ? "Source sort mode "
                                    + sourceSort
                                    + " has no direct target equivalent; choose ascending, descending, smart-ascending, or smart-descending."
                            : "Source sort mode " + sourceSort + " maps to target sortOrder " + targetSort
                                    + "; verify the visible order and any first/top default against the current refs.");
        }
        if (sourceFilter.isEmpty()) {
            decisions.add("The source declares no ref filter; confirm that every ref should be offered.");
        } else if (translatedFilter == null) {
            decisions.add(
                    "Source filter "
                            + sourceFilter
                            + " could not be translated automatically; author an equivalent full-match regex over the short ref name.");
        } else {
            decisions.add("Source filter " + sourceFilter + " was translated to includePattern " + translatedFilter
                    + "; compare the matched ref count against the source before enabling the Provider.");
        }
        if (!sourceDefault.isEmpty()) {
            decisions.add("Source default "
                    + sourceDefault
                    + " must exist in the migrated ref list; otherwise choose first, none, or correct the default.");
        }
        if ("TOP".equalsIgnoreCase(selectedValue)) {
            decisions.add(
                    "Source SelectedValue.TOP becomes defaultMode first; with smart sorting the first ref is the highest or lowest version by the chosen order.");
        }
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
                "Resolve representative empty, one-ref, many-ref, renamed/deleted-ref, credential failure, API, CLI, browser, trigger, and Rebuild cases; compare the canonical value and default policy.",
                facts);
    }

    /**
     * Translates a source full-ref filter such as {@code .*refs/heads/(?!release/)(.*)} into a
     * full-match regex over the short ref name. Returns {@code null} when the pattern cannot be
     * translated confidently.
     */
    static String translateFilter(String sourceFilter, String refType) {
        if (sourceFilter == null) {
            return null;
        }
        String value = sourceFilter.trim();
        if (value.isEmpty() || "*".equals(value) || ".*".equals(value)) {
            return null;
        }
        if (value.startsWith("^")) {
            value = value.substring(1);
        }
        if (value.endsWith("$")) {
            value = value.substring(0, value.length() - 1);
        }
        while (value.startsWith(".*") || value.startsWith(".+")) {
            value = value.substring(2);
        }
        for (String prefix : List.of("refs/heads/", "refs/tags/", "refs/")) {
            if (value.startsWith(prefix)) {
                value = value.substring(prefix.length());
                break;
            }
        }
        if (value.contains("refs/")) {
            return null;
        }
        StringBuilder lookaheads = new StringBuilder();
        java.util.regex.Matcher leading =
                java.util.regex.Pattern.compile("^\\(\\?[!=][^)]*\\)").matcher(value);
        while (leading.find()) {
            lookaheads.append(leading.group());
            value = value.substring(leading.end());
            leading = java.util.regex.Pattern.compile("^\\(\\?[!=][^)]*\\)").matcher(value);
        }
        if (value.endsWith("(.*)") || value.endsWith("(.+)")) {
            value = value.substring(0, value.length() - 4);
        }
        if (value.isEmpty() || ".*".equals(value) || ".+".equals(value)) {
            value = "";
        }
        if (lookaheads.length() == 0) {
            return value.isEmpty() ? null : value;
        }
        return lookaheads + (value.isEmpty() ? ".*" : value);
    }

    /** Maps the source sort mode onto a supported Provider sort order, or {@code null} to review. */
    static String mapSortOrder(String sourceSort) {
        String value = sourceSort == null ? "" : sourceSort.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "ASCENDING_SMART", "SMART_ASCENDING" -> "smart-ascending";
            case "DESCENDING_SMART", "SMART_DESCENDING" -> "smart-descending";
            case "ASCENDING", "ASCENDING_ALPHABETICAL" -> "ascending";
            case "DESCENDING", "DESCENDING_ALPHABETICAL" -> "descending";
            case "NONE" -> "ascending";
            default -> null;
        };
    }

    private static String gitProviderSkeleton(
            ParameterDefinition definition,
            GitMapping mapping,
            String translatedFilter,
            String targetSort,
            boolean quickFilter) {
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
                + quote(translatedFilter == null ? "<reviewed-source-include-regex-or-empty>" : translatedFilter)
                + ",\n"
                + "    excludePattern: '<reviewed-source-exclusions-or-empty>',\n"
                + "    sortOrder: "
                + quote(targetSort == null ? "<reviewed-source-sort-order>" : targetSort)
                + ",\n"
                + "    maximumRefs: 1000\n"
                + ")\n"
                + "unifiedChoice(\n"
                + "    name: "
                + quote(definition.getName())
                + ",\n"
                + "    providerId: '"
                + providerId
                + "',\n"
                + "    renderer: "
                + quote(quickFilter ? "searchable" : "select")
                + "\n"
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
