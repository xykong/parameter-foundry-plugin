package io.jenkins.plugins.unifiedparameters.migration.additional;

import hudson.Extension;
import hudson.model.ParameterDefinition;
import java.util.List;

@Extension
public final class RestListMigrationAdapter extends AbstractAdditionalMigrationAdapter {

    @Override
    public String getSourcePluginId() {
        return "rest-list-parameter";
    }

    @Override
    protected String sourceClassName() {
        return "io.jenkins.plugins.restlistparam.RestListParameterDefinition";
    }

    @Override
    protected String capabilitySummary() {
        return "REST List maps to the governed REST JSON/XML Provider with Connection Profile and bounded parsing.";
    }

    @Override
    protected String targetSkeleton(ParameterDefinition definition) {
        return "unifiedChoice(\n"
                + "    name: "
                + quote(definition.getName())
                + ",\n"
                + "    providerId: 'TODO-governed-rest-provider',\n"
                + "    renderer: 'searchable'\n"
                + ")";
    }

    @Override
    protected List<String> decisions(ParameterDefinition definition) {
        return List.of(
                "Bind authority, path allowlists, credentials, timeout, response size, and redirect policy through a Connection Profile.",
                "Map response value/label paths to the bounded JSONPath/XPath subset.",
                "Verify SSRF policy, content type, empty/error/fallback, dependency query values, and snapshot behavior.");
    }
}
