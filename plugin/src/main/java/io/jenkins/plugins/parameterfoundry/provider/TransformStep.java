package io.jenkins.plugins.parameterfoundry.provider;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import io.jenkins.plugins.parameterfoundry.model.Option;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public final class TransformStep extends AbstractDescribableImpl<TransformStep> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String operation;
    private String field = "value";
    private String pattern = ".*";
    private String replacement = "";
    private int limit = 10_000;
    private boolean descending;

    @DataBoundConstructor
    public TransformStep(String operation) {
        if (!java.util.Set.of("map", "filter", "sort", "distinct", "limit", "group", "mark-disabled")
                .contains(operation)) {
            throw new IllegalArgumentException("Unsupported transform operation: " + operation);
        }
        this.operation = operation;
    }

    public String getOperation() {
        return operation;
    }

    public String getField() {
        return field;
    }

    @DataBoundSetter
    public void setField(String field) {
        if (!java.util.Set.of("value", "label", "description", "group").contains(field)) {
            throw new IllegalArgumentException("Unsupported Option field: " + field);
        }
        this.field = field;
    }

    public String getPattern() {
        return pattern;
    }

    @DataBoundSetter
    public void setPattern(String pattern) {
        compileSafe(pattern);
        this.pattern = pattern;
    }

    public String getReplacement() {
        return replacement;
    }

    @DataBoundSetter
    public void setReplacement(String replacement) {
        this.replacement = replacement == null ? "" : replacement;
    }

    public int getLimit() {
        return limit;
    }

    @DataBoundSetter
    public void setLimit(int limit) {
        if (limit < 0 || limit > 10_000) {
            throw new IllegalArgumentException("Transform limit must be between 0 and 10000.");
        }
        this.limit = limit;
    }

    public boolean isDescending() {
        return descending;
    }

    @DataBoundSetter
    public void setDescending(boolean descending) {
        this.descending = descending;
    }

    List<Option> apply(List<Option> input) {
        return switch (operation) {
            case "filter" -> filter(input);
            case "sort" -> sort(input);
            case "distinct" -> distinct(input);
            case "limit" -> List.copyOf(input.subList(0, Math.min(limit, input.size())));
            case "map" -> map(input);
            case "group" -> group(input);
            case "mark-disabled" -> markDisabled(input);
            default -> throw new IllegalStateException(operation);
        };
    }

    String fingerprint() {
        return String.join(
                "\u0000", operation, field, pattern, replacement, String.valueOf(limit), String.valueOf(descending));
    }

    private List<Option> filter(List<Option> input) {
        Pattern compiled = compileSafe(pattern);
        return input.stream()
                .filter(option -> compiled.matcher(value(option)).find())
                .toList();
    }

    private List<Option> sort(List<Option> input) {
        Comparator<Option> comparator = Comparator.comparing(this::value, Comparator.nullsFirst(String::compareTo));
        if (descending) {
            comparator = comparator.reversed();
        }
        return input.stream().sorted(comparator).toList();
    }

    private List<Option> distinct(List<Option> input) {
        Map<String, Option> unique = new LinkedHashMap<>();
        for (Option option : input) {
            unique.putIfAbsent(value(option), option);
        }
        return List.copyOf(unique.values());
    }

    private List<Option> map(List<Option> input) {
        Pattern compiled = compileSafe(pattern);
        List<Option> output = new ArrayList<>();
        for (Option option : input) {
            String mapped = compiled.matcher(value(option)).replaceAll(replacement);
            output.add(copy(option, field, mapped, null));
        }
        return List.copyOf(output);
    }

    private List<Option> group(List<Option> input) {
        Pattern compiled = compileSafe(pattern);
        return input.stream()
                .map(option ->
                        compiled.matcher(value(option)).find() ? copy(option, "group", replacement, null) : option)
                .toList();
    }

    private List<Option> markDisabled(List<Option> input) {
        Pattern compiled = compileSafe(pattern);
        return input.stream()
                .map(option -> compiled.matcher(value(option)).find() ? copy(option, null, null, replacement) : option)
                .toList();
    }

    private String value(Option option) {
        String value =
                switch (field) {
                    case "value" -> option.getValue();
                    case "label" -> option.getLabel();
                    case "description" -> option.getDescription();
                    case "group" -> option.getGroup();
                    default -> throw new IllegalStateException(field);
                };
        return value == null ? "" : value;
    }

    private static Option copy(Option source, String field, String value, String disabledReason) {
        Option copy = new Option("value".equals(field) ? value : source.getValue());
        copy.setLabel("label".equals(field) ? value : source.getLabel());
        copy.setDescription("description".equals(field) ? value : source.getDescription());
        copy.setGroup("group".equals(field) ? value : source.getGroup());
        copy.setDisabled(disabledReason != null || source.isDisabled());
        copy.setDisabledReason(disabledReason == null ? source.getDisabledReason() : disabledReason);
        copy.setTags(source.getTags());
        return copy;
    }

    private static Pattern compileSafe(String expression) {
        if (expression == null || expression.length() > 256) {
            throw new IllegalArgumentException("Transform regex must be at most 256 characters.");
        }
        if (expression.matches(".*(\\\\[1-9]|\\(\\?<|\\(\\?=|\\(\\?!|\\{\\d+,?\\d*}\\s*[+*]).*")) {
            throw new IllegalArgumentException("Transform regex uses a prohibited construct.");
        }
        try {
            return Pattern.compile(expression);
        } catch (PatternSyntaxException exception) {
            throw new IllegalArgumentException("Transform regex is invalid.", exception);
        }
    }

    @Extension
    @Symbol("foundryTransform")
    public static final class DescriptorImpl extends Descriptor<TransformStep> {
        @Override
        public String getDisplayName() {
            return "Parameter Foundry Transform";
        }
    }
}
