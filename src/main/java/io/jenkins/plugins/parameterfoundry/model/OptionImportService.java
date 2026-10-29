package io.jenkins.plugins.parameterfoundry.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

public final class OptionImportService {

    public static final int MAX_CONTENT_LENGTH = 1024 * 1024;
    public static final int MAX_OPTIONS = 10_000;

    private OptionImportService() {}

    public static List<Option> parse(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("Import content exceeds 1 MiB.");
        }
        Object decoded = decode(content);
        if (decoded instanceof Map<?, ?> map && map.containsKey("options")) {
            decoded = map.get("options");
        }
        if (!(decoded instanceof List<?> list)) {
            throw new IllegalArgumentException("Import root must be a list or an object containing options.");
        }
        if (list.size() > MAX_OPTIONS) {
            throw new IllegalArgumentException("Import contains more than 10,000 options.");
        }
        List<Option> options = new ArrayList<>();
        Set<String> values = new HashSet<>();
        for (Object item : list) {
            Option option = toOption(item);
            if (!values.add(option.getValue())) {
                throw new IllegalArgumentException("Import contains duplicate option value: " + option.getValue());
            }
            options.add(option);
        }
        return List.copyOf(options);
    }

    private static Object decode(String content) {
        try {
            String trimmed = content.stripLeading();
            if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
                Object json = trimmed.startsWith("[") ? JSONArray.fromObject(content) : JSONObject.fromObject(content);
                return normalizeJson(json);
            }
            LoaderOptions options = new LoaderOptions();
            options.setMaxAliasesForCollections(0);
            options.setNestingDepthLimit(20);
            options.setCodePointLimit(MAX_CONTENT_LENGTH);
            return new Yaml(new SafeConstructor(options)).load(content);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Import content is not valid YAML or JSON.");
        }
    }

    private static Object normalizeJson(Object value) {
        if (value instanceof JSONArray array) {
            List<Object> values = new ArrayList<>();
            for (Object item : array) {
                values.add(normalizeJson(item));
            }
            return values;
        }
        if (value instanceof JSONObject object) {
            return object;
        }
        return value;
    }

    private static Option toOption(Object item) {
        if (item instanceof String string) {
            return new Option(string);
        }
        if (!(item instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Each option must be a string or object.");
        }
        String value = string(map.get("value"));
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Each option requires a non-empty value.");
        }
        Option option = new Option(value);
        option.setLabel(string(map.get("label")));
        option.setDescription(string(map.get("description")));
        option.setGroup(string(map.get("group")));
        option.setDisabled(Boolean.TRUE.equals(map.get("disabled")));
        option.setDisabledReason(string(map.get("disabledReason")));
        Object tags = map.get("tags");
        if (tags instanceof List<?> list) {
            option.setTags(list.stream().map(String::valueOf).toList());
        }
        return option;
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
