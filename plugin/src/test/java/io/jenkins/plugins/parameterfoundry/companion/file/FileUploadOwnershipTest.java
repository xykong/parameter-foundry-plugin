package io.jenkins.plugins.parameterfoundry.companion.file;

import static org.junit.jupiter.api.Assertions.*;

import hudson.cli.CLICommandInvoker;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Queue;
import java.io.ByteArrayInputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.UUID;
import org.htmlunit.FormEncodingType;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.util.KeyDataPair;
import org.htmlunit.util.NameValuePair;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class FileUploadOwnershipTest {
    @TempDir
    Path tmp;

    private WorkflowJob job(JenkinsRule j, String name) throws Exception {
        var job = j.createProject(WorkflowJob.class, name);
        job.addProperty(new ParametersDefinitionProperty(new FoundryStashedFileParameterDefinition("FILE")));
        job.setDefinition(new CpsFlowDefinition("echo 'unused upload'", true));
        return job;
    }

    private long pending(JenkinsRule j) throws Exception {
        Path root = j.jenkins.getRootDir().toPath().resolve("parameter-foundry-uploads");
        if (!Files.exists(root)) return 0;
        try (var paths = Files.list(root)) {
            return paths.count();
        }
    }

    @Test
    void rejectedMultipartSubmissionCleansAlreadyParsedUpload(JenkinsRule j) throws Exception {
        var job = job(j, "http-rejected");
        Path file = Files.writeString(tmp.resolve("upload.txt"), "rejected bytes");
        var request = new WebRequest(new URL(j.getURL(), job.getUrl() + "build"), HttpMethod.POST);
        request.setEncodingType(FormEncodingType.MULTIPART);
        request.setRequestParameters(
                List.of(
                        new KeyDataPair(
                                "file0",
                                file.toFile(),
                                "upload.txt",
                                "application/octet-stream",
                                StandardCharsets.UTF_8),
                        new NameValuePair(
                                "json",
                                "{\"parameter\":[{\"name\":\"FILE\",\"file\":\"file0\"},{\"name\":\"UNKNOWN\",\"value\":\"reject\"}]}")));
        j.jenkins.setCrumbIssuer(null);
        var client = j.createWebClient().withThrowExceptionOnFailingStatusCode(false);
        assertTrue(client.getPage(request).getWebResponse().getStatusCode() >= 400);
        assertEquals(0, pending(j));
        assertEquals(0, j.jenkins.getQueue().getItems().length);
        assertNull(job.getLastBuild());
    }

    @Test
    void rejectedCliSubmissionAndQueueVetoCleanImmediately(JenkinsRule j) throws Exception {
        job(j, "cli-rejected");
        var unknown = new CLICommandInvoker(j, "build")
                .withStdin(new ByteArrayInputStream(new byte[] {1, 2}))
                .invokeWithArgs("-p", "FILE=", "-p", "UNKNOWN=reject", "cli-rejected");
        assertNotEquals(0, unknown.returnCode());
        assertEquals(0, pending(j));
        var veto = new CLICommandInvoker(j, "build")
                .withStdin(new ByteArrayInputStream(new byte[] {3, 4}))
                .invokeWithArgs("-f", "-p", "FILE=", "cli-rejected");
        assertNotEquals(0, veto.returnCode());
        assertEquals(0, pending(j));
    }

    @TestExtension("rejectedCliSubmissionAndQueueVetoCleanImmediately")
    public static final class Veto extends Queue.QueueDecisionHandler {
        @Override
        public boolean shouldSchedule(Queue.Task task, List<hudson.model.Action> actions) {
            return false;
        }
    }

    @TestExtension("admittedCliUploadSurvivesScopeAndRecoveryThenCancels")
    public static final class Block extends hudson.model.queue.QueueTaskDispatcher {
        @Override
        public hudson.model.queue.CauseOfBlockage canRun(Queue.Item item) {
            return new hudson.model.queue.CauseOfBlockage() {
                @Override
                public String getShortDescription() {
                    return "Blocked for upload ownership verification";
                }
            };
        }
    }

    @Test
    void admittedCliUploadSurvivesScopeAndRecoveryThenCancels(JenkinsRule j) throws Exception {
        var job = job(j, "queued");
        job.setQuietPeriod(3600);
        var result = new CLICommandInvoker(j, "build")
                .withStdin(new ByteArrayInputStream(new byte[] {5, 6}))
                .invokeWithArgs("-p", "FILE=", "queued");
        assertEquals(0, result.returnCode(), result.stderr());
        assertEquals(1, pending(j));
        var item = j.jenkins.getQueue().getItems()[0];
        var value = (FoundryStashedFileParameterValue)
                item.getAction(ParametersAction.class).getParameter("FILE");
        // Model a fresh JVM: no in-memory claim remains, but persisted queue ownership does.
        FileUploadOwnership.released(value.pendingUploadId());
        FileUploadOwnership.OrphanRecovery.recover(
                System.currentTimeMillis() + FileUploadOwnership.ORPHAN_AGE_MILLIS * 2);
        assertEquals(1, pending(j));
        assertTrue(j.jenkins.getQueue().cancel(job));
        assertEquals(0, pending(j));
    }

    @Test
    void completedUnusedUploadIsRetainedByBuild(JenkinsRule j) throws Exception {
        var job = job(j, "unused");
        byte[] bytes = {7, 8, 9};
        var result = new CLICommandInvoker(j, "build")
                .withStdin(new ByteArrayInputStream(bytes))
                .invokeWithArgs("-f", "-p", "FILE=", "unused");
        assertEquals(0, result.returnCode(), result.stderr());
        j.waitUntilNoActivity();
        var run = job.getLastBuild();
        j.assertBuildStatusSuccess(run);
        assertEquals(0, pending(j));
        var value = (FoundryStashedFileParameterValue)
                run.getAction(ParametersAction.class).getParameter("FILE");
        try (var in = value.open(run)) {
            assertArrayEquals(bytes, in.readAllBytes());
        }
    }

    @Test
    void recoveryRemovesOnlyOldOrphansAndProtectsActiveIngestion(JenkinsRule j) throws Exception {
        assertThrows(
                java.io.IOException.class,
                () -> new FoundryStashedFileParameterValue("FILE", new ByteArrayInputStream(new byte[] {1})));
        assertEquals(0, pending(j));
        Path root = j.jenkins.getRootDir().toPath().resolve("parameter-foundry-uploads");
        Path old = Files.createDirectories(root.resolve(UUID.randomUUID().toString()));
        Files.writeString(old.resolve("FILE"), "orphan");
        Files.setLastModifiedTime(old, FileTime.fromMillis(0));
        Path unrelated = Files.createDirectory(root.resolve("keep-unrecognized"));
        try (var scope = FileUploadOwnership.open(this)) {
            new FoundryStashedFileParameterValue("FILE", new ByteArrayInputStream(new byte[] {1}));
            FileUploadOwnership.OrphanRecovery.recover(
                    System.currentTimeMillis() + FileUploadOwnership.ORPHAN_AGE_MILLIS * 2);
            assertFalse(Files.exists(old));
            assertTrue(Files.exists(unrelated));
            assertEquals(2, pending(j));
        }
        assertEquals(1, pending(j));
    }
}
