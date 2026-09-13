package io.jenkins.plugins.unifiedparameters.companion.git;

import hudson.Extension;
import hudson.model.Descriptor;
import io.jenkins.plugins.unifiedparameters.evaluation.ErrorCode;
import io.jenkins.plugins.unifiedparameters.model.Option;
import io.jenkins.plugins.unifiedparameters.provider.ConnectionProfileConsumer;
import io.jenkins.plugins.unifiedparameters.provider.ProviderException;
import io.jenkins.plugins.unifiedparameters.provider.ProviderLoadContext;
import io.jenkins.plugins.unifiedparameters.provider.SharedProviderDefinition;
import io.jenkins.plugins.unifiedparameters.provider.UnifiedParametersGlobalConfiguration;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public final class GitRefProviderDefinition extends SharedProviderDefinition implements ConnectionProfileConsumer {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final int MAX_REFS = 10_000;

    private final String connectionProfileId;
    private String refType = "branch";
    private String includePattern;
    private String excludePattern;
    private String sortOrder = "ascending";
    private int maximumRefs = 1_000;

    @DataBoundConstructor
    public GitRefProviderDefinition(String id, String connectionProfileId) {
        super(id);
        if (connectionProfileId == null || connectionProfileId.isBlank()) {
            throw new IllegalArgumentException("Connection Profile ID is required.");
        }
        this.connectionProfileId = connectionProfileId;
    }

    GitRefProviderDefinition(String id, String connectionProfileId, GitReferenceSource source) {
        this(id, connectionProfileId);
        this.source = source;
    }

    private transient GitReferenceSource source;

    @Override
    public String getConnectionProfileId() {
        return connectionProfileId;
    }

    public String getRefType() {
        return refType == null || refType.isBlank() ? "branch" : refType;
    }

    @DataBoundSetter
    public void setRefType(String refType) {
        if (!Set.of("branch", "tag", "ref").contains(refType)) {
            throw new IllegalArgumentException("Git ref type must be branch, tag, or ref.");
        }
        this.refType = refType;
    }

    public String getIncludePattern() {
        return includePattern;
    }

    @DataBoundSetter
    public void setIncludePattern(String includePattern) {
        this.includePattern = normalizePattern(includePattern, "includePattern");
    }

    public String getExcludePattern() {
        return excludePattern;
    }

    @DataBoundSetter
    public void setExcludePattern(String excludePattern) {
        this.excludePattern = normalizePattern(excludePattern, "excludePattern");
    }

    public String getSortOrder() {
        return sortOrder == null || sortOrder.isBlank() ? "ascending" : sortOrder;
    }

    @DataBoundSetter
    public void setSortOrder(String sortOrder) {
        if (!Set.of("ascending", "descending", "smart-ascending", "smart-descending")
                .contains(sortOrder)) {
            throw new IllegalArgumentException(
                    "Git ref sort order must be ascending, descending, smart-ascending, or smart-descending.");
        }
        this.sortOrder = sortOrder;
    }

    public int getMaximumRefs() {
        return maximumRefs < 1 ? 1_000 : maximumRefs;
    }

    @DataBoundSetter
    public void setMaximumRefs(int maximumRefs) {
        if (maximumRefs < 1 || maximumRefs > MAX_REFS) {
            throw new IllegalArgumentException("maximumRefs must be between 1 and 10000.");
        }
        this.maximumRefs = maximumRefs;
    }

    @Override
    public List<Option> load(ProviderLoadContext context) {
        if (!UnifiedParametersGlobalConfiguration.gitRefsProviderFeatureEnabled()) {
            throw new ProviderException(ErrorCode.PROVIDER_UNAVAILABLE, getId(), "The Git refs Provider is disabled.");
        }
        return normalize(
                sourceOrDefault().fetch(context),
                context.request().providerId(),
                getRefType(),
                includePattern,
                excludePattern,
                getSortOrder(),
                getMaximumRefs());
    }

    /**
     * Version-aware ordering: numeric runs compare by value, so {@code 1.9} sorts before
     * {@code 1.10}, which is what the source plugin's SMART sort modes do.
     */
    static int compareSmart(String left, String right) {
        int leftIndex = 0;
        int rightIndex = 0;
        while (leftIndex < left.length() && rightIndex < right.length()) {
            char leftChar = left.charAt(leftIndex);
            char rightChar = right.charAt(rightIndex);
            if (Character.isDigit(leftChar) && Character.isDigit(rightChar)) {
                int leftEnd = leftIndex;
                while (leftEnd < left.length() && Character.isDigit(left.charAt(leftEnd))) {
                    leftEnd++;
                }
                int rightEnd = rightIndex;
                while (rightEnd < right.length() && Character.isDigit(right.charAt(rightEnd))) {
                    rightEnd++;
                }
                String leftNumber = stripLeadingZeros(left.substring(leftIndex, leftEnd));
                String rightNumber = stripLeadingZeros(right.substring(rightIndex, rightEnd));
                int comparison = Integer.compare(leftNumber.length(), rightNumber.length());
                if (comparison != 0) {
                    return comparison;
                }
                comparison = leftNumber.compareTo(rightNumber);
                if (comparison != 0) {
                    return comparison;
                }
                leftIndex = leftEnd;
                rightIndex = rightEnd;
                continue;
            }
            if (leftChar != rightChar) {
                return Character.compare(leftChar, rightChar);
            }
            leftIndex++;
            rightIndex++;
        }
        return Integer.compare(left.length() - leftIndex, right.length() - rightIndex);
    }

    private static String stripLeadingZeros(String value) {
        int index = 0;
        while (index < value.length() - 1 && value.charAt(index) == '0') {
            index++;
        }
        return value.substring(index);
    }

    static List<Option> normalize(
            Map<String, String> refs,
            String providerId,
            String refType,
            String includePattern,
            String excludePattern,
            String sortOrder,
            int maximumRefs) {
        Pattern include = compilePattern(includePattern, "includePattern", providerId);
        Pattern exclude = compilePattern(excludePattern, "excludePattern", providerId);
        List<String> names = new ArrayList<>();
        for (String refName : refs.keySet()) {
            String name = shortName(refName, refType);
            if (name == null
                    || (include != null && !include.matcher(name).matches())
                    || (exclude != null && exclude.matcher(name).matches())) {
                continue;
            }
            names.add(name);
        }
        boolean smart = sortOrder != null && sortOrder.startsWith("smart-");
        Comparator<String> order = smart ? GitRefProviderDefinition::compareSmart : Comparator.naturalOrder();
        if (sortOrder != null && sortOrder.endsWith("descending")) {
            order = order.reversed();
        }
        names.sort(order);
        if (names.size() > maximumRefs) {
            throw new ProviderException(
                    ErrorCode.PROVIDER_UNAVAILABLE,
                    providerId,
                    "Git ref Provider returned more than the configured maximum.");
        }
        return names.stream()
                .distinct()
                .map(name -> {
                    Option option = new Option(name);
                    option.setLabel(name);
                    option.setDescription("Git " + refType);
                    option.setTags(List.of("git", refType));
                    return option;
                })
                .toList();
    }

    private static String shortName(String refName, String refType) {
        if (refName == null || refName.endsWith("^{}") || "HEAD".equals(refName)) {
            return null;
        }
        if ("branch".equals(refType)) {
            return refName.startsWith("refs/heads/") ? refName.substring("refs/heads/".length()) : null;
        }
        if ("tag".equals(refType)) {
            return refName.startsWith("refs/tags/") ? refName.substring("refs/tags/".length()) : null;
        }
        if ("ref".equals(refType)) {
            return refName.startsWith("refs/") ? refName : null;
        }
        return null;
    }

    private static String normalizePattern(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() > 512) {
            throw new IllegalArgumentException(field + " must be at most 512 characters.");
        }
        try {
            Pattern.compile(value);
        } catch (PatternSyntaxException exception) {
            throw new IllegalArgumentException(field + " must be a valid regular expression.", exception);
        }
        return value;
    }

    private static Pattern compilePattern(String value, String field, String providerId) {
        if (value == null) {
            return null;
        }
        try {
            return Pattern.compile(value);
        } catch (PatternSyntaxException exception) {
            throw new ProviderException(ErrorCode.PROVIDER_UNAVAILABLE, providerId, "Invalid " + field + ".");
        }
    }

    private GitReferenceSource sourceOrDefault() {
        return source == null ? new JenkinsGitReferenceSource(refType) : source;
    }

    @Override
    protected String providerFingerprint() {
        return connectionProfileId
                + '\u0000'
                + getRefType()
                + '\u0000'
                + includePattern
                + '\u0000'
                + excludePattern
                + '\u0000'
                + getSortOrder()
                + '\u0000'
                + getMaximumRefs();
    }

    @Extension
    @Symbol("unifiedGitRefProvider")
    public static final class DescriptorImpl extends Descriptor<SharedProviderDefinition> {
        @Override
        public String getDisplayName() {
            return "Unified Parameters Git Branch/Tag/Ref Provider";
        }
    }
}
