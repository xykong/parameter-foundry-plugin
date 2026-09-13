package io.jenkins.plugins.unifiedparameters.api;

import hudson.model.Action;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import io.jenkins.plugins.unifiedparameters.evaluation.EvaluationException;
import io.jenkins.plugins.unifiedparameters.migration.MigrationBatchEntry;
import io.jenkins.plugins.unifiedparameters.migration.MigrationBatchPreview;
import io.jenkins.plugins.unifiedparameters.migration.MigrationCoordinator;
import io.jenkins.plugins.unifiedparameters.migration.MigrationEvidence;
import io.jenkins.plugins.unifiedparameters.migration.MigrationEvidenceService;
import io.jenkins.plugins.unifiedparameters.migration.MigrationPreview;
import io.jenkins.plugins.unifiedparameters.migration.MigrationRecord;
import io.jenkins.plugins.unifiedparameters.migration.MigrationRecordStore;
import io.jenkins.plugins.unifiedparameters.migration.SourceMigrationAdapter;
import io.jenkins.plugins.unifiedparameters.retirement.RetirementScanner;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.verb.GET;
import org.kohsuke.stapler.verb.POST;

public final class UnifiedParametersJobAction implements Action {

    private final Job<?, ?> job;

    UnifiedParametersJobAction(Job<?, ?> job) {
        this.job = job;
    }

    public ApiRoot getApi() {
        return new ApiRoot(job);
    }

    public Job<?, ?> getJob() {
        return job;
    }

    public List<MigrationPreview> getMigrationPreviews() {
        job.checkPermission(Item.READ);
        job.checkPermission(Item.CONFIGURE);
        ParametersDefinitionProperty property = job.getProperty(ParametersDefinitionProperty.class);
        if (property == null) {
            return List.of();
        }
        List<MigrationPreview> previews = new ArrayList<>();
        for (ParameterDefinition definition : property.getParameterDefinitions()) {
            for (SourceMigrationAdapter adapter : SourceMigrationAdapter.all()) {
                if (!adapter.isEnabled() || !adapter.supports(definition)) {
                    continue;
                }
                try {
                    previews.add(MigrationPreview.from(adapter, definition, adapter.inspect(definition)));
                } catch (RuntimeException exception) {
                    previews.add(MigrationPreview.failed(adapter, definition));
                }
                break;
            }
        }
        return List.copyOf(previews);
    }

    public boolean hasMigrationCandidates() {
        if (!job.hasPermission(Item.CONFIGURE)) {
            return false;
        }
        ParametersDefinitionProperty property = job.getProperty(ParametersDefinitionProperty.class);
        if (property == null) {
            return false;
        }
        for (ParameterDefinition definition : property.getParameterDefinitions()) {
            for (SourceMigrationAdapter adapter : SourceMigrationAdapter.all()) {
                if (adapter.isEnabled() && adapter.supports(definition)) {
                    return true;
                }
            }
        }
        return false;
    }

    public List<MigrationRecord> getMigrationRecords() {
        job.checkPermission(Item.READ);
        return MigrationRecordStore.get().getRecords().stream()
                .filter(record -> record.getTargetJob().equals(job.getFullName()))
                .toList();
    }

    /** Dry run for every parameter of the Job, including the exact target diff. */
    public MigrationBatchPreview getBatchPreview() {
        if (job.getProperty(ParametersDefinitionProperty.class) == null) {
            return new MigrationBatchPreview(job.getFullName(), "", List.of(), List.of());
        }
        return MigrationCoordinator.previewBatch(job, List.of());
    }

    /** Applied batches of this Job, newest first, for the rollback section. */
    public List<MigrationBatchView> getMigrationBatches() {
        job.checkPermission(Item.READ);
        Map<String, List<MigrationRecord>> byBatch = new LinkedHashMap<>();
        for (MigrationRecord record : getMigrationRecords()) {
            if (record.getBatchId() != null) {
                byBatch.computeIfAbsent(record.getBatchId(), ignored -> new ArrayList<>())
                        .add(record);
            }
        }
        List<MigrationBatchView> batches = new ArrayList<>();
        byBatch.forEach((batchId, records) -> batches.add(new MigrationBatchView(batchId, records)));
        return List.copyOf(batches);
    }

    /** The parameters of an SCM-defined Pipeline cannot be read from the Job configuration. */
    public boolean isParametersDefinedInRepository() {
        return RetirementScanner.definesParametersInRepository(job);
    }

    @GET
    public void doPreviewBatch(
            StaplerResponse2 response, @QueryParameter String parameters, @QueryParameter boolean json)
            throws java.io.IOException {
        List<String> names = new ArrayList<>();
        if (parameters != null) {
            for (String name : parameters.split(",")) {
                if (!name.isBlank()) {
                    names.add(name.trim());
                }
            }
        }
        try {
            MigrationBatchPreview preview = MigrationCoordinator.previewBatch(job, names);
            JSONObject body = new JSONObject();
            body.put("job", preview.getJobFullName());
            body.put("jobDigest", preview.getJobDigest());
            body.put("applicable", preview.getApplicableCount());
            body.put("blocked", preview.getBlockedCount());
            body.put("diff", preview.getDiff());
            body.put("requiredDecisions", JSONArray.fromObject(preview.getRequiredDecisions()));
            JSONArray entries = new JSONArray();
            for (MigrationBatchEntry entry : preview.getEntries()) {
                entries.add(encode(entry));
            }
            body.put("parameters", entries);
            JSONArray blocked = new JSONArray();
            for (MigrationBatchEntry entry : preview.getBlocked()) {
                blocked.add(encode(entry));
            }
            body.put("blockedParameters", blocked);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(body.toString());
        } catch (EvaluationException exception) {
            throw HttpResponses.errorWithoutStack(409, exception.getSafeMessage());
        }
    }

    @POST
    public void doApplyBatch(
            StaplerRequest2 request,
            StaplerResponse2 response,
            @QueryParameter String parameters,
            @QueryParameter boolean acceptDecisions)
            throws java.io.IOException {
        try {
            List<String> names = new ArrayList<>();
            if (parameters != null) {
                for (String name : parameters.split(",")) {
                    if (!name.isBlank()) {
                        names.add(name.trim());
                    }
                }
            }
            MigrationBatchPreview preview = MigrationCoordinator.previewBatch(job, names);
            List<String> accepted = acceptDecisions ? preview.getRequiredDecisions() : List.of();
            MigrationCoordinator.applyBatch(job, preview, accepted);
            response.sendRedirect(request.getContextPath() + "/" + job.getUrl());
        } catch (EvaluationException exception) {
            throw HttpResponses.errorWithoutStack(409, exception.getSafeMessage());
        }
    }

    @POST
    public void doRollbackBatch(StaplerRequest2 request, StaplerResponse2 response, @QueryParameter String batchId)
            throws java.io.IOException {
        try {
            MigrationCoordinator.rollbackBatch(job, batchId);
            response.sendRedirect(request.getContextPath() + "/" + job.getUrl());
        } catch (EvaluationException exception) {
            throw HttpResponses.errorWithoutStack(409, exception.getSafeMessage());
        }
    }

    @POST
    public void doVerifyMigration(
            StaplerRequest2 request,
            StaplerResponse2 response,
            @QueryParameter String recordId,
            @QueryParameter Integer sourceRun,
            @QueryParameter Integer targetRun)
            throws java.io.IOException {
        try {
            MigrationEvidenceService.verify(job, recordId, sourceRun, targetRun);
            response.sendRedirect(request.getContextPath() + "/" + job.getUrl());
        } catch (EvaluationException exception) {
            throw HttpResponses.errorWithoutStack(409, exception.getSafeMessage());
        }
    }

    /** Evidence of one migration record, exposed for the page and for JSON clients. */
    public MigrationEvidence getEvidence(@QueryParameter String recordId) {
        job.checkPermission(Item.READ);
        if (recordId == null || recordId.isBlank()) {
            return null;
        }
        try {
            return MigrationRecordStore.get().findRecord(recordId).getEvidence();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static JSONObject encode(MigrationBatchEntry entry) {
        JSONObject encoded = new JSONObject();
        encoded.put("parameter", entry.parameterName());
        encoded.put("sourceClass", entry.sourceClass());
        encoded.put("adapter", entry.sourcePluginId());
        encoded.put("classification", entry.classification());
        encoded.put("applicable", entry.isApplicable());
        encoded.put("targetType", entry.targetType());
        encoded.put("reason", entry.reason());
        encoded.put("decisions", JSONArray.fromObject(entry.decisions()));
        encoded.put("diff", entry.diff());
        return encoded;
    }

    @POST
    public void doCloneMigration(
            StaplerRequest2 request,
            StaplerResponse2 response,
            @QueryParameter String parameterName,
            @QueryParameter String cloneName,
            @QueryParameter boolean acceptDecisions)
            throws java.io.IOException {
        try {
            var preview = MigrationCoordinator.preview(job, parameterName);
            List<String> accepted = acceptDecisions ? preview.requiredDecisions() : List.of();
            MigrationRecord record = MigrationCoordinator.cloneAndApply(job, cloneName, preview, accepted);
            Job<?, ?> clone = jenkins.model.Jenkins.get().getItemByFullName(record.getTargetJob(), Job.class);
            response.sendRedirect(request.getContextPath() + "/" + (clone == null ? job.getUrl() : clone.getUrl()));
        } catch (io.jenkins.plugins.unifiedparameters.evaluation.EvaluationException exception) {
            throw HttpResponses.errorWithoutStack(409, exception.getSafeMessage());
        }
    }

    @POST
    public void doApplyMigration(
            StaplerRequest2 request,
            StaplerResponse2 response,
            @QueryParameter String parameterName,
            @QueryParameter boolean acceptDecisions)
            throws java.io.IOException {
        try {
            var preview = MigrationCoordinator.preview(job, parameterName);
            List<String> accepted = acceptDecisions ? preview.requiredDecisions() : List.of();
            MigrationCoordinator.applyInPlace(job, preview, accepted);
            response.sendRedirect(request.getContextPath() + "/" + job.getUrl());
        } catch (io.jenkins.plugins.unifiedparameters.evaluation.EvaluationException exception) {
            throw HttpResponses.errorWithoutStack(409, exception.getSafeMessage());
        }
    }

    @POST
    public void doRollbackMigration(StaplerRequest2 request, StaplerResponse2 response, @QueryParameter String recordId)
            throws java.io.IOException {
        try {
            MigrationCoordinator.rollback(job, recordId);
            response.sendRedirect(request.getContextPath() + "/" + job.getUrl());
        } catch (io.jenkins.plugins.unifiedparameters.evaluation.EvaluationException exception) {
            throw HttpResponses.errorWithoutStack(409, exception.getSafeMessage());
        }
    }

    public Object getDynamic(String token, StaplerRequest2 request, StaplerResponse2 response) {
        if ("api".equals(token)) {
            return new ApiRoot(job);
        }
        return null;
    }

    @Override
    public String getIconFileName() {
        return hasMigrationCandidates() ? "symbol-parameters" : null;
    }

    @Override
    public String getDisplayName() {
        return "Unified Parameters Migration";
    }

    @Override
    public String getUrlName() {
        return "unified-parameters";
    }

    public static final class ApiRoot {
        private final Job<?, ?> job;

        ApiRoot(Job<?, ?> job) {
            this.job = job;
        }

        public V1Api getV1() {
            return new V1Api(job);
        }

        public Object getDynamic(String token, StaplerRequest2 request, StaplerResponse2 response) {
            if ("v1".equals(token)) {
                return new V1Api(job);
            }
            return null;
        }
    }
}
