package io.jenkins.plugins.parameterfoundry.evaluation;

import java.util.ArrayList;
import java.util.List;

public record DecodedInput(boolean present, List<String> values) {

    public DecodedInput {
        values = values == null ? List.of() : List.copyOf(new ArrayList<>(values));
    }

    public static DecodedInput absent() {
        return new DecodedInput(false, List.of());
    }

    public static DecodedInput present(List<String> values) {
        return new DecodedInput(true, values);
    }
}
