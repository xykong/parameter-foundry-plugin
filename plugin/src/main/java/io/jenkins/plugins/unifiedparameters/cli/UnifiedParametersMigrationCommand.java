package io.jenkins.plugins.unifiedparameters.cli;

import hudson.Extension;
import hudson.cli.CLICommand;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.ParametersDefinitionProperty;
import io.jenkins.plugins.unifiedparameters.migration.MigrationBatchEntry;
import io.jenkins.plugins.unifiedparameters.migration.MigrationBatchPreview;
import io.jenkins.plugins.unifiedparameters.migration.MigrationBatchResult;
import io.jenkins.plugins.unifiedparameters.migration.MigrationCoordinator;
import io.jenkins.plugins.unifiedparameters.migration.MigrationRecord;
import java.util.ArrayList;
import java.util.List;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.args4j.Option;

/**
 * Scriptable migration entry point for the migration window.
 *
 * <p>Without {@code -job} the command prints the controller-wide inventory of parameters that a
 * migration adapter recognizes. With {@code -job} it performs a dry run and, only when
 * {@code -apply} is given together with {@code -accept-decisions}, applies one atomic batch.
 */
@Extension
public final class UnifiedParametersMigrationCommand extends CLICommand {

    @Option(name = "-job", usage = "Full name of the Job to preview or migrate.")
    public String job;

    @Option(name = "-parameter", usage = "Parameter name to limit the batch to. Repeat for several parameters.")
    public List<String> parameters = new ArrayList<>();

    @Option(
            name = "-apply",
            usage = "Apply the batch after the dry run. Requires -accept-decisions when decisions exist.")
    public boolean apply;

    @Option(name = "-accept-decisions", usage = "Accept every semantic decision reported by the dry run.")
    public boolean acceptDecisions;

    @Option(name = "-json", usage = "Print the result as JSON.")
    public boolean json;

    @Override
    public String getName() {
        return "unified-parameters-migration";
    }

    @Override
    public String getShortDescription() {
        return "Inventory, preview, and migrate source parameter definitions in one atomic batch.";
    }

    @Override
    protected int run() throws Exception {
        if (job == null || job.isBlank()) {
            return scan();
        }
        Job<?, ?> target = Jenkins.get().getItemByFullName(job, Job.class);
        if (target == null) {
            throw new IllegalArgumentException("No such Job: " + job);
        }
        MigrationBatchPreview preview = MigrationCoordinator.previewBatch(target, parameters);
        if (json) {
            stdout.println(toJson(preview, null));
            return preview.isApplicable() ? 0 : 2;
        }
        printPreview(preview);
        if (!apply) {
            return preview.isApplicable() ? 0 : 2;
        }
        if (!preview.getRequiredDecisions().isEmpty() && !acceptDecisions) {
            stdout.println("Refused: the batch carries semantic decisions. Re-run with -accept-decisions.");
            return 3;
        }
        MigrationBatchResult result = MigrationCoordinator.applyBatch(
                target, preview, acceptDecisions ? preview.getRequiredDecisions() : List.of());
        stdout.println("Applied batch " + result.getBatchId() + " to " + result.getJobFullName() + ":");
        for (MigrationRecord record : result.getRecords()) {
            stdout.println(
                    "  " + record.getParameterName() + " (" + record.getAdapterId() + ") -> " + record.getTargetJob());
        }
        return 0;
    }

    private int scan() {
        List<MigrationBatchPreview> previews = new ArrayList<>();
        List<String[]> errors = new ArrayList<>();
        int skippedByPermission = 0;
        int skippedWithoutParameters = 0;
        for (Job<?, ?> candidate : Jenkins.get().getAllItems(Job.class)) {
            if (!candidate.hasPermission(Item.CONFIGURE)) {
                skippedByPermission++;
                continue;
            }
            if (candidate.getProperty(ParametersDefinitionProperty.class) == null) {
                skippedWithoutParameters++;
                continue;
            }
            MigrationBatchPreview preview;
            try {
                preview = MigrationCoordinator.previewBatch(candidate, List.of());
            } catch (RuntimeException exception) {
                if (errors.size() < 20) {
                    errors.add(new String[] {candidate.getFullName(), String.valueOf(exception.getMessage())});
                }
                continue;
            }
            if (preview.isApplicable() || preview.getBlockedCount() > 0) {
                previews.add(preview);
            }
        }
        int applicable = 0;
        int blocked = 0;
        for (MigrationBatchPreview preview : previews) {
            applicable += preview.getApplicableCount();
            blocked += preview.getBlockedCount();
        }
        if (json) {
            JSONArray jobs = new JSONArray();
            for (MigrationBatchPreview preview : previews) {
                JSONObject entry = new JSONObject();
                entry.put("job", preview.getJobFullName());
                entry.put("applicable", preview.getApplicableCount());
                entry.put("blocked", preview.getBlockedCount());
                entry.put("parameters", entries(preview.getEntries()));
                entry.put("blockedParameters", entries(preview.getBlocked()));
                jobs.add(entry);
            }
            JSONObject summary = new JSONObject();
            summary.put("jobs", previews.size());
            summary.put("applicableParameters", applicable);
            summary.put("blockedParameters", blocked);
            summary.put("skippedByPermission", skippedByPermission);
            summary.put("skippedWithoutParameters", skippedWithoutParameters);
            summary.put("scanErrors", errors.size());
            JSONArray encodedErrors = new JSONArray();
            for (String[] error : errors) {
                JSONObject item = new JSONObject();
                item.put("job", error[0]);
                item.put("error", error[1]);
                encodedErrors.add(item);
            }
            summary.put("errors", encodedErrors);
            JSONObject body = new JSONObject();
            body.put("summary", summary);
            body.put("jobs", jobs);
            stdout.println(body.toString(2));
            return 0;
        }
        for (MigrationBatchPreview preview : previews) {
            stdout.println(preview.getJobFullName() + ": " + preview.getApplicableCount() + " applicable, "
                    + preview.getBlockedCount() + " need a decision");
            for (MigrationBatchEntry entry : preview.getEntries()) {
                stdout.println("  - " + entry.parameterName() + " [" + entry.sourceClass() + "] Class "
                        + entry.classification() + " -> " + entry.targetType() + ", "
                        + entry.decisions().size() + " decision(s), "
                        + entry.diff().split("\n").length + " diff line(s)");
            }
            for (MigrationBatchEntry entry : preview.getBlocked()) {
                stdout.println("  ! " + entry.parameterName() + " [" + entry.sourceClass() + "] Class "
                        + entry.classification() + ": " + entry.reason());
            }
        }
        stdout.println("Summary: " + previews.size() + " Job(s), " + applicable + " applicable parameter(s), " + blocked
                + " parameter(s) that need a decision.");
        stdout.println("Skipped: " + skippedByPermission + " Job(s) without Job/Configure permission, "
                + skippedWithoutParameters + " Job(s) without parameter definitions, " + errors.size()
                + " Job(s) that could not be scanned.");
        for (String[] error : errors) {
            stdout.println("  ! " + error[0] + ": " + error[1]);
        }
        return 0;
    }

    private void printPreview(MigrationBatchPreview preview) {
        stdout.println("Job: " + preview.getJobFullName());
        stdout.println(
                "Applicable: " + preview.getApplicableCount() + ", needs a decision: " + preview.getBlockedCount());
        for (MigrationBatchEntry entry : preview.getBlocked()) {
            stdout.println("! " + entry.parameterName() + " [" + entry.sourceClass() + "] Class "
                    + entry.classification() + ": " + entry.reason());
        }
        for (MigrationBatchEntry entry : preview.getEntries()) {
            stdout.println("- " + entry.parameterName() + " [" + entry.sourceClass() + "] Class "
                    + entry.classification() + " -> " + entry.targetType());
            for (String decision : entry.decisions()) {
                stdout.println("    decision: " + decision);
            }
            stdout.println(entry.diff());
        }
        if (!preview.getRequiredDecisions().isEmpty()) {
            stdout.println(
                    "Decisions to accept: " + preview.getRequiredDecisions().size());
        }
    }

    private JSONObject toJson(MigrationBatchPreview preview, MigrationBatchResult result) {
        JSONObject body = new JSONObject();
        body.put("job", preview.getJobFullName());
        body.put("jobDigest", preview.getJobDigest());
        body.put("applicable", preview.getApplicableCount());
        body.put("blocked", preview.getBlockedCount());
        body.put("parameters", entries(preview.getEntries()));
        body.put("blockedParameters", entries(preview.getBlocked()));
        body.put("requiredDecisions", JSONArray.fromObject(preview.getRequiredDecisions()));
        body.put("diff", preview.getDiff());
        if (result != null) {
            JSONArray records = new JSONArray();
            for (MigrationRecord record : result.getRecords()) {
                JSONObject encoded = new JSONObject();
                encoded.put("id", record.getId());
                encoded.put("parameter", record.getParameterName());
                encoded.put("adapter", record.getAdapterId());
                encoded.put("status", record.getStatus());
                records.add(encoded);
            }
            body.put("batchId", result.getBatchId());
            body.put("records", records);
        }
        return body;
    }

    private static JSONArray entries(List<MigrationBatchEntry> entries) {
        JSONArray encoded = new JSONArray();
        for (MigrationBatchEntry entry : entries) {
            JSONObject item = new JSONObject();
            item.put("parameter", entry.parameterName());
            item.put("sourceClass", entry.sourceClass());
            item.put("adapter", entry.sourcePluginId());
            item.put("classification", entry.classification());
            item.put("applicable", entry.isApplicable());
            item.put("targetType", entry.targetType());
            item.put("reason", entry.reason());
            item.put("decisions", JSONArray.fromObject(entry.decisions()));
            item.put("diff", entry.diff());
            encoded.add(item);
        }
        return encoded;
    }
}
