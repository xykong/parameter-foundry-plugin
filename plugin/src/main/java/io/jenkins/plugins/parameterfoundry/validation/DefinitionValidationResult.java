package io.jenkins.plugins.parameterfoundry.validation;

import java.util.ArrayList;
import java.util.List;

public final class DefinitionValidationResult {

    private final List<String> errors;
    private final List<String> warnings;

    DefinitionValidationResult(List<String> errors) {
        this(errors, List.of());
    }

    DefinitionValidationResult(List<String> errors, List<String> warnings) {
        this.errors = List.copyOf(new ArrayList<>(errors));
        this.warnings = List.copyOf(new ArrayList<>(warnings));
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    public List<String> getErrors() {
        return errors;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public String summarize() {
        return String.join("; ", errors);
    }
}
