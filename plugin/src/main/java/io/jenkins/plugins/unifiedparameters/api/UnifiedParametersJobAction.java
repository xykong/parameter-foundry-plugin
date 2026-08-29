package io.jenkins.plugins.unifiedparameters.api;

import hudson.model.Action;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import io.jenkins.plugins.unifiedparameters.migration.MigrationCoordinator;
import io.jenkins.plugins.unifiedparameters.migration.MigrationPreview;
import io.jenkins.plugins.unifiedparameters.migration.MigrationRecord;
import io.jenkins.plugins.unifiedparameters.migration.MigrationRecordStore;
import io.jenkins.plugins.unifiedparameters.migration.SourceMigrationAdapter;
import java.util.ArrayList;
import java.util.List;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
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
                if (!adapter.supports(definition)) {
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
                if (adapter.supports(definition)) {
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
