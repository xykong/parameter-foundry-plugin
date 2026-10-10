package io.jenkins.plugins.parameterfoundry.provider;

import groovy.json.JsonSlurper;
import io.jenkins.plugins.parameterfoundry.evaluation.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Data-only interpretation of the reviewed JSON data-or-root source idiom. */
final class ActiveChoicesJsonResponse {
    private static final int MAX_RESPONSE_BYTES = 10 * 1024 * 1024;
    private static final int MAX_DEPTH = 64;
    private static final int MAX_CHOICES = 10_000;

    private ActiveChoicesJsonResponse() {}

    static Map<String, String> parse(byte[] body, String providerId) {
        try {
            if (body.length > MAX_RESPONSE_BYTES) {
                throw new IllegalArgumentException("Response exceeds the byte limit.");
            }
            String text = new String(body, StandardCharsets.UTF_8);
            requireBoundedDepth(text);
            Object root = new JsonSlurper().parseText(text);
            Object selected;
            if (root instanceof Map<?, ?> map) {
                Object data = map.get("data");
                selected = truthy(data) ? data : root;
            } else if (root instanceof List<?> list) {
                List<Object> data = new ArrayList<>();
                for (Object item : list) {
                    if (!(item instanceof Map<?, ?> map)) {
                        throw new IllegalArgumentException("List entries do not have a data property.");
                    }
                    data.add(map.get("data"));
                }
                selected = data.isEmpty() ? root : data;
            } else {
                throw new IllegalArgumentException("Response is not a supported data envelope.");
            }
            Map<String, String> result = new LinkedHashMap<>();
            if (selected instanceof List<?> list) {
                for (Object item : list) {
                    String value = item == null ? null : String.valueOf(item);
                    putChoice(result, value, value);
                }
            } else if (selected instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    putChoice(
                            result,
                            String.valueOf(entry.getKey()),
                            entry.getValue() == null ? null : String.valueOf(entry.getValue()));
                }
            }
            // Non-sandbox source scripts may return a scalar successfully; getChoices
            // then returns an empty map rather than evaluating the fallback script.
            return result;
        } catch (RuntimeException exception) {
            throw new ProviderException(
                    ErrorCode.PROVIDER_UNAVAILABLE,
                    providerId,
                    "Provider JSON does not match the reviewed Active Choices response contract.");
        }
    }

    private static void putChoice(Map<String, String> choices, String value, String label) {
        choices.put(value, label);
        if (choices.size() > MAX_CHOICES) {
            throw new IllegalArgumentException("Response exceeds the distinct choice limit.");
        }
    }

    /** Check nesting before the recursive JSON parser, ignoring delimiters inside strings. */
    private static void requireBoundedDepth(String text) {
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (quoted) {
                if (escaped) {
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == '"') {
                    quoted = false;
                }
            } else if (character == '"') {
                quoted = true;
            } else if (character == '[' || character == '{') {
                if (++depth > MAX_DEPTH) {
                    throw new IllegalArgumentException("Response exceeds the nesting limit.");
                }
            } else if (character == ']' || character == '}') {
                depth--;
            }
        }
    }

    private static boolean truthy(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean bool) return bool;
        if (value instanceof CharSequence text) return !text.isEmpty();
        if (value instanceof List<?> list) return !list.isEmpty();
        if (value instanceof Map<?, ?> map) return !map.isEmpty();
        if (value instanceof Number number) return number.doubleValue() != 0;
        return true;
    }
}
