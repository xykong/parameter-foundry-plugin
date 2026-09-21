package io.jenkins.plugins.unifiedparameters.cli;

import hudson.Extension;
import hudson.cli.CLICommand;
import hudson.model.Job;
import io.jenkins.plugins.unifiedparameters.migration.MigrationEvidence;
import io.jenkins.plugins.unifiedparameters.migration.MigrationEvidenceService;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.args4j.Option;

/**
 * Records the A/B evidence for one migration: the same input must produce the same canonical
 * value, environment export, and downstream value before and after the migration.
 *
 * <p>Exit codes: 0 verified, 1 mismatch, 2 waiting for a comparable build.
 */
@Extension
public final class UnifiedParametersMigrationVerifyCommand extends CLICommand {

    @Option(name = "-job", usage = "Full name of the migrated Job.", required = true)
    public String job;

    @Option(name = "-record", usage = "Migration record id to verify.", required = true)
    public String record;

    @Option(name = "-source-run", usage = "Build number that used the source definition.")
    public Integer sourceRun;

    @Option(name = "-target-run", usage = "Build number that used the target definition.")
    public Integer targetRun;

    @Option(name = "-json", usage = "Print the evidence as JSON.")
    public boolean json;

    @Override
    public String getName() {
        return "unified-parameters-migration-verify";
    }

    @Override
    public String getShortDescription() {
        return "Compare a migrated parameter before and after the migration and record the evidence.";
    }

    @Override
    protected int run() throws Exception {
        Job<?, ?> target = Jenkins.get().getItemByFullName(job, Job.class);
        if (target == null) {
            throw new IllegalArgumentException("No such Job: " + job);
        }
        MigrationEvidence evidence = MigrationEvidenceService.verify(target, record, sourceRun, targetRun);
        if (json) {
            JSONObject body = new JSONObject();
            body.put("status", evidence.getStatus());
            body.put("summary", evidence.getSummary());
            body.put("sourceRun", evidence.getSourceRun());
            body.put("targetRun", evidence.getTargetRun());
            body.put("sourceValue", evidence.getSourceValue());
            body.put("targetValue", evidence.getTargetValue());
            body.put("environmentDifferences", evidence.getEnvironmentDifferences());
            body.put("recordedAt", evidence.getRecordedAt());
            body.put("actor", evidence.getActor());
            stdout.println(body.toString(2));
        } else {
            stdout.println(evidence.getStatus() + ": " + evidence.getSummary());
            stdout.println("source run: " + evidence.getSourceRun());
            stdout.println("target run: " + evidence.getTargetRun());
            stdout.println("source value: " + evidence.getSourceValue());
            stdout.println("target value: " + evidence.getTargetValue());
        }
        return switch (evidence.getStatus()) {
            case MigrationEvidence.PASS -> 0;
            case MigrationEvidence.FAIL -> 1;
            default -> 2;
        };
    }
}
