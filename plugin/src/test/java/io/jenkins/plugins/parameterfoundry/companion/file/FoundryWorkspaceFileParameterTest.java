package io.jenkins.plugins.parameterfoundry.companion.file;

import static org.junit.jupiter.api.Assertions.*;

import hudson.FilePath;
import hudson.cli.CLICommandInvoker;
import hudson.model.FileParameterDefinition;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.tasks.Shell;
import io.jenkins.plugins.parameterfoundry.migration.MigrationCoordinator;
import io.jenkins.plugins.parameterfoundry.simulation.ReplacementSimulationService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.htmlunit.FormEncodingType;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.html.HtmlFileInput;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.util.KeyDataPair;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class FoundryWorkspaceFileParameterTest {
    @TempDir
    Path tmp;

    @Test
    void unifiedModesRoundTripAndHelp(JenkinsRule j) throws Exception {
        var job = j.createFreeStyleProject();
        var workspace = definition("UPLOAD", "workspace", "inputs/data.txt", 17);
        var base64 = definition("SMALL", "base64", null, 0);
        var stash = definition("STASH", "stash", null, 0);
        job.addProperty(new ParametersDefinitionProperty(workspace, base64, stash));
        j.configRoundtrip(job);
        var property = job.getProperty(ParametersDefinitionProperty.class);
        var saved = (FoundryFileParameterDefinition) property.getParameterDefinition("UPLOAD");
        assertEquals("workspace", saved.getDeliveryMode());
        assertEquals("inputs/data.txt", saved.getWorkspacePath());
        assertEquals(17, saved.getMaxUploadBytes());
        assertEquals(
                FileUploadService.MAX_BASE64_BYTES,
                ((FoundryFileParameterDefinition) property.getParameterDefinition("SMALL")).getMaxUploadBytes());
        assertEquals(
                FileUploadService.MAX_STASHED_BYTES,
                ((FoundryFileParameterDefinition) property.getParameterDefinition("STASH")).getMaxUploadBytes());
        assertEquals(
                0,
                ((FoundryFileParameterDefinition) property.getParameterDefinition("SMALL"))
                        .getConfiguredMaxUploadBytes());
        assertTrue(j.createWebClient()
                .goTo("descriptor/" + saved.getClass().getName() + "/help/workspacePath")
                .asNormalizedText()
                .contains("relative"));
        assertThrows(hudson.model.Failure.class, () -> saved.setMaxUploadBytes(-1));
        assertThrows(hudson.model.Failure.class, () -> saved.setDeliveryMode("unknown"));
    }

    @Test
    void freestyleAutomaticallyPlacesAndPreservesOriginalFilename(JenkinsRule j) throws Exception {
        var job = j.createFreeStyleProject("automatic-file");
        var definition = definition("UPLOAD", "workspace", "inputs/data.txt", 20);
        job.addProperty(new ParametersDefinitionProperty(
                definition, new hudson.model.StringParameterDefinition("UPLOAD_FILENAME", "independent")));
        job.getBuildersList()
                .add(new Shell("test \"$(cat inputs/data.txt)\" = workspace && test \"$UPLOAD\" = payload.txt"));
        var value = new FoundryWorkspaceFileParameterValue("UPLOAD", bytes("workspace"), 20, "inputs/data.txt");
        value.setFilename("C:\\fakepath\\payload.txt");
        var first = job.scheduleBuild2(
                        0,
                        new ParametersAction(
                                new hudson.model.StringParameterValue("UPLOAD_FILENAME", "independent"), value))
                .get();
        j.assertBuildStatusSuccess(first);
        assertEquals("payload.txt", first.getEnvironment(TaskListener.NULL).get("UPLOAD"));
        assertEquals("independent", first.getEnvironment(TaskListener.NULL).get("UPLOAD_FILENAME"));
        assertEquals(
                "workspace", job.getSomeWorkspace().child("inputs/data.txt").readToString());
        job.getBuildersList().replace(new Shell("test \"$(cat inputs/data.txt)\" = workspace"));
        j.assertBuildStatusSuccess(job.scheduleBuild2(0).get());
        try (var content = value.open(first)) {
            assertEquals("workspace", new String(content.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void remotePipelineWorkspaceDeliveryAndRebuild(JenkinsRule j) throws Exception {
        j.createSlave("workspace-agent", null, null);
        var job = j.createProject(WorkflowJob.class, "workspace-pipeline");
        job.addProperty(new ParametersDefinitionProperty(definition("UPLOAD", "workspace", "nested/input.txt", 20)));
        job.setDefinition(new CpsFlowDefinition("""
                node('workspace-agent') {
                  withFoundryFileParameter('UPLOAD') {
                    if (readFile('nested/input.txt') != 'workspace') { error 'bytes differ' }
                    if (env.UPLOAD != 'payload.txt') { error 'filename environment changed' }
                  }
                  if (!fileExists('nested/input.txt')) { error 'workspace upload was removed' }
                  echo 'WORKSPACE_DELIVERY_PASSED'
                }
                """, true));
        var value = new FoundryWorkspaceFileParameterValue("UPLOAD", bytes("workspace"), 20, "nested/input.txt");
        value.setFilename("payload.txt");
        var first = job.scheduleBuild2(0, new ParametersAction(value)).get();
        j.assertBuildStatusSuccess(first);
        var client = j.createWebClient();
        HtmlPage page = client.getPage(first, "rebuild/parameterized");
        j.submit(page.getFormByName("config"));
        j.waitUntilNoActivity();
        assertEquals(2, job.getLastBuild().getNumber());
        j.assertBuildStatusSuccess(job.getLastBuild());
        j.assertLogContains("WORKSPACE_DELIVERY_PASSED", job.getLastBuild());
    }

    @Test
    void coreMigrationPreservesRelativeIdentityAndRollsBack(JenkinsRule j) throws Exception {
        var job = j.createFreeStyleProject("core-file-migration");
        job.getBuildersList().add(new Shell("test \"$(cat inputs/data.zip)\" = cli"));
        var original = new FileParameterDefinition("inputs/data.zip", "Core upload");
        job.addProperty(new ParametersDefinitionProperty(original));
        var adapter = new io.jenkins.plugins.parameterfoundry.migration.additional.CoreFileMigrationAdapter();
        String summary = io.jenkins.plugins.parameterfoundry.migration.MigrationPreview.from(
                        adapter, original, adapter.inspect(original))
                .getTargetSummary();
        assertTrue(
                summary.contains("workspace") && summary.contains("inputs/data.zip") && summary.contains("104857600"),
                summary);
        var preview = MigrationCoordinator.previewBatch(job, List.of());
        assertEquals(1, preview.getApplicableCount(), preview.getDiff());
        assertEquals(0, preview.getBlockedCount());
        var simulation = ReplacementSimulationService.simulate(job, 5).entries().get(0);
        assertEquals("NEEDS_MANUAL_CHECK", simulation.verdict());
        assertTrue(simulation.targetSnippet().contains("foundryFile"));
        assertTrue(simulation.targetSnippet().contains("workspacePath: 'inputs/data.zip'"));
        var result = MigrationCoordinator.applyBatch(job, preview, preview.getRequiredDecisions());
        var target = (FoundryFileParameterDefinition)
                job.getProperty(ParametersDefinitionProperty.class).getParameterDefinition("inputs/data.zip");
        assertEquals("inputs/data.zip", target.getWorkspacePath());
        assertEquals("Core upload", target.getDescription());
        assertEquals("workspace", target.getDeliveryMode());
        var cli = new CLICommandInvoker(j, "build")
                .withStdin(bytes("cli"))
                .invokeWithArgs("-f", "-p", "inputs/data.zip=", job.getFullName());
        assertEquals(0, cli.returnCode(), cli.stderr());
        assertEquals(
                "stdin", job.getLastBuild().getEnvironment(TaskListener.NULL).get("inputs/data.zip"));
        MigrationCoordinator.rollbackBatch(job, result.getBatchId());
        assertInstanceOf(
                FileParameterDefinition.class,
                job.getProperty(ParametersDefinitionProperty.class).getParameterDefinition("inputs/data.zip"));
        assertThrows(
                hudson.model.Failure.class,
                () -> new io.jenkins.plugins.parameterfoundry.migration.additional.CoreFileMigrationAdapter()
                        .inspect(new FileParameterDefinition("../outside")));
    }

    @Test
    void workspacePathsRejectTraversalDevicesAndSymlinks(JenkinsRule j) throws Exception {
        for (String path : List.of(
                "../outside",
                "/absolute",
                "a/../../b",
                "C:\\outside",
                "a//b",
                "a/./b",
                "a/NUL.txt",
                "a/file:stream",
                "a/.. ")) {
            assertThrows(hudson.model.Failure.class, () -> FileUploadService.checkWorkspacePath(path), path);
        }
        Path root = Files.createDirectory(tmp.resolve("workspace"));
        Path outside = Files.createDirectory(tmp.resolve("outside"));
        Files.writeString(outside.resolve("unchanged"), "original");
        Files.createSymbolicLink(root.resolve("linked"), outside);
        var workspace = new FilePath(root.toFile());
        assertThrows(
                IOException.class,
                () -> WorkspaceFileDelivery.place(workspace, "linked/unchanged", bytes("replacement")));
        Files.createSymbolicLink(root.resolve("file-link"), outside.resolve("unchanged"));
        assertThrows(
                IOException.class, () -> WorkspaceFileDelivery.place(workspace, "file-link", bytes("replacement")));
        assertEquals("original", Files.readString(outside.resolve("unchanged")));
        WorkspaceFileDelivery.place(workspace, "safe/input.txt", bytes("first"));
        WorkspaceFileDelivery.place(workspace, "safe/input.txt", bytes("second"));
        assertEquals("second", Files.readString(root.resolve("safe/input.txt")));
        try (var files = Files.list(root)) {
            assertFalse(files.anyMatch(p -> p.getFileName().toString().startsWith("foundry-upload-")));
        }
    }

    @Test
    void configuredLimitsApplyToRestCliDownstreamAndRebuild(JenkinsRule j) throws Exception {
        var definition = definition("FILE", "base64", null, 4);
        var job = j.createProject(WorkflowJob.class, "bounded-upload");
        job.addProperty(new ParametersDefinitionProperty(definition));
        job.setDefinition(
                new CpsFlowDefinition("node { withFoundryFileParameter('FILE') { echo 'BODY_PASSED' } }", true));
        Path file = tmp.resolve("five.txt");
        Files.writeString(file, "12345");
        var request = new WebRequest(new URL(j.getURL(), "job/bounded-upload/buildWithParameters"), HttpMethod.POST);
        request.setEncodingType(FormEncodingType.MULTIPART);
        request.setRequestParameters(
                List.of(new KeyDataPair("FILE", file.toFile(), "five.txt", "text/plain", StandardCharsets.UTF_8)));
        j.jenkins.setCrumbIssuer(null);
        var client = j.createWebClient().withThrowExceptionOnFailingStatusCode(false);
        assertEquals(400, client.getPage(request).getWebResponse().getStatusCode());
        assertEquals(0, job.getBuilds().size());
        assertEquals(0, j.jenkins.getQueue().getItems().length);
        definition.setMaxUploadBytes(6);
        job.save();
        var cli = new CLICommandInvoker(j, "build")
                .withStdin(bytes("12345"))
                .invokeWithArgs("-f", "-p", "FILE=", job.getFullName());
        assertEquals(0, cli.returnCode(), cli.stderr());
        var first = job.getLastBuild();
        j.assertBuildStatusSuccess(first);
        definition.setMaxUploadBytes(4);
        job.save();
        HtmlPage rebuild = client.getPage(first, "rebuild/parameterized");
        assertEquals(
                400, j.submit(rebuild.getFormByName("config")).getWebResponse().getStatusCode());
        assertEquals(1, job.getLastBuild().getNumber());
        var injected = new FoundryBase64FileParameterValue("FILE");
        injected.setBase64("MTIzNDU=");
        var rejected = job.scheduleBuild2(0, new ParametersAction(injected)).get();
        j.assertBuildStatus(Result.FAILURE, rejected);
        j.assertLogNotContains("BODY_PASSED", rejected);
        byte[] large = new byte[(int) FileUploadService.MAX_BASE64_BYTES + 1];
        assertFalse(FileUploadService.encode(new ByteArrayInputStream(large), large.length)
                .isEmpty());
    }

    @Test
    void unifiedBase64SupportsPipelineInput(JenkinsRule j) throws Exception {
        var job = j.createProject(WorkflowJob.class, "input-file");
        job.setDefinition(new CpsFlowDefinition("""
                def value = input id: 'upload', message: 'Upload small file', parameters: [foundryFile(name: 'FILE', deliveryMode: 'base64', maxUploadBytes: 4)]
                if (value != 'dGVzdA==') { error 'input bytes differ' }
                echo 'INPUT_FILE_PASSED'
                """, true));
        var run = job.scheduleBuild2(0).waitForStart();
        j.waitForMessage("Input requested", run);
        Path file = tmp.resolve("input.txt");
        Files.writeString(file, "test");
        HtmlPage page = j.createWebClient().getPage(run, "input/");
        HtmlFileInput upload = page.getFirstByXPath("//input[@type='file']");
        assertNotNull(upload);
        upload.setValue(file.toString());
        j.submit(upload.getEnclosingForm());
        j.waitForCompletion(run);
        j.assertBuildStatusSuccess(run);
        j.assertLogContains("INPUT_FILE_PASSED", run);
    }

    @Test
    void nonBase64InputRejectsWithoutStaging(JenkinsRule j) throws Exception {
        for (String mode : List.of("workspace", "stash")) {
            var job = j.createProject(WorkflowJob.class, "input-" + mode);
            job.setDefinition(new CpsFlowDefinition(
                    "input id: 'upload', message: 'Upload', parameters: [foundryFile(name: 'FILE', deliveryMode: '"
                            + mode + "')]",
                    true));
            var run = job.scheduleBuild2(0).waitForStart();
            j.waitForMessage("Input requested", run);
            var client = j.createWebClient().withThrowExceptionOnFailingStatusCode(false);
            HtmlPage page = client.getPage(run, "input/");
            Path file = tmp.resolve(mode + ".txt");
            Files.writeString(file, "test");
            HtmlFileInput upload = page.getFirstByXPath("//input[@type='file']");
            upload.setValue(file.toString());
            assertEquals(
                    400, j.submit(upload.getEnclosingForm()).getWebResponse().getStatusCode());
            var staging = new java.io.File(j.jenkins.getRootDir(), "parameter-foundry-uploads");
            assertTrue(!staging.exists() || staging.list().length == 0);
            run.getExecution().interrupt(Result.ABORTED);
            j.waitForCompletion(run);
        }
    }

    @Test
    void deliveryModeChangeRequiresReplacement(JenkinsRule j) throws Exception {
        var job = j.createProject(WorkflowJob.class, "mode-change");
        var definition = definition("FILE", "workspace", "data.txt", 20);
        job.addProperty(new ParametersDefinitionProperty(definition));
        job.setDefinition(
                new CpsFlowDefinition("node { withFoundryFileParameter('FILE') { echo 'delivered' } }", true));
        var value = new FoundryWorkspaceFileParameterValue("FILE", bytes("original"), 20, "data.txt");
        value.setFilename("payload.txt");
        var first = job.scheduleBuild2(0, new ParametersAction(value)).get();
        j.assertBuildStatusSuccess(first);
        definition.setDeliveryMode("stash");
        job.save();
        var client = j.createWebClient().withThrowExceptionOnFailingStatusCode(false);
        HtmlPage page = client.getPage(first, "rebuild/parameterized");
        assertEquals(
                400, j.submit(page.getFormByName("config")).getWebResponse().getStatusCode());
        assertEquals(1, job.getLastBuild().getNumber());
    }

    @Test
    void binarySimulationDoesNotEvaluateSourceFileValues(JenkinsRule j) throws Exception {
        var job = j.createFreeStyleProject("binary-simulation");
        job.addProperty(new ParametersDefinitionProperty(new FileParameterDefinition("FILE")));
        var run = j.buildAndAssertSuccess(job);
        Path file = tmp.resolve("private-bytes");
        Files.writeString(file, "not an option value");
        var opaque = new hudson.model.FileParameterValue("FILE", file.toFile(), "payload.txt") {
            @Override
            public Object getValue() {
                throw new AssertionError("Binary source values must not be evaluated");
            }

            @Override
            public void buildEnvironment(hudson.model.Run<?, ?> build, hudson.EnvVars env) {
                throw new AssertionError("Binary source environment must not be evaluated");
            }
        };
        run.replaceAction(new ParametersAction(opaque));
        assertSame(opaque, run.getAction(ParametersAction.class).getParameter("FILE"));
        var result = ReplacementSimulationService.simulate(job, 5).entries().get(0);
        assertEquals("NEEDS_MANUAL_CHECK", result.verdict());
        assertEquals(0, result.observationCount());
    }

    @Test
    void equivalentDuplicatePropertiesNormalizeAndConflictsRefuse(JenkinsRule j) throws Exception {
        var source = j.createFreeStyleProject("duplicate-core");
        source.addProperty(new ParametersDefinitionProperty(new FileParameterDefinition("FILE")));
        source.addProperty(new ParametersDefinitionProperty(new FileParameterDefinition("FILE")));
        assertEquals(
                2,
                source.getAllProperties().stream()
                        .filter(ParametersDefinitionProperty.class::isInstance)
                        .count());
        var preview = MigrationCoordinator.preview(source, "FILE");
        var record =
                MigrationCoordinator.cloneAndApply(source, "duplicate-clone", preview, preview.requiredDecisions());
        var clone = (hudson.model.FreeStyleProject) j.jenkins.getItem("duplicate-clone");
        assertEquals(
                1,
                clone.getAllProperties().stream()
                        .filter(ParametersDefinitionProperty.class::isInstance)
                        .count());
        assertInstanceOf(
                FoundryFileParameterDefinition.class,
                clone.getProperty(ParametersDefinitionProperty.class).getParameterDefinition("FILE"));
        clone.doReload();
        assertEquals(
                1,
                clone.getAllProperties().stream()
                        .filter(ParametersDefinitionProperty.class::isInstance)
                        .count());
        MigrationCoordinator.rollback(clone, record.getId());
        assertEquals(
                2,
                clone.getAllProperties().stream()
                        .filter(ParametersDefinitionProperty.class::isInstance)
                        .count());
        var conflict = j.createFreeStyleProject("conflicting-core");
        conflict.addProperty(new ParametersDefinitionProperty(new FileParameterDefinition("FILE")));
        conflict.addProperty(new ParametersDefinitionProperty(new hudson.model.StringParameterDefinition("HIDDEN")));
        String before = conflict.getConfigFile().asString();
        assertThrows(
                io.jenkins.plugins.parameterfoundry.evaluation.EvaluationException.class,
                () -> MigrationCoordinator.previewBatch(conflict, List.of()));
        assertEquals(before, conflict.getConfigFile().asString());
    }

    @Test
    void legacyRebuildRequiresFreshUploadAfterMigration(JenkinsRule j) throws Exception {
        var job = j.createFreeStyleProject("legacy-rebuild");
        job.addProperty(new ParametersDefinitionProperty(new FileParameterDefinition("FILE")));
        job.getBuildersList().add(new Shell("test -f FILE"));
        var cli = new CLICommandInvoker(j, "build")
                .withStdin(bytes("legacy"))
                .invokeWithArgs("-f", "-p", "FILE=", job.getFullName());
        assertEquals(0, cli.returnCode(), cli.stderr());
        var original = job.getLastBuild();
        var preview = MigrationCoordinator.previewBatch(job, List.of());
        MigrationCoordinator.applyBatch(job, preview, preview.getRequiredDecisions());
        var client = j.createWebClient().withThrowExceptionOnFailingStatusCode(false);
        HtmlPage page = client.getPage(original, "rebuild/parameterized");
        assertEquals(
                400, j.submit(page.getFormByName("config")).getWebResponse().getStatusCode());
        assertEquals(1, job.getLastBuild().getNumber());
        Path replacement = tmp.resolve("replacement.txt");
        Files.writeString(replacement, "replacement");
        var request = new WebRequest(new URL(j.getURL(), "job/legacy-rebuild/buildWithParameters"), HttpMethod.POST);
        request.setEncodingType(FormEncodingType.MULTIPART);
        request.setRequestParameters(List.of(new KeyDataPair(
                "FILE", replacement.toFile(), "replacement.txt", "text/plain", StandardCharsets.UTF_8)));
        j.jenkins.setCrumbIssuer(null);
        client.getPage(request);
        j.waitUntilNoActivity();
        j.assertBuildStatusSuccess(job.getLastBuild());
        assertEquals(2, job.getLastBuild().getNumber());
        assertEquals("replacement", job.getSomeWorkspace().child("FILE").readToString());
    }

    private static FoundryFileParameterDefinition definition(String name, String mode, String path, long limit) {
        var definition = new FoundryFileParameterDefinition(name);
        definition.setDeliveryMode(mode);
        if (path != null) definition.setWorkspacePath(path);
        definition.setMaxUploadBytes(limit);
        return definition;
    }

    private static ByteArrayInputStream bytes(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }
}
