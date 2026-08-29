package io.jenkins.plugins.unifiedparameters.migration.additional;

import hudson.Extension;
import hudson.model.ParameterDefinition;
import java.util.List;

@Extension
public final class ValidatingStringMigrationAdapter extends AbstractAdditionalMigrationAdapter {

    @Override
    public String getSourcePluginId() {
        return "validating-string-parameter";
    }

    @Override
    protected String sourceClassName() {
        return "hudson.plugins.validating_string_parameter.ValidatingStringParameterDefinition";
    }

    @Override
    protected String capabilitySummary() {
        return "Validating String maps to a typed string field in Unified Form with a bounded server-side validator.";
    }

    @Override
    protected String targetSkeleton(ParameterDefinition definition) {
        return "unifiedForm(\n"
                + "    name: "
                + quote(definition.getName())
                + ",\n"
                + "    fields: [stringField(/* TODO regex, length, default, required */)],\n"
                + "    renderer: 'schema'\n"
                + ")";
    }

    @Override
    protected List<String> decisions(ParameterDefinition definition) {
        return List.of(
                "Translate the source validation expression into the safe regex/length subset or report manual redesign.",
                "Confirm validation timing, error text, required/empty/default behavior, and secret handling.",
                "Verify standard, Typed API, CLI, Pipeline, remote, and Rebuild error-code parity.");
    }
}
