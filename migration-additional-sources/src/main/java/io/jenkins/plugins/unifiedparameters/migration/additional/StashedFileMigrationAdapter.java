package io.jenkins.plugins.unifiedparameters.migration.additional;

import hudson.Extension;
import hudson.model.ParameterDefinition;
import io.jenkins.plugins.unifiedparameters.migration.MigrationClassification;
import io.jenkins.plugins.unifiedparameters.migration.MigrationPlan;
import io.jenkins.plugins.unifiedparameters.migration.SourceMigrationAdapter;
import java.util.List;

@Extension
public final class StashedFileMigrationAdapter extends SourceMigrationAdapter {

    private static final String SOURCE_CLASS = "io.jenkins.plugins.file_parameters.StashedFileParameterDefinition";

    @Override
    public String getSourcePluginId() {
        return "file-parameters";
    }

    @Override
    public boolean supports(ParameterDefinition definition) {
        return definition != null
                && (SOURCE_CLASS.equals(definition.getClass().getName())
                        || definition.getClass().getName().endsWith(".StashedFileParameterDefinition"));
    }

    @Override
    public MigrationPlan inspect(ParameterDefinition definition) {
        if (!supports(definition)) {
            throw new IllegalArgumentException("Unsupported source definition");
        }
        return new MigrationPlan(
                MigrationClassification.D,
                "Stashed File is a multipart/file transport capability, not an option parameter; it must remain native or use a reviewed file companion.",
                null,
                List.of(
                        "Keep the native Stashed File parameter if the business requirement is uploading a file.",
                        "If the requirement is selecting a file-like option, redesign it as a governed Provider/Form field.",
                        "Verify stash name, workspace/file retention, credentials, remote triggers, and Rebuild behavior separately."),
                null,
                verificationFixture(definition));
    }

    private static String verificationFixture(ParameterDefinition definition) {
        return "For "
                + definition.getName()
                + ", verify native multipart submission, stash lifecycle, downstream file access, missing-file errors, "
                + "remote/upstream behavior, and Rebuild without converting it to a choice value.";
    }
}
