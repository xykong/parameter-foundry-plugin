package io.jenkins.plugins.parameterfoundry.migration.listgitbranches;

import hudson.Extension;
import hudson.model.ParameterDefinition;
import io.jenkins.plugins.parameterfoundry.migration.MigrationClassification;
import io.jenkins.plugins.parameterfoundry.migration.MigrationPlan;
import io.jenkins.plugins.parameterfoundry.migration.MigrationText;
import io.jenkins.plugins.parameterfoundry.migration.SourceMigrationAdapter;
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
                    MigrationText.of("MigrationReasonGitUnknownMode", sourceType),
                    null,
                    List.of(
                            MigrationText.of("MigrationDecisionGitDocumentMode"),
                            MigrationText.of("MigrationDecisionGitDoNotCopyCredentials")),
                    null,
                    MigrationText.of("MigrationCaseGitUnknownMode"));
        }
        String branchFilter = firstNonEmpty(readString(definition, "getBranchFilter"));
        String tagFilter = firstNonEmpty(readString(definition, "getTagFilter"));
        String sourceSort = firstNonEmpty(readString(definition, "getSortMode"));
        String sourceDefault = readNullableString(definition, "getDefaultValue");
        String selectedValue = firstNonEmpty(readString(definition, "getSelectedValue"));
        boolean quickFilter = Boolean.parseBoolean(firstNonEmpty(readString(definition, "getQuickFilterEnabled")));
        String sourceFilter = "tag".equals(mapping.refType()) ? tagFilter : branchFilter;
        String translatedFilter = translateFilter(sourceFilter, mapping.refType());
        String resolvedInclude = translatedFilter != null
                ? translatedFilter
                : (mapping.includePattern().startsWith("<") ? null : mapping.includePattern());
        String targetSort = mapSortOrder(sourceSort);
        String skeleton = gitProviderSkeleton(
                definition, mapping, resolvedInclude, targetSort, quickFilter, sourceDefault, selectedValue);

        java.util.Map<MigrationText, MigrationText> facts = new java.util.LinkedHashMap<>();
        facts.put(MigrationText.of("MigrationFactSourceMode"), MigrationText.value(mapping.sourceType()));
        facts.put(
                MigrationText.of("MigrationFactSourceSort"),
                sourceSort.isEmpty() ? MigrationText.of("MigrationValueDefault") : MigrationText.value(sourceSort));
        facts.put(
                MigrationText.of("MigrationFactTargetSort"),
                targetSort == null
                        ? MigrationText.of("MigrationValueReviewRequired")
                        : MigrationText.value(targetSort));
        facts.put(
                MigrationText.of("MigrationFactSourceFilter"),
                sourceFilter.isEmpty() ? MigrationText.of("MigrationValueNone") : MigrationText.value(sourceFilter));
        facts.put(
                MigrationText.of("MigrationFactTargetIncludeRegex"),
                resolvedInclude == null
                        ? MigrationText.of("MigrationValueNoneOrReview")
                        : MigrationText.value(resolvedInclude));
        facts.put(
                MigrationText.of("MigrationFactSourceDefault"),
                sourceDefault == null || sourceDefault.isEmpty()
                        ? MigrationText.of("MigrationValueNone")
                        : MigrationText.value(sourceDefault));
        facts.put(
                MigrationText.of("MigrationFactSourceSelectedValue"),
                selectedValue.isEmpty()
                        ? MigrationText.of("MigrationValueDefault")
                        : MigrationText.value(selectedValue));
        facts.put(
                MigrationText.of("MigrationFactSourceQuickFilter"),
                MigrationText.of(quickFilter ? "MigrationValueEnabled" : "MigrationValueDisabled"));

        List<MigrationText> decisions = new java.util.ArrayList<>(List.of(
                MigrationText.of("MigrationDecisionGitModeMapping", mapping.sourceType(), mapping.refType()),
                MigrationText.of("MigrationDecisionGitAuthority"),
                MigrationText.of("MigrationDecisionGitTransport"),
                MigrationText.of("MigrationDecisionGitDefaultSemantics"),
                MigrationText.of("MigrationDecisionGitCanonicalValue")));
        if (!sourceSort.isEmpty()) {
            decisions.add(
                    targetSort == null
                            ? MigrationText.of("MigrationDecisionGitSortUnknown", sourceSort)
                            : MigrationText.of("MigrationDecisionGitSortMapped", sourceSort, targetSort));
        }
        if (sourceFilter.isEmpty()) {
            decisions.add(MigrationText.of("MigrationDecisionGitNoFilter"));
        } else if (translatedFilter == null) {
            decisions.add(MigrationText.of("MigrationDecisionGitFilterUntranslated", sourceFilter));
        } else {
            decisions.add(MigrationText.of("MigrationDecisionGitFilterTranslated", sourceFilter, translatedFilter));
        }
        if (sourceDefault != null && !sourceDefault.isEmpty()) {
            decisions.add(MigrationText.of("MigrationDecisionGitDefaultMustExist", sourceDefault));
        }
        if ("TOP".equalsIgnoreCase(selectedValue)) {
            decisions.add(MigrationText.of("MigrationDecisionGitTop"));
        }
        if (mapping.organizationSpecific()) {
            decisions.add(MigrationText.of("MigrationDecisionGitOrganizationMode"));
            decisions.add(MigrationText.of("MigrationDecisionGitOrganizationAuthority"));
        } else if (PT_BRANCH_TAG.equals(mapping.sourceType())) {
            decisions.add(MigrationText.of("MigrationDecisionGitCombinedRefs"));
        }
        return new MigrationPlan(
                MigrationClassification.C,
                mapping.organizationSpecific()
                        ? MigrationText.of("MigrationReasonGitOrganizationSpecific")
                        : MigrationText.of("MigrationReasonGitMapped", mapping.sourceType()),
                null,
                decisions,
                skeleton,
                MigrationText.of("MigrationCaseGitRefs"),
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
        String translated = lookaheads.length() == 0 ? value : lookaheads + (value.isEmpty() ? ".*" : value);
        if (translated.isEmpty()) {
            return null;
        }
        // A filter such as .*refs/heads/(*) cannot be expressed as a full-match regex at all.
        try {
            java.util.regex.Pattern.compile(translated);
        } catch (java.util.regex.PatternSyntaxException exception) {
            return null;
        }
        return translated;
    }

    /** Maps the source sort mode onto a supported Provider sort order, or {@code null} to review. */
    static String mapSortOrder(String sourceSort) {
        String value = sourceSort == null ? "" : sourceSort.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "ASCENDING_SMART", "SMART_ASCENDING" -> "smart-ascending";
            case "DESCENDING_SMART", "SMART_DESCENDING" -> "smart-descending";
            case "ASCENDING", "ASCENDING_ALPHABETICAL" -> "ascending";
            case "DESCENDING", "DESCENDING_ALPHABETICAL" -> "descending";
            case "NONE" -> "none";
            default -> null;
        };
    }

    private static String gitProviderSkeleton(
            ParameterDefinition definition,
            GitMapping mapping,
            String translatedFilter,
            String targetSort,
            boolean quickFilter,
            String sourceDefault,
            String selectedValue) {
        String providerId = "TODO-git-refs-provider";
        return "foundryGitRefProvider(\n"
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
                + "    cacheTtlSeconds: 0,\n"
                + "    maximumRefs: 1000\n"
                + ")\n"
                + "foundryChoice(\n"
                + "    name: "
                + quote(definition.getName())
                + ",\n"
                + "    providerId: '"
                + providerId
                + "',\n"
                + "    renderer: "
                + quote(quickFilter ? "searchable" : "select")
                + ",\n    standardApiInputMode: 'list-git-branches',\n    defaultMode: "
                + quote(
                        sourceDefault != null && !sourceDefault.isBlank()
                                ? "explicit"
                                : "TOP".equalsIgnoreCase(selectedValue) ? "first" : "none")
                + ",\n    defaultValue: " + quote(sourceDefault)
                + ",\n    gitSelectionMode: "
                + quote(
                        "TOP".equalsIgnoreCase(selectedValue)
                                ? "top"
                                : "NONE".equalsIgnoreCase(selectedValue) ? "none" : "default")
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

    private static String readNullableString(Object source, String methodName) {
        try {
            Object value = source.getClass().getMethod(methodName).invoke(source);
            return value == null ? null : String.valueOf(value);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
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
        if (value == null) return "null";
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    private record GitMapping(String sourceType, String refType, String includePattern, boolean organizationSpecific) {}
}
