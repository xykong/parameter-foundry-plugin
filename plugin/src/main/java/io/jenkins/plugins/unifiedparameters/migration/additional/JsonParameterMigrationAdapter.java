package io.jenkins.plugins.unifiedparameters.migration.additional;

import hudson.Extension;
import hudson.model.ParameterDefinition;
import java.util.List;

@Extension
public final class JsonParameterMigrationAdapter extends AbstractAdditionalMigrationAdapter {

    @Override
    public String getSourcePluginId() {
        return "json-parameter";
    }

    @Override
    protected String sourceClassName() {
        return "com.github.cyanbaz.jenkins.plugins.jsonparameter.JsonParameterDefinition";
    }

    @Override
    public boolean supports(ParameterDefinition definition) {
        return super.supports(definition)
                || (definition != null
                        && "com.github.cyanbaz.jenkins.plugins.jsonparameter.JsonParameterReferenceDefinition"
                                .equals(definition.getClass().getName()));
    }

    @Override
    protected String capabilitySummary() {
        return "JSON Parameter and JSON Parameter Reference map to typed Provider/Form contracts.";
    }

    @Override
    protected String targetSkeleton(ParameterDefinition definition) {
        return "unifiedForm(\n"
                + "    name: "
                + quote(definition.getName())
                + ",\n"
                + "    fields: [/* TODO typed object fields */],\n"
                + "    output: 'json',\n"
                + "    renderer: 'schema'\n"
                + ")";
    }

    @Override
    protected List<String> decisions(ParameterDefinition definition) {
        return List.of(
                "Separate structured data from reference-driven option discovery and choose Provider versus Form.",
                "Confirm JSONPath/reference dependency semantics without executing source scripts in core.",
                "Verify schema/type/default/empty/unknown-field, dependency refresh, and structured-output parity.");
    }
}
