package io.jenkins.plugins.parameterfoundry.companion.file;

import static org.junit.jupiter.api.Assertions.*;

import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.model.StringParameterValue;
import io.jenkins.plugins.parameterfoundry.evaluation.EvaluationException;
import io.jenkins.plugins.parameterfoundry.migration.MigrationCoordinator;
import io.jenkins.plugins.parameterfoundry.simulation.ReplacementSimulationService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
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
class FoundryFileParameterTest {
    @TempDir
    Path tmp;

    @Test
    void configurationRoundTripAndHelp(JenkinsRule j) throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        var small = new FoundryBase64FileParameterDefinition("SMALL");
        small.setDescription("Small binary upload");
        project.addProperty(
                new ParametersDefinitionProperty(small, new FoundryStashedFileParameterDefinition("LARGE")));
        j.configRoundtrip(project);
        var definitions =
                project.getProperty(ParametersDefinitionProperty.class).getParameterDefinitions();
        assertInstanceOf(FoundryBase64FileParameterDefinition.class, definitions.get(0));
        assertEquals("Small binary upload", definitions.get(0).getDescription());
        assertInstanceOf(FoundryStashedFileParameterDefinition.class, definitions.get(1));
        assertTrue(j.createWebClient()
                .goTo("descriptor/" + small.getClass().getName() + "/help/name")
                .asNormalizedText()
                .contains("stash name"));
    }

    @Test
    void base64UiUploadAndDownload(JenkinsRule j) throws Exception {
        WorkflowJob job =
                job(j, "ui", new FoundryBase64FileParameterDefinition("FILE"), "echo env.FILE_FILENAME; echo env.FILE");
        Path file = tmp.resolve("sample.txt");
        Files.writeString(file, "hello upload");
        var client = j.createWebClient();
        client.setThrowExceptionOnFailingStatusCode(false);
        HtmlPage page = client.goTo("job/ui/build?delay=0sec");
        var form = page.getFormByName("parameters");
        ((HtmlFileInput) form.getInputByName("file")).setValue(file.toString());
        assertEquals(200, j.submit(form).getWebResponse().getStatusCode());
        j.waitUntilNoActivity();
        var run = job.getLastBuild();
        assertNotNull(run);
        j.assertBuildStatusSuccess(run);
        j.assertLogContains("sample.txt", run);
        j.assertLogContains("aGVsbG8gdXBsb2Fk", run);
        var response = client.goTo("job/ui/1/parameters/parameter/FILE/download", "application/octet-stream")
                .getWebResponse();
        assertEquals("hello upload", response.getContentAsString());
        assertEquals("attachment", response.getResponseHeaderValue("Content-Disposition"));
    }

    @Test
    void multipartStashRemoteAgentWrapperAndReusedUpload(JenkinsRule j) throws Exception {
        j.createSlave("remote", null, null);
        WorkflowJob job = job(j, "stash", new FoundryStashedFileParameterDefinition("FILE"), """
                node('remote') {
                  unstash 'FILE'
                  echo "unstashed=${readFile('FILE')}"
                  withFoundryFileParameter('FILE') {
                    echo "bound=${readFile(env.FILE)}"
                    writeFile file: 'bound-path', text: env.FILE
                  }
                  def bound = readFile('bound-path')
                  if (fileExists(bound)) { error 'temporary upload was not cleaned' }
                  try {
                    withFoundryFileParameter('FILE') {
                      writeFile file: 'failed-path', text: env.FILE
                      error 'expected wrapper failure'
                    }
                  } catch (err) {
                    if (fileExists(readFile('failed-path'))) { error 'failed wrapper was not cleaned' }
                  }
                }
                """);
        byte[] bytes = "retained upload".getBytes(StandardCharsets.UTF_8);
        var value = new FoundryStashedFileParameterValue("FILE", new ByteArrayInputStream(bytes));
        value.setFilename("C:\\fakepath\\upload.txt");
        var first = job.scheduleBuild2(0, new ParametersAction(value)).get();
        j.assertBuildStatusSuccess(first);
        j.assertLogContains("unstashed=retained upload", first);
        j.assertLogContains("bound=retained upload", first);
        assertEquals("upload.txt", value.getFilename());
        assertArrayEquals(bytes, value.open(first).readAllBytes());
        var client = j.createWebClient();
        HtmlPage rebuild = client.getPage(first, "rebuild/parameterized");
        j.submit(rebuild.getFormByName("config"));
        j.waitUntilNoActivity();
        var second = job.getLastBuild();
        assertEquals(2, second.getNumber());
        j.assertBuildStatusSuccess(second);
        j.assertLogContains("unstashed=retained upload", second);
        assertArrayEquals(bytes, value.open(second).readAllBytes());
        second.save();
        second.reload();
        first.delete();
        HtmlPage rebuildAgain = client.getPage(second, "rebuild/parameterized");
        j.submit(rebuildAgain.getFormByName("config"));
        j.waitUntilNoActivity();
        assertEquals(3, job.getLastBuild().getNumber());
        j.assertBuildStatusSuccess(job.getLastBuild());
        j.assertLogContains("unstashed=retained upload", job.getLastBuild());
        assertEquals(
                0,
                Path.of(j.jenkins.getRootDir().toString(), "parameter-foundry-uploads")
                        .toFile()
                        .list()
                        .length);
    }

    @Test
    void multipartApiAndMissingUpload(JenkinsRule j) throws Exception {
        WorkflowJob job = job(
                j,
                "rest",
                new FoundryStashedFileParameterDefinition("FILE"),
                "node { withFoundryFileParameter(name: 'FILE', allowNoFile: true) { echo 'body executed' } }");
        Path file = tmp.resolve("binary.dat");
        byte[] bytes = {0, 1, -1, 13, 10};
        Files.write(file, bytes);
        WebRequest request = new WebRequest(new URL(j.getURL(), "job/rest/buildWithParameters"), HttpMethod.POST);
        request.setEncodingType(FormEncodingType.MULTIPART);
        request.setRequestParameters(List.of(new KeyDataPair(
                "FILE", file.toFile(), "binary.dat", "application/octet-stream", StandardCharsets.UTF_8)));
        var client = j.createWebClient();
        j.jenkins.setCrumbIssuer(null);
        client.getPage(request);
        j.waitUntilNoActivity();
        j.assertBuildStatusSuccess(job.getLastBuild());
        var value = (FoundryStashedFileParameterValue)
                job.getLastBuild().getAction(ParametersAction.class).getParameter("FILE");
        assertArrayEquals(bytes, value.open(job.getLastBuild()).readAllBytes());
        request.setRequestParameters(List.of());
        client.getPage(request);
        j.waitUntilNoActivity();
        assertEquals(2, job.getLastBuild().getNumber());
        j.assertBuildStatusSuccess(job.getLastBuild());
        j.assertLogContains("body executed", job.getLastBuild());
    }

    @Test
    void base64DownstreamBuildAndMissingFileValidation(JenkinsRule j) throws Exception {
        WorkflowJob downstream = job(
                j,
                "downstream",
                new FoundryBase64FileParameterDefinition("F"),
                "node { withFoundryFileParameter('F') { echo readFile(env.F) } }");
        WorkflowJob upstream = j.createProject(WorkflowJob.class, "upstream");
        upstream.setDefinition(new CpsFlowDefinition(
                "build job: 'downstream', parameters: [foundryBase64File(name: 'F', base64: 'aGVsbG8=')]", true));
        j.buildAndAssertSuccess(upstream);
        j.assertLogContains("hello", downstream.getLastBuild());
        var missing = downstream.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.FAILURE, missing);
        j.assertLogContains("No parameter named F", missing);
        downstream.setDefinition(new CpsFlowDefinition(
                "node { withFoundryFileParameter(name: 'F', allowNoFile: true) { echo 'wrong' } }", true));
        var wrong = downstream
                .scheduleBuild2(0, new ParametersAction(new StringParameterValue("F", "/etc/passwd")))
                .get();
        j.assertBuildStatus(Result.FAILURE, wrong);
        j.assertLogContains("Unsupported parameter type", wrong);
    }

    @Test
    void limitsNamesAndBinaryDecoding(JenkinsRule j) throws Exception {
        assertThrows(hudson.model.Failure.class, () -> new FoundryStashedFileParameterDefinition("../FILE"));
        assertThrows(
                hudson.model.Failure.class,
                () -> new FoundryStashedFileParameterValue("../FILE", new ByteArrayInputStream(new byte[0])));
        assertThrows(IOException.class, () -> FileUploadService.decode("@@@"));
        assertThrows(
                IOException.class,
                () -> FileUploadService.encode(
                        new ByteArrayInputStream(new byte[(int) FileUploadService.MAX_BASE64_BYTES + 1])));
        assertThrows(
                IOException.class,
                () -> FileUploadService.copy(new ByteArrayInputStream(new byte[9]), new ByteArrayOutputStream(), 8));
        byte[] binary = {0, -1, 1, 2};
        var value = new FoundryBase64FileParameterValue("FILE", new ByteArrayInputStream(binary));
        assertArrayEquals(binary, value.open(null).readAllBytes());
        var same = new FoundryBase64FileParameterValue("FILE", new ByteArrayInputStream(binary));
        var different = new FoundryBase64FileParameterValue("FILE", new ByteArrayInputStream(new byte[] {2}));
        assertEquals(value, same);
        assertEquals(value.hashCode(), same.hashCode());
        assertNotEquals(value, different);
        assertEquals(Base64.getEncoder().encodeToString(binary), value.getValue());
        assertThrows(
                IOException.class,
                () -> new FoundryStashedFileParameterValue("FILE", new java.io.InputStream() {
                    long remaining = FileUploadService.MAX_STASHED_BYTES + 1;

                    @Override
                    public int read() {
                        return remaining-- > 0 ? 0 : -1;
                    }

                    @Override
                    public int read(byte[] b, int off, int len) {
                        if (remaining <= 0) return -1;
                        int count = (int) Math.min(remaining, len);
                        remaining -= count;
                        return count;
                    }
                }));
        assertEquals(0, new java.io.File(j.jenkins.getRootDir(), "parameter-foundry-uploads").list().length);
    }

    @Test
    void cancelledQueueRemovesUpload(JenkinsRule j) throws Exception {
        WorkflowJob job = job(j, "cancel", new FoundryStashedFileParameterDefinition("FILE"), "echo 'not run'");
        var value = new FoundryStashedFileParameterValue("FILE", new ByteArrayInputStream(new byte[] {1}));
        var future = job.scheduleBuild2(3600, new ParametersAction(value));
        assertNotNull(future);
        assertTrue(future.cancel(false));
        assertEquals(0, new java.io.File(j.jenkins.getRootDir(), "parameter-foundry-uploads").list().length);
    }

    @Test
    void fileMigrationRequiresDecisionsAndRollsBack(JenkinsRule j) throws Exception {
        var project = j.createFreeStyleProject("file-migration");
        var source = new io.jenkins.plugins.file_parameters.StashedFileParameterDefinition("UPLOAD");
        source.setDescription("Keep this description");
        project.addProperty(new ParametersDefinitionProperty(
                source, new io.jenkins.plugins.file_parameters.Base64FileParameterDefinition("SMALL")));
        var preview = MigrationCoordinator.previewBatch(project, List.of());
        assertEquals(2, preview.getApplicableCount(), preview.getDiff());
        assertEquals(0, preview.getBlockedCount());
        assertFalse(preview.getRequiredDecisions().isEmpty());
        assertThrows(EvaluationException.class, () -> MigrationCoordinator.applyBatch(project, preview, List.of()));
        var simulation = ReplacementSimulationService.simulate(project, 5);
        assertEquals(2, simulation.entries().size());
        assertTrue(simulation.entries().stream().allMatch(e -> "NEEDS_MANUAL_CHECK".equals(e.verdict())));
        assertTrue(simulation.entries().get(0).targetSnippet().contains("foundryStashedFile"));
        var result = MigrationCoordinator.applyBatch(project, preview, preview.getRequiredDecisions());
        var property = project.getProperty(ParametersDefinitionProperty.class);
        assertInstanceOf(FoundryStashedFileParameterDefinition.class, property.getParameterDefinition("UPLOAD"));
        assertEquals(
                "Keep this description",
                property.getParameterDefinition("UPLOAD").getDescription());
        assertInstanceOf(FoundryBase64FileParameterDefinition.class, property.getParameterDefinition("SMALL"));
        MigrationCoordinator.rollbackBatch(project, result.getBatchId());
        assertInstanceOf(
                io.jenkins.plugins.file_parameters.StashedFileParameterDefinition.class,
                project.getProperty(ParametersDefinitionProperty.class).getParameterDefinition("UPLOAD"));
    }

    @Test
    void cliUploadUsesStdin(JenkinsRule j) throws Exception {
        var project = job(j, "cli", new FoundryBase64FileParameterDefinition("FILE"), "echo env.FILE");
        var result = new hudson.cli.CLICommandInvoker(j, "build")
                .withStdin(new ByteArrayInputStream("cli bytes".getBytes(StandardCharsets.UTF_8)))
                .invokeWithArgs("-f", "-p", "FILE=", "cli");
        assertEquals(0, result.returnCode(), result.stderr());
        j.assertLogContains("Y2xpIGJ5dGVz", project.getLastBuild());
    }

    @Test
    void declarativeFileSymbolsAndStashes(JenkinsRule j) throws Exception {
        var project = j.createProject(WorkflowJob.class, "declared-files");
        project.addProperty(new ParametersDefinitionProperty(
                new FoundryStashedFileParameterDefinition("UPLOAD"),
                new FoundryBase64FileParameterDefinition("SMALL")));
        project.setDefinition(new CpsFlowDefinition("""
                pipeline {
                  agent any
                  parameters {
                    foundryFile(name: 'UPLOAD', deliveryMode: 'stash')
                    foundryFile(name: 'SMALL', deliveryMode: 'base64')
                  }
                  stages {
                    stage('Files') {
                      steps {
                        unstash 'UPLOAD'
                        sh 'test "$(cat UPLOAD)" = "stash"'
                        withFoundryFileParameter('SMALL') {
                          sh 'test "$(cat "$SMALL")" = "small"'
                        }
                        echo 'declared file parameters passed'
                      }
                    }
                  }
                }
                """, true));
        var stash = new FoundryStashedFileParameterValue(
                "UPLOAD", new ByteArrayInputStream("stash".getBytes(StandardCharsets.UTF_8)));
        var small = new FoundryBase64FileParameterValue(
                "SMALL", new ByteArrayInputStream("small".getBytes(StandardCharsets.UTF_8)));
        var build =
                project.scheduleBuild2(0, new ParametersAction(stash, small)).get();
        j.assertBuildStatusSuccess(build);
        j.assertLogContains("declared file parameters passed", build);
    }

    private WorkflowJob job(JenkinsRule j, String name, hudson.model.ParameterDefinition definition, String pipeline)
            throws Exception {
        WorkflowJob job = j.createProject(WorkflowJob.class, name);
        job.addProperty(new ParametersDefinitionProperty(definition));
        job.setDefinition(new CpsFlowDefinition(pipeline, true));
        return job;
    }
}
