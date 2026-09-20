package io.jenkins.plugins.unifiedparameters.provider;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node;
import io.jenkins.plugins.unifiedparameters.evaluation.ErrorCode;
import io.jenkins.plugins.unifiedparameters.model.Option;
import java.io.Serial;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Offers the controller's agent names or labels as governed options. This is the declarative
 * replacement for the very common Active Choices pattern that reads
 * {@code Jenkins.instance.nodes} or {@code Jenkins.instance.getSelfLabel()} and returns node names:
 * the plugin reads the Jenkins model itself, so no Job-provided script runs.
 */
public final class NodeLabelProviderDefinition extends SharedProviderDefinition {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String SOURCE_NODES = "nodes";
    public static final String SOURCE_LABELS = "labels";

    private String source = SOURCE_NODES;
    private String includePattern;
    private String excludePattern;
    private boolean includeBuiltIn = true;
    private boolean onlineOnly;

    @DataBoundConstructor
    public NodeLabelProviderDefinition(String id) {
        super(id);
    }

    /** {@code nodes} lists agent names; {@code labels} lists the assigned label names. */
    public String getSource() {
        return source == null || source.isBlank() ? SOURCE_NODES : source;
    }

    @DataBoundSetter
    public void setSource(String source) {
        if (!Set.of(SOURCE_NODES, SOURCE_LABELS).contains(source)) {
            throw new IllegalArgumentException("Node Provider source must be nodes or labels.");
        }
        this.source = source;
    }

    public String getIncludePattern() {
        return includePattern;
    }

    @DataBoundSetter
    public void setIncludePattern(String includePattern) {
        this.includePattern = validatePattern(includePattern, "includePattern");
    }

    public String getExcludePattern() {
        return excludePattern;
    }

    @DataBoundSetter
    public void setExcludePattern(String excludePattern) {
        this.excludePattern = validatePattern(excludePattern, "excludePattern");
    }

    public boolean isIncludeBuiltIn() {
        return includeBuiltIn;
    }

    @DataBoundSetter
    public void setIncludeBuiltIn(boolean includeBuiltIn) {
        this.includeBuiltIn = includeBuiltIn;
    }

    /** When set, offline agents are not offered. The default keeps every configured agent. */
    public boolean isOnlineOnly() {
        return onlineOnly;
    }

    @DataBoundSetter
    public void setOnlineOnly(boolean onlineOnly) {
        this.onlineOnly = onlineOnly;
    }

    @Override
    public List<Option> load(ProviderLoadContext context) {
        context.checkCancelled();
        TreeSet<String> names = new TreeSet<>();
        if (SOURCE_LABELS.equals(getSource())) {
            for (Label label : Jenkins.get().getAssignedLabels()) {
                names.add(label.getDisplayName());
            }
        } else {
            for (Computer computer : Jenkins.get().getComputers()) {
                if (onlineOnly && computer.isOffline()) {
                    continue;
                }
                Node node = computer.getNode();
                // The built-in node has an empty node name, which the source scripts reported through
                // getSelfLabel().getName() as "built-in".
                String name = node == null ? "" : node.getNodeName();
                names.add(name == null || name.isBlank() ? "built-in" : name);
            }
        }
        if (!includeBuiltIn) {
            names.remove("built-in");
            names.remove("master");
        }
        Pattern include = compile(includePattern);
        Pattern exclude = compile(excludePattern);
        List<Option> options = new ArrayList<>();
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            if (include != null && !include.matcher(name).matches()) {
                continue;
            }
            if (exclude != null && exclude.matcher(name).matches()) {
                continue;
            }
            options.add(new Option(name));
        }
        if (options.isEmpty()) {
            throw new ProviderException(
                    ErrorCode.PROVIDER_UNAVAILABLE, getId(), "No " + getSource() + " matched the configured patterns.");
        }
        return List.copyOf(options);
    }

    @Override
    protected String providerFingerprint() {
        Set<String> parts = new LinkedHashSet<>();
        parts.add(getSource());
        parts.add(String.valueOf(includeBuiltIn));
        parts.add(String.valueOf(onlineOnly));
        parts.add(String.valueOf(includePattern));
        parts.add(String.valueOf(excludePattern));
        return Digests.sha256(String.join("\u0000", parts));
    }

    private static Pattern compile(String pattern) {
        return pattern == null || pattern.isBlank() ? null : Pattern.compile(pattern);
    }

    private static String validatePattern(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            Pattern.compile(value);
        } catch (PatternSyntaxException exception) {
            throw new IllegalArgumentException(field + " must be a valid regular expression.");
        }
        return value;
    }

    @Extension
    @Symbol("unifiedNodeProvider")
    public static final class DescriptorImpl extends Descriptor<SharedProviderDefinition> {
        @Override
        public String getDisplayName() {
            return "Unified Parameters Node/Label Provider";
        }
    }
}
