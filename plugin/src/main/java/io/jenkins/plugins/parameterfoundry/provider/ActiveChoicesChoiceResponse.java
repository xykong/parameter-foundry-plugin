package io.jenkins.plugins.parameterfoundry.provider;

import io.jenkins.plugins.parameterfoundry.model.ChoiceSourceMetadata;
import io.jenkins.plugins.parameterfoundry.model.Option;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Data-only interpretation of the characterized ordinary/cascade source metadata. */
final class ActiveChoicesChoiceResponse {
    record Choice(
            String value,
            String label,
            boolean sourceSelected,
            boolean apiSelected,
            boolean renderFailure,
            boolean disabled,
            String apiValue,
            String selectedApiValue) {}

    record Response(List<Choice> choices, String apiDefault, int selectedIndex) {
        Response {
            choices = List.copyOf(choices);
        }
    }

    static List<Option> options(byte[] body, boolean cascade, String providerId) {
        return interpret(ActiveChoicesJsonResponse.parseRaw(body, providerId), cascade).choices().stream()
                .map(choice -> Option.sourceChoice(
                        choice.value(),
                        choice.label(),
                        choice.disabled(),
                        new ChoiceSourceMetadata(
                                choice.sourceSelected(),
                                choice.apiSelected(),
                                choice.renderFailure(),
                                choice.apiValue(),
                                choice.selectedApiValue())))
                .toList();
    }

    static Response interpret(Map<String, ?> raw, boolean cascade) {
        List<Choice> choices = new ArrayList<>();
        List<String> selectedApiValues = new ArrayList<>();
        int selectedIndex = -1;
        String firstApiValue = raw.isEmpty() ? "" : raw.keySet().iterator().next();
        for (Map.Entry<String, ?> entry : raw.entrySet()) {
            String key = entry.getKey();
            String javaLabel = entry.getValue() == null ? null : String.valueOf(entry.getValue());
            String label = javaLabel;
            boolean coercionFailure = false;
            if (cascade) {
                try {
                    label = SourceJsonText.javascript(entry.getValue());
                } catch (IllegalArgumentException exception) {
                    label = "";
                    coercionFailure = true;
                }
            }
            boolean selected = isSelected(label);
            boolean disabled = isDisabled(label);
            boolean renderFailure = coercionFailure
                    || cascade
                            && entry.getValue() != null
                            && !(entry.getValue() instanceof String)
                            && (selected || disabled);
            if (isSelected(javaLabel)) selectedApiValues.add(escapeSelected(escapeDisabled(key)));
            if (selected) {
                selectedIndex = choices.size();
            }
            String value;
            String caption;
            if (cascade) {
                value = cascadeEscape(key == null ? "null" : key);
                caption = cascadeEscape(label == null ? "null" : label);
            } else if (selected && disabled) {
                value = escapeDisabled(escapeSelected(key));
                caption = escapeDisabled(escapeSelected(label));
            } else if (disabled) {
                value = escapeDisabled(key);
                caption = escapeDisabled(label);
            } else {
                value = escapeSelected(key);
                caption = escapeSelected(label);
            }
            choices.add(new Choice(
                    value,
                    caption,
                    selected,
                    isSelected(javaLabel),
                    renderFailure,
                    disabled,
                    key == null ? "" : key,
                    escapeSelected(escapeDisabled(key))));
        }
        if (selectedIndex < 0) {
            for (int index = 0; index < choices.size(); index++) {
                if (!choices.get(index).disabled()) {
                    selectedIndex = index;
                    break;
                }
            }
        }
        String apiDefault = selectedApiValues.isEmpty()
                ? firstApiValue == null ? "" : firstApiValue
                : String.join(",", selectedApiValues);
        return new Response(List.copyOf(choices), apiDefault, selectedIndex);
    }

    private static boolean isSelected(String text) {
        return text != null && (text.endsWith(":selected") || text.endsWith(":selected:disabled"));
    }

    private static boolean isDisabled(String text) {
        return text != null && (text.endsWith(":disabled") || text.endsWith(":disabled:selected"));
    }

    private static String escapeSelected(String text) {
        if (text == null || text.isBlank()) return "";
        return isSelected(text)
                ? text.replaceAll(":selected$", "").replaceAll(":selected:disabled$", ":disabled")
                : text;
    }

    private static String escapeDisabled(String text) {
        if (text == null || text.isBlank()) return "";
        return isDisabled(text)
                ? text.replaceAll(":disabled$", "").replaceAll(":disabled:selected$", ":selected")
                : text;
    }

    private static String cascadeEscape(String text) {
        String result = text;
        if (isSelected(text)) {
            result = replaceSuffix(result, ":selected", "");
            result = replaceSuffix(result, ":selected:disabled", ":disabled");
        }
        if (isDisabled(text)) {
            result = replaceSuffix(result, ":disabled", "");
            result = replaceSuffix(result, ":disabled:selected", ":selected");
        }
        return result;
    }

    private static String replaceSuffix(String text, String suffix, String replacement) {
        return text.endsWith(suffix) ? text.substring(0, text.length() - suffix.length()) + replacement : text;
    }
}
