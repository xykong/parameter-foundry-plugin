package io.jenkins.plugins.parameterfoundry.migration.additional;

import hudson.Extension;
import hudson.model.ParameterDefinition;
import io.jenkins.plugins.parameterfoundry.migration.MigrationText;
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
    protected String reasonKey() {
        return "MigrationReasonHtmlParameters";
    }

    @Override
    protected String targetSkeleton(ParameterDefinition definition) {
        return "foundryForm(\n"
                + "    name: "
                + quote(definition.getName())
                + ",\n"
                + "    fields: [/* TODO typed fields from reviewed mappings */],\n"
                + "    layouts: [/* TODO sections/grid/conditions */],\n"
                + "    renderer: 'schema'\n"
                + ")";
    }

    @Override
    protected List<MigrationText> decisions(ParameterDefinition definition) {
        return List.of(
                MigrationText.of("MigrationDecisionHtmlParameters1"),
                MigrationText.of("MigrationDecisionHtmlParameters2"),
                MigrationText.of("MigrationDecisionHtmlParameters3"));
    }
}
