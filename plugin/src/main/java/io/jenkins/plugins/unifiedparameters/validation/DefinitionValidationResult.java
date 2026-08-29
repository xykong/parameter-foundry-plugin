package io.jenkins.plugins.unifiedparameters.validation;

import java.util.ArrayList;
import java.util.List;

public final class DefinitionValidationResult {

    private final List<String> errors;

    DefinitionValidationResult(List<String> errors) {
        this.errors = List.copyOf(new ArrayList<>(errors));
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public List<String> getErrors() {
        return errors;
    }

    public String summarize() {
        return String.join("; ", errors);
    }
}
