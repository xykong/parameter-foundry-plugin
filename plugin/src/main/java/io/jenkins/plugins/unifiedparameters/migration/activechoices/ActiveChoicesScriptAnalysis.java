package io.jenkins.plugins.unifiedparameters.migration.activechoices;

import io.jenkins.plugins.unifiedparameters.model.Option;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inspects an Active Choices script without executing it. Literal option lists are converted
 * deterministically; anything else is only described so a human can choose a governed Provider.
 */
final class ActiveChoicesScriptAnalysis {

    private static final Pattern QUOTED = Pattern.compile("^(['\"])(.*)\\1$", Pattern.DOTALL);
    private static final Pattern MAP_ENTRY = Pattern.compile("^([A-Za-z][A-Za-z0-9_]*)\\s*:\\s*(.+)$", Pattern.DOTALL);
    private static final Pattern URL = Pattern.compile("https?://[^\\s'\"()]+");
    private static final Set<String> OPTION_KEYS = Set.of("value", "label", "description", "group");
    private static final List<String> NETWORK_MARKERS =
            List.of("://", ".getText(", "readjson", "jsonslurper", "httpurlconnection", "curl", "wget", "new url(");
    private static final List<String> NODE_MARKERS = List.of(
            "jenkins.instance.nodes", "getselflabel(", "getcomputers(", "computer.name", "listnodes(", "nodenames");

    private static final List<String> CONTROLLER_MARKERS =
            List.of("jenkins.instance", ".getitem(", "sh(", "sh ", "bat(", "run(");

    private ActiveChoicesScriptAnalysis() {}

    record LiteralChoices(List<Option> options, List<String> selectedValues) {}

    /** A script that branches on one parameter and returns a literal list per branch. */
    record ConditionalChoices(
            String dependency, String caseValue, List<Option> caseOptions, List<Option> otherOptions) {}

    enum ScriptShape {
        LITERAL_LIST,
        NETWORK,
        CONTROLLER_OR_SHELL,
        OTHER
    }

    /** True when the script reads the controller's nodes or labels, which the Node Provider covers. */
    static boolean listsNodesOrLabels(String script) {
        if (script == null || script.isBlank()) {
            return false;
        }
        String normalized = script.toLowerCase(Locale.ROOT);
        return NODE_MARKERS.stream().anyMatch(normalized::contains);
    }

    static ScriptShape shape(String script) {
        String normalized = script == null ? "" : script.toLowerCase(Locale.ROOT);
        if (literalChoices(script).isPresent()) {
            return ScriptShape.LITERAL_LIST;
        }
        for (String marker : CONTROLLER_MARKERS) {
            if (normalized.contains(marker)) {
                return ScriptShape.CONTROLLER_OR_SHELL;
            }
        }
        for (String marker : NETWORK_MARKERS) {
            if (normalized.contains(marker)) {
                return ScriptShape.NETWORK;
            }
        }
        return ScriptShape.OTHER;
    }

    /** Parses a literal-only script such as {@code return ['a', 'b']} or a list of option maps. */
    static Optional<LiteralChoices> literalChoices(String script) {
        if (script == null || script.isBlank()) {
            return Optional.empty();
        }
        String value = script.trim();
        if (value.startsWith("return")) {
            value = value.substring("return".length()).trim();
        }
        if (!value.startsWith("[") || !value.endsWith("]")) {
            return Optional.empty();
        }
        String body = value.substring(1, value.length() - 1);
        List<String> elements = splitTopLevel(body);
        if (elements.isEmpty()) {
            return Optional.empty();
        }
        // Groovy treats [value: 'a', label: 'A'] as one map, not as a one-element list.
        boolean singleMap = elements.stream()
                .allMatch(element -> MAP_ENTRY.matcher(element.trim()).matches());
        List<String> candidates = singleMap ? List.of("[" + body + "]") : elements;
        List<Option> options = new ArrayList<>();
        List<String> selected = new ArrayList<>();
        for (String element : candidates) {
            Optional<Option> parsed = parseElement(element.trim(), selected);
            if (parsed.isEmpty()) {
                return Optional.empty();
            }
            options.add(parsed.get());
        }
        return Optional.of(new LiteralChoices(List.copyOf(options), List.copyOf(selected)));
    }

    private static final Pattern CONDITIONAL = Pattern.compile(
            "if\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_.-]*)\\s*==\\s*['\"]([^'\"]*)['\"]\\s*\\)\\s*\\{\\s*return\\s*(\\[[^\\]]*])\\s*}\\s*else\\s*\\{\\s*return\\s*(\\[[^\\]]*])\\s*}",
            Pattern.DOTALL);

    /**
     * Recognises {@code if (DEP == "value") { return [...] } else { return [...] }} — the shape the
     * production cascades use — so the migration can propose a declarative dependency mapping
     * instead of a script.
     */
    static Optional<ConditionalChoices> conditionalChoices(String script) {
        if (script == null || script.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = CONDITIONAL.matcher(script);
        if (!matcher.find()) {
            return Optional.empty();
        }
        Optional<LiteralChoices> caseChoices = literalChoices("return " + matcher.group(3));
        Optional<LiteralChoices> otherChoices = literalChoices("return " + matcher.group(4));
        if (caseChoices.isEmpty() || otherChoices.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ConditionalChoices(
                matcher.group(1),
                matcher.group(2),
                caseChoices.get().options(),
                otherChoices.get().options()));
    }

    /** Returns a redacted endpoint shape such as {@code https://<host>/api/platforms}. */
    static Optional<String> redactedEndpoint(String script) {
        if (script == null) {
            return Optional.empty();
        }
        Matcher matcher = URL.matcher(script);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String endpoint = matcher.group();
        String scheme = endpoint.substring(0, endpoint.indexOf("://") + 3);
        String remainder = endpoint.substring(scheme.length());
        int slash = remainder.indexOf('/');
        String path = slash < 0 ? "" : remainder.substring(slash);
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        int fragment = path.indexOf('#');
        if (fragment >= 0) {
            path = path.substring(0, fragment);
        }
        return Optional.of(scheme + "<host>" + path);
    }

    /** Response field names the script reads, so the target path mapping can be reviewed. */
    static List<String> detectedFields(String script) {
        if (script == null) {
            return List.of();
        }
        Set<String> fields = new TreeSet<>();
        Matcher named = Pattern.compile("\\bit\\.([A-Za-z][A-Za-z0-9_]*)").matcher(script);
        while (named.find()) {
            fields.add(named.group(1));
        }
        Matcher indexed =
                Pattern.compile("\\[\\s*['\"]([A-Za-z][A-Za-z0-9_]*)['\"]\\s*]").matcher(script);
        while (indexed.find()) {
            fields.add(indexed.group(1));
        }
        return List.copyOf(new LinkedHashSet<>(fields));
    }

    private static Optional<Option> parseElement(String element, List<String> selected) {
        if (element.startsWith("[") && element.endsWith("]")) {
            String body = element.substring(1, element.length() - 1);
            String value = null;
            String label = null;
            String description = null;
            String group = null;
            for (String entry : splitTopLevel(body)) {
                Matcher matcher = MAP_ENTRY.matcher(entry.trim());
                if (!matcher.matches()) {
                    return Optional.empty();
                }
                String key = matcher.group(1);
                String raw = matcher.group(2).trim();
                if ("selected".equals(key) || "disabled".equals(key)) {
                    if (!"true".equals(raw) && !"false".equals(raw)) {
                        return Optional.empty();
                    }
                    if ("selected".equals(key) && "true".equals(raw) && value != null) {
                        selected.add(value);
                    }
                    continue;
                }
                if (!OPTION_KEYS.contains(key)) {
                    return Optional.empty();
                }
                Optional<String> text = literalText(raw);
                if (text.isEmpty()) {
                    return Optional.empty();
                }
                switch (key) {
                    case "value" -> value = text.get();
                    case "label" -> label = text.get();
                    case "description" -> description = text.get();
                    case "group" -> group = text.get();
                    default -> {
                        return Optional.empty();
                    }
                }
            }
            if (value == null || value.isEmpty()) {
                return Optional.empty();
            }
            Option option = new Option(value);
            if (label != null) {
                option.setLabel(label);
            }
            if (description != null) {
                option.setDescription(description);
            }
            if (group != null) {
                option.setGroup(group);
            }
            return Optional.of(option);
        }
        Optional<String> text = literalText(element);
        if (text.isEmpty() || text.get().isEmpty()) {
            return Optional.empty();
        }
        String raw = text.get();
        String value = raw;
        if (raw.endsWith(":selected")) {
            value = raw.substring(0, raw.length() - ":selected".length());
            selected.add(value);
        } else if (raw.endsWith(":disabled")) {
            value = raw.substring(0, raw.length() - ":disabled".length());
        }
        Option option = new Option(value);
        option.setDisabled(raw.endsWith(":disabled"));
        return Optional.of(option);
    }

    private static Optional<String> literalText(String raw) {
        Matcher matcher = QUOTED.matcher(raw);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(matcher.group(2));
    }

    /** Splits on top-level commas so nested maps and lists stay intact. */
    private static List<String> splitTopLevel(String value) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        char quote = 0;
        StringBuilder current = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (quote != 0) {
                current.append(character);
                if (character == quote) {
                    quote = 0;
                }
                continue;
            }
            if (character == '\'' || character == '"') {
                quote = character;
                current.append(character);
                continue;
            }
            if (character == '[' || character == '(') {
                depth++;
            } else if (character == ']' || character == ')') {
                depth--;
            } else if (character == ',' && depth == 0) {
                parts.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(character);
        }
        if (!current.toString().isBlank()) {
            parts.add(current.toString());
        }
        return parts;
    }
}
