package io.jenkins.plugins.parameterfoundry.provider;

import io.jenkins.plugins.parameterfoundry.model.Option;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/** Whole-response data interpretation; transport failures are deliberately handled elsewhere. */
final class RawScalarChoiceAdapter {
    private RawScalarChoiceAdapter() {}

    static List<Option> options(byte[] body, List<String> ignoredResponses, List<String> trailingChoices) {
        return ActiveChoicesChoiceResponse.options(interpret(body, ignoredResponses, trailingChoices));
    }

    static ActiveChoicesChoiceResponse.Response interpret(
            byte[] body, List<String> ignoredResponses, List<String> trailingChoices) {
        Objects.requireNonNull(body, "Response body is required.");
        Objects.requireNonNull(ignoredResponses, "Ignored responses are required.");
        Objects.requireNonNull(trailingChoices, "Trailing choices are required.");
        String value = new String(body, StandardCharsets.UTF_8);
        var choices = new LinkedHashMap<String, String>();
        if (!ignoredResponses.contains(value)) {
            choices.put(value, value);
        }
        for (String trailing : trailingChoices) {
            Objects.requireNonNull(trailing, "Trailing choices must be strings.");
            choices.put(trailing, trailing);
        }
        return ActiveChoicesChoiceResponse.interpret(choices, false);
    }
}
