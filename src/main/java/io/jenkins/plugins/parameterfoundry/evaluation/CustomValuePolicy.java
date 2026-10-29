package io.jenkins.plugins.parameterfoundry.evaluation;

import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class CustomValuePolicy {

    private CustomValuePolicy() {}

    public static void validate(FoundryChoiceParameterDefinition definition, String value) {
        if (!definition.isAllowCustomValue()) {
            throw new EvaluationException(ErrorCode.UNKNOWN_VALUE, definition.getName(), "Unsupported value: " + value);
        }
        if (value.length() > definition.getCustomValueMaxLength()) {
            throw new EvaluationException(
                    ErrorCode.CUSTOM_VALUE_INVALID,
                    definition.getName(),
                    "Custom value exceeds the configured maximum length.");
        }
        String expression = definition.getCustomValuePattern();
        if (expression != null && !compile(expression).matcher(value).matches()) {
            throw new EvaluationException(
                    ErrorCode.CUSTOM_VALUE_INVALID,
                    definition.getName(),
                    "Custom value does not match the configured pattern.");
        }
    }

    public static Pattern compile(String expression) {
        if (expression == null || expression.length() > 256) {
            throw new IllegalArgumentException("Custom-value regex must be at most 256 characters");
        }
        if (expression.matches(".*(\\\\[1-9]|\\(\\?<|\\(\\?=|\\(\\?!|\\{\\d+,?\\d*}\\s*[+*]).*")) {
            throw new IllegalArgumentException("Custom-value regex uses a prohibited construct");
        }
        try {
            return Pattern.compile(expression);
        } catch (PatternSyntaxException exception) {
            throw new IllegalArgumentException("Custom-value regex is invalid", exception);
        }
    }
}
