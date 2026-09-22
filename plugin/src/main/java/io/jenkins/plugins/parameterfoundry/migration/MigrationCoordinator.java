package io.jenkins.plugins.parameterfoundry.migration;

import hudson.model.Item;
import hudson.model.Items;
import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.TopLevelItem;
import io.jenkins.plugins.parameterfoundry.Messages;
import io.jenkins.plugins.parameterfoundry.evaluation.ErrorCode;
import io.jenkins.plugins.parameterfoundry.evaluation.EvaluationException;
import io.jenkins.plugins.parameterfoundry.provider.Digests;
import io.jenkins.plugins.parameterfoundry.validation.DefinitionValidationService;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.xml.transform.stream.StreamSource;
import jenkins.model.Jenkins;
import jenkins.model.ModifiableTopLevelItemGroup;

public final class MigrationCoordinator {

    private MigrationCoordinator() {}

    public static MigrationPreviewHandle preview(Job<?, ?> job, String parameterName) {
        job.checkPermission(Item.READ);
        job.checkPermission(Item.CONFIGURE);
        ParameterDefinition source = requireDefinition(job, parameterName);
        SourceMigrationAdapter adapter = requireAdapter(source);
        MigrationPlan plan = adapter.inspect(source);
        return new MigrationPreviewHandle(
                job.getFullName(),
                parameterName,
                adapter.getSourcePluginId(),
                plan.getClassification(),
                jobDigest(job),
                definitionDigest(source),
                plan.getDecisions());
    }

    /**
     * Dry run for a Job-wide batch. Named parameters are always reported, even when they are
     * blocked; an empty list previews every parameter that an installed adapter recognizes.
     */
    public static MigrationBatchPreview previewBatch(Job<?, ?> job, List<String> parameterNames) {
        job.checkPermission(Item.READ);
        job.checkPermission(Item.CONFIGURE);
        ParametersDefinitionProperty property = job.getProperty(ParametersDefinitionProperty.class);
        if (property == null) {
            throw new EvaluationException(
                    ErrorCode.CONFIGURATION_INVALID, job.getFullName(), Messages.MigrationErrorNotParameterized());
        }
        List<String> requested = normalizeNames(parameterNames);
        List<MigrationBatchEntry> entries = new ArrayList<>();
        List<MigrationBatchEntry> blocked = new ArrayList<>();
        Set<String> declaredByPipeline = DeclaredPipelineParameters.of(job);
        for (ParameterDefinition definition : property.getParameterDefinitions()) {
            if (!requested.isEmpty() && !requested.contains(definition.getName())) {
                continue;
            }
            SourceMigrationAdapter adapter = findAdapter(definition);
            if (declaredByPipeline.contains(definition.getName())) {
                blocked.add(MigrationBatchEntry.declaredByPipeline(
                        definition, adapter, Messages.MigrationBatchBlockedPipelineDeclaresParameter()));
                continue;
            }
            if (adapter == null) {
                if (!requested.isEmpty()) {
                    blocked.add(MigrationBatchEntry.unsupported(definition, Messages.MigrationBatchBlockedNoAdapter()));
                }
                continue;
            }
            MigrationPlan plan;
            try {
                plan = adapter.inspect(definition);
            } catch (RuntimeException exception) {
                blocked.add(MigrationBatchEntry.failed(
                        definition, adapter, Messages.MigrationBatchBlockedAdapterFailure()));
                continue;
            }
            ParameterDefinition target = plan.getTarget();
            if (target == null) {
                blocked.add(MigrationBatchEntry.blocked(definition, adapter, plan));
                continue;
            }
            try {
                DefinitionValidationService.requireValid(target);
            } catch (EvaluationException exception) {
                blocked.add(MigrationBatchEntry.failed(
                        definition, adapter, Messages.MigrationBatchBlockedInvalidTarget(exception.getSafeMessage())));
                continue;
            }
            entries.add(MigrationBatchEntry.applicable(
                    definition,
                    adapter,
                    plan,
                    MigrationDiffs.definitionDiff(definition, target),
                    definitionDigest(definition)));
        }
        if (!requested.isEmpty()) {
            List<String> known = new ArrayList<>();
            for (ParameterDefinition definition : property.getParameterDefinitions()) {
                known.add(definition.getName());
            }
            for (String name : requested) {
                if (!known.contains(name)) {
                    throw new EvaluationException(
                            ErrorCode.CONFIGURATION_INVALID, name, Messages.MigrationErrorUnknownDefinition());
                }
            }
        }
        return new MigrationBatchPreview(job.getFullName(), jobDigest(job), entries, blocked);
    }

    /**
     * Applies every applicable parameter of the preview in one Job write. The Job is restored from
     * the batch backup when the write fails, so a batch never leaves a half-migrated Job behind.
     */
    public static MigrationBatchResult applyBatch(
            Job<?, ?> job, MigrationBatchPreview preview, List<String> acceptedDecisions) throws IOException {
        job.checkPermission(Item.CONFIGURE);
        verifyPreview(job, preview);
        List<MigrationBatchEntry> entries = preview.getEntries();
        if (entries.isEmpty()) {
            throw new EvaluationException(
                    ErrorCode.CONFIGURATION_INVALID, job.getFullName(), Messages.MigrationBatchNoApplicable());
        }
        List<String> accepted = acceptedDecisions == null ? List.of() : List.copyOf(acceptedDecisions);
        List<String> names = new ArrayList<>();
        List<ParameterDefinition> targets = new ArrayList<>();
        for (MigrationBatchEntry entry : entries) {
            ParameterDefinition source = requireDefinition(job, entry.parameterName());
            SourceMigrationAdapter adapter = requireAdapter(source);
            if (!adapter.getSourcePluginId().equals(entry.sourcePluginId())
                    || !definitionDigest(source).equals(entry.sourceDefinitionDigest())) {
                throw changed(job, Messages.MigrationBatchSourceChanged());
            }
            if (!accepted.containsAll(entry.decisions())) {
                throw new EvaluationException(
                        ErrorCode.CONFIGURATION_INVALID,
                        entry.parameterName(),
                        Messages.MigrationBatchDecisionsNotAccepted(entry.parameterName()));
            }
            MigrationPlan plan = adapter.inspect(source);
            ParameterDefinition target = plan.getTarget();
            if (target == null) {
                throw changed(job, Messages.MigrationBatchSourceChanged());
            }
            DefinitionValidationService.requireValid(target);
            names.add(entry.parameterName());
            targets.add(target);
        }

        String batchId = UUID.randomUUID().toString();
        String backupFile = "batch-" + batchId + ".xml";
        writeBackup(backupFile, Items.getConfigFile(job).asString());
        try {
            replaceDefinitions(job, names, targets);
        } catch (IOException | RuntimeException exception) {
            restoreBackup(job, backupFile);
            throw exception;
        }

        String targetDigest = jobDigest(job);
        String actor = Jenkins.getAuthentication2().getName();
        Instant createdAt = Instant.now();
        List<MigrationRecord> records = new ArrayList<>();
        for (MigrationBatchEntry entry : entries) {
            records.add(new MigrationRecord(
                    UUID.randomUUID().toString(),
                    job.getFullName(),
                    job.getFullName(),
                    entry.parameterName(),
                    entry.sourcePluginId(),
                    actor,
                    createdAt,
                    preview.getJobDigest(),
                    targetDigest,
                    backupFile,
                    entry.decisions(),
                    "APPLIED",
                    batchId));
        }
        MigrationRecordStore.get().addAll(records);
        return new MigrationBatchResult(batchId, job.getFullName(), records);
    }

    /** Restores the Job XML captured before the batch and marks every record of the batch. */
    public static void rollbackBatch(Job<?, ?> job, String batchId) throws IOException {
        job.checkPermission(Item.CONFIGURE);
        List<MigrationRecord> records = MigrationRecordStore.get().findBatch(batchId);
        if (records.isEmpty() || !records.get(0).getTargetJob().equals(job.getFullName())) {
            throw changed(job, Messages.MigrationBatchUnknown(batchId));
        }
        if (records.stream().anyMatch(record -> !"APPLIED".equals(record.getStatus()))) {
            throw changed(job, Messages.MigrationBatchNotApplied(batchId));
        }
        String expected = records.get(0).getTargetDigest();
        if (!jobDigest(job).equals(expected)) {
            throw changed(job, Messages.MigrationBatchRollbackRefused());
        }
        restoreBackup(job, records.get(0).getBackupFile());
        for (MigrationRecord record : records) {
            record.setStatus("ROLLED_BACK");
            MigrationRecordStore.get().update(record);
        }
    }

    public static MigrationRecord cloneAndApply(
            Job<?, ?> sourceJob, String cloneName, MigrationPreviewHandle preview, List<String> acceptedDecisions)
            throws IOException {
        sourceJob.checkPermission(Item.CONFIGURE);
        verifyPreview(sourceJob, preview);
        if (!(sourceJob instanceof TopLevelItem sourceItem)
                || !(sourceJob.getParent() instanceof ModifiableTopLevelItemGroup parent)) {
            throw new EvaluationException(
                    ErrorCode.CONFIGURATION_INVALID,
                    sourceJob.getFullName(),
                    "Job type cannot be cloned by the migration coordinator.");
        }
        TopLevelItem clone = parent.copy(sourceItem, cloneName);
        if (!(clone instanceof Job<?, ?> targetJob)) {
            throw new EvaluationException(ErrorCode.CONFIGURATION_INVALID, cloneName, "Cloned item is not a Job.");
        }
        try {
            return apply(targetJob, sourceJob.getFullName(), preview, acceptedDecisions);
        } catch (IOException | RuntimeException exception) {
            try {
                clone.delete();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            throw exception;
        }
    }

    public static MigrationRecord applyInPlace(
            Job<?, ?> job, MigrationPreviewHandle preview, List<String> acceptedDecisions) throws IOException {
        job.checkPermission(Item.CONFIGURE);
        verifyPreview(job, preview);
        return apply(job, job.getFullName(), preview, acceptedDecisions);
    }

    public static void rollback(Job<?, ?> job, String recordId) throws IOException {
        job.checkPermission(Item.CONFIGURE);
        MigrationRecord record = MigrationRecordStore.get().findRecord(recordId);
        if (!record.getTargetJob().equals(job.getFullName())) {
            throw changed(job, Messages.MigrationErrorRecordConflict());
        }
        if (record.getBatchId() != null) {
            throw changed(job, Messages.MigrationBatchMember());
        }
        if (!record.getTargetDigest().equals(jobDigest(job))) {
            throw changed(job, Messages.MigrationBatchRollbackRefused());
        }
        restoreBackup(job, record.getBackupFile());
        record.setStatus("ROLLED_BACK");
        MigrationRecordStore.get().update(record);
    }

    private static MigrationRecord apply(
            Job<?, ?> targetJob, String sourceJobName, MigrationPreviewHandle preview, List<String> acceptedDecisions)
            throws IOException {
        requireDecisions(preview, acceptedDecisions);
        ParameterDefinition source = requireDefinition(targetJob, preview.parameterName());
        SourceMigrationAdapter adapter = requireAdapter(source);
        if (!adapter.getSourcePluginId().equals(preview.sourcePluginId())
                || !definitionDigest(source).equals(preview.sourceDefinitionDigest())) {
            throw changed(targetJob, "Source definition changed after preview.");
        }
        MigrationPlan plan = adapter.inspect(source);
        ParameterDefinition target = plan.getTarget();
        if (target == null) {
            throw new EvaluationException(
                    ErrorCode.CONFIGURATION_INVALID, preview.parameterName(), Messages.MigrationErrorManualRedesign());
        }
        DefinitionValidationService.requireValid(target);

        String recordId = UUID.randomUUID().toString();
        String backupFile = recordId + ".xml";
        writeBackup(backupFile, Items.getConfigFile(targetJob).asString());
        replaceDefinition(targetJob, preview.parameterName(), target);
        String targetDigest = jobDigest(targetJob);
        MigrationRecord record = new MigrationRecord(
                recordId,
                sourceJobName,
                targetJob.getFullName(),
                preview.parameterName(),
                adapter.getSourcePluginId(),
                Jenkins.getAuthentication2().getName(),
                Instant.now(),
                preview.jobDigest(),
                targetDigest,
                backupFile,
                acceptedDecisions,
                "APPLIED");
        MigrationRecordStore.get().add(record);
        return record;
    }

    private static void replaceDefinitions(Job<?, ?> job, List<String> names, List<ParameterDefinition> targets)
            throws IOException {
        ParametersDefinitionProperty property = job.getProperty(ParametersDefinitionProperty.class);
        if (property == null) {
            throw new EvaluationException(
                    ErrorCode.CONFIGURATION_INVALID, job.getFullName(), Messages.MigrationErrorNotParameterized());
        }
        Map<String, ParameterDefinition> replacements = new LinkedHashMap<>();
        for (int index = 0; index < names.size(); index++) {
            replacements.put(names.get(index), targets.get(index));
        }
        Set<String> pending = new LinkedHashSet<>(replacements.keySet());
        List<ParameterDefinition> definitions = new ArrayList<>();
        for (ParameterDefinition definition : property.getParameterDefinitions()) {
            if (pending.remove(definition.getName())) {
                definitions.add(replacements.get(definition.getName()));
            } else {
                definitions.add(definition);
            }
        }
        if (!pending.isEmpty()) {
            throw changed(job, Messages.MigrationErrorSourceGone());
        }
        job.removeProperty(ParametersDefinitionProperty.class);
        @SuppressWarnings({"rawtypes", "unchecked"})
        Job raw = job;
        raw.addProperty(new ParametersDefinitionProperty(definitions));
        job.save();
    }

    private static void replaceDefinition(Job<?, ?> job, String name, ParameterDefinition target) throws IOException {
        ParametersDefinitionProperty property = job.getProperty(ParametersDefinitionProperty.class);
        if (property == null) {
            throw new EvaluationException(
                    ErrorCode.CONFIGURATION_INVALID, job.getFullName(), "Job is not parameterized.");
        }
        List<ParameterDefinition> definitions = new ArrayList<>();
        boolean replaced = false;
        for (ParameterDefinition definition : property.getParameterDefinitions()) {
            if (definition.getName().equals(name)) {
                definitions.add(target);
                replaced = true;
            } else {
                definitions.add(definition);
            }
        }
        if (!replaced) {
            throw changed(job, "Source definition no longer exists.");
        }
        job.removeProperty(ParametersDefinitionProperty.class);
        @SuppressWarnings({"rawtypes", "unchecked"})
        Job raw = job;
        raw.addProperty(new ParametersDefinitionProperty(definitions));
        job.save();
    }

    private static void verifyPreview(Job<?, ?> job, MigrationPreviewHandle preview) {
        if (!job.getFullName().equals(preview.jobFullName()) || !jobDigest(job).equals(preview.jobDigest())) {
            throw changed(job, Messages.MigrationErrorJobChanged());
        }
    }

    private static void verifyPreview(Job<?, ?> job, MigrationBatchPreview preview) {
        if (!job.getFullName().equals(preview.getJobFullName())
                || !jobDigest(job).equals(preview.getJobDigest())) {
            throw changed(job, Messages.MigrationBatchJobChanged());
        }
    }

    private static void requireDecisions(MigrationPreviewHandle preview, List<String> acceptedDecisions) {
        List<String> accepted = acceptedDecisions == null ? List.of() : List.copyOf(acceptedDecisions);
        if (!accepted.containsAll(preview.requiredDecisions())) {
            throw new EvaluationException(
                    ErrorCode.CONFIGURATION_INVALID, preview.parameterName(), Messages.MigrationErrorDecisions());
        }
    }

    private static ParameterDefinition requireDefinition(Job<?, ?> job, String name) {
        ParametersDefinitionProperty property = job.getProperty(ParametersDefinitionProperty.class);
        ParameterDefinition definition = property == null ? null : property.getParameterDefinition(name);
        if (definition == null) {
            throw new EvaluationException(
                    ErrorCode.CONFIGURATION_INVALID, name, Messages.MigrationErrorUnknownDefinition());
        }
        return definition;
    }

    private static SourceMigrationAdapter requireAdapter(ParameterDefinition definition) {
        SourceMigrationAdapter adapter = findAdapter(definition);
        if (adapter == null) {
            throw new EvaluationException(
                    ErrorCode.CONFIGURATION_INVALID, definition.getName(), Messages.MigrationErrorNoAdapter());
        }
        return adapter;
    }

    private static SourceMigrationAdapter findAdapter(ParameterDefinition definition) {
        return SourceMigrationAdapter.all().stream()
                .filter(SourceMigrationAdapter::isEnabled)
                .filter(adapter -> adapter.supports(definition))
                .findFirst()
                .orElse(null);
    }

    private static List<String> normalizeNames(List<String> parameterNames) {
        if (parameterNames == null) {
            return List.of();
        }
        Set<String> names = new LinkedHashSet<>();
        for (String name : parameterNames) {
            if (name != null && !name.isBlank()) {
                names.add(name.trim());
            }
        }
        return List.copyOf(names);
    }

    private static String jobDigest(Job<?, ?> job) {
        try {
            return Digests.sha256(Items.getConfigFile(job).asString());
        } catch (IOException exception) {
            throw new EvaluationException(
                    ErrorCode.CONFIGURATION_INVALID, job.getFullName(), Messages.MigrationErrorConfigUnreadable());
        }
    }

    static String definitionDigest(ParameterDefinition definition) {
        return Digests.sha256(Jenkins.XSTREAM2.toXML(definition));
    }

    private static void writeBackup(String fileName, String xml) throws IOException {
        Path root = backupRoot();
        Files.createDirectories(root);
        Path backup = root.resolve(fileName);
        Files.writeString(backup, xml, StandardCharsets.UTF_8);
        try {
            Files.setPosixFilePermissions(backup, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException ignored) {
            // Windows and non-POSIX filesystems rely on controller directory ACLs.
        }
    }

    private static Path backupRoot() {
        return Jenkins.get().getRootDir().toPath().resolve("parameter-foundry/migration-backups");
    }

    private static void restoreBackup(Job<?, ?> job, String backupFile) throws IOException {
        Path root = backupRoot();
        Path backup = root.resolve(backupFile).normalize();
        if (!backup.startsWith(root) || !Files.isRegularFile(backup)) {
            throw new EvaluationException(
                    ErrorCode.CONFIGURATION_INVALID, job.getFullName(), Messages.MigrationErrorBackupUnavailable());
        }
        job.updateByXml(new StreamSource(new StringReader(Files.readString(backup))));
    }

    private static EvaluationException changed(Job<?, ?> job, String message) {
        return new EvaluationException(ErrorCode.CONFIGURATION_CHANGED, job.getFullName(), message);
    }
}
