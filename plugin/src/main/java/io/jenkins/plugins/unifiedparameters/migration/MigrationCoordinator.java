package io.jenkins.plugins.unifiedparameters.migration;

import hudson.model.Item;
import hudson.model.Items;
import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.TopLevelItem;
import io.jenkins.plugins.unifiedparameters.evaluation.ErrorCode;
import io.jenkins.plugins.unifiedparameters.evaluation.EvaluationException;
import io.jenkins.plugins.unifiedparameters.provider.Digests;
import io.jenkins.plugins.unifiedparameters.validation.DefinitionValidationService;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
            throw changed(job, "Migration record belongs to another Job.");
        }
        if (!record.getTargetDigest().equals(jobDigest(job))) {
            throw changed(job, "Job changed after migration; rollback refused.");
        }
        Path backup = backupRoot().resolve(record.getBackupFile()).normalize();
        if (!backup.startsWith(backupRoot()) || !Files.isRegularFile(backup)) {
            throw new EvaluationException(
                    ErrorCode.CONFIGURATION_INVALID, job.getFullName(), "Migration backup is unavailable.");
        }
        job.updateByXml(new StreamSource(new StringReader(Files.readString(backup))));
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
                    ErrorCode.CONFIGURATION_INVALID,
                    preview.parameterName(),
                    "Migration requires a manual redesign and cannot be applied automatically.");
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
            throw changed(job, "Job configuration changed after preview.");
        }
    }

    private static void requireDecisions(MigrationPreviewHandle preview, List<String> acceptedDecisions) {
        List<String> accepted = acceptedDecisions == null ? List.of() : List.copyOf(acceptedDecisions);
        if (!accepted.containsAll(preview.requiredDecisions())) {
            throw new EvaluationException(
                    ErrorCode.CONFIGURATION_INVALID,
                    preview.parameterName(),
                    "Required migration decisions were not accepted.");
        }
    }

    private static ParameterDefinition requireDefinition(Job<?, ?> job, String name) {
        ParametersDefinitionProperty property = job.getProperty(ParametersDefinitionProperty.class);
        ParameterDefinition definition = property == null ? null : property.getParameterDefinition(name);
        if (definition == null) {
            throw new EvaluationException(
                    ErrorCode.CONFIGURATION_INVALID, name, "Source parameter definition was not found.");
        }
        return definition;
    }

    private static SourceMigrationAdapter requireAdapter(ParameterDefinition definition) {
        return SourceMigrationAdapter.all().stream()
                .filter(adapter -> adapter.supports(definition))
                .findFirst()
                .orElseThrow(() -> new EvaluationException(
                        ErrorCode.CONFIGURATION_INVALID,
                        definition.getName(),
                        "No installed migration adapter supports this definition."));
    }

    private static String jobDigest(Job<?, ?> job) {
        try {
            return Digests.sha256(Items.getConfigFile(job).asString());
        } catch (IOException exception) {
            throw new EvaluationException(
                    ErrorCode.CONFIGURATION_INVALID, job.getFullName(), "Job configuration could not be read.");
        }
    }

    private static String definitionDigest(ParameterDefinition definition) {
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
        return Jenkins.get().getRootDir().toPath().resolve("unified-parameters/migration-backups");
    }

    private static EvaluationException changed(Job<?, ?> job, String message) {
        return new EvaluationException(ErrorCode.CONFIGURATION_CHANGED, job.getFullName(), message);
    }
}
