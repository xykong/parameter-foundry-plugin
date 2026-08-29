package io.jenkins.plugins.unifiedparameters.migration.additional;

import hudson.Extension;
import hudson.model.ParameterDefinition;
import java.util.List;

@Extension
public final class HtmlParametersMigrationAdapter extends AbstractAdditionalMigrationAdapter {

    @Override
    public String getSourcePluginId() {
        return "html-parameters";
    }

    @Override
    protected String sourceClassName() {
        return "org.jenkinsci.plugins.uiparameter.HtmlFormParameterDefinition";
    }

    @Override
    protected String capabilitySummary() {
        return "HTML Parameters must be re-authored as typed fields and trusted layouts; arbitrary HTML/JavaScript is not migrated.";
    }

    @Override
    protected String targetSkeleton(ParameterDefinition definition) {
        return "unifiedForm(\n"
                + "    name: "
                + quote(definition.getName())
                + ",\n"
                + "    fields: [/* TODO typed fields from reviewed mappings */],\n"
                + "    layouts: [/* TODO sections/grid/conditions */],\n"
                + "    renderer: 'schema'\n"
                + ")";
    }

    @Override
    protected List<String> decisions(ParameterDefinition definition) {
        return List.of(
                "Convert each HTML control mapping into an explicit typed field; presentation-only markup is discarded.",
                "Replace custom CSS/JavaScript with layout schema, design tokens, or a separately reviewed Renderer companion.",
                "Confirm output field names, required/type validation, conditional visibility, and no-JavaScript behavior.");
    }
}
