package io.jenkins.plugins.unifiedparameters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.cli.CLICommandInvoker;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import io.jenkins.plugins.unifiedparameters.definition.UnifiedChoiceParameterDefinition;
import io.jenkins.plugins.unifiedparameters.definition.UnifiedChoiceParameterValue;
import io.jenkins.plugins.unifiedparameters.definition.UnifiedMultiChoiceParameterDefinition;
import io.jenkins.plugins.unifiedparameters.definition.UnifiedMultiChoiceParameterValue;
import io.jenkins.plugins.unifiedparameters.evaluation.ErrorCode;
import io.jenkins.plugins.unifiedparameters.model.Option;
import io.jenkins.plugins.unifiedparameters.provider.ProviderLoadContext;
import io.jenkins.plugins.unifiedparameters.provider.ProviderOperation;
import io.jenkins.plugins.unifiedparameters.provider.ProviderResolutionService;
import io.jenkins.plugins.unifiedparameters.provider.SharedProviderDefinition;
import io.jenkins.plugins.unifiedparameters.provider.UnifiedParametersGlobalConfiguration;
import io.jenkins.plugins.unifiedparameters.snapshot.ProviderSnapshotStore;
import io.jenkins.plugins.unifiedparameters.snapshot.ProviderSubmission;
import io.jenkins.plugins.unifiedparameters.snapshot.SnapshotAudience;
import java.net.URL;
import java.util.List;
import java.util.Map;
import net.sf.json.JSONObject;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ReactiveSnapshotIntegrationTest {

    @Test
    void standardFormContextUsesParameterScopedSnapshotFields() {
        UnifiedChoiceParameterDefinition definition = new UnifiedChoiceParameterDefinition("ARTIFACT");

        assertEquals("__up_snapshot_ARTIFACT", ProviderSubmission.snapshotRequestField(definition));
        assertEquals("__up_dependencies_ARTIFACT", ProviderSubmission.dependenciesRequestField(definition));
    }

    @Test
    void typedResolveAndBuildShareDependencyBoundSnapshot(JenkinsRule jenkins) throws Exception {
        DependencyProvider provider = new DependencyProvider("artifacts");
        UnifiedParametersGlobalConfiguration.get().setProviders(List.of(provider));
        ProviderSnapshotStore.get().clear();
        FreeStyleProject project = reactiveJob(jenkins, "reactive-snapshot");

        JSONObject dependencies = new JSONObject();
        dependencies.put("ENVIRONMENT", "staging");
        JSONObject resolveBody = new JSONObject();
        resolveBody.put("parameter", "ARTIFACT");
        resolveBody.put("dependencies", dependencies);
        resolveBody.put("version", 7);
        JenkinsRule.JSONWebResponse resolved =
                postJson(jenkins, project.getUrl() + "unified-parameters/api/v1/resolve", resolveBody);

        assertEquals(200, resolved.getStatusCode());
        JSONObject resolution = resolved.getJSONObject();
        assertEquals(7, resolution.getInt("version"));
        assertEquals(
                "staging-artifact",
                resolution.getJSONArray("options").getJSONObject(0).getString("value"));
        String snapshotId = resolution.getString("snapshotId");
        assertTrue(snapshotId.startsWith("up-snapshot-"));

        JSONObject values = new JSONObject();
        values.put("ENVIRONMENT", "staging");
        values.put("ARTIFACT", "staging-artifact");
        JSONObject snapshots = new JSONObject();
        snapshots.put("ARTIFACT", snapshotId);
        JSONObject buildBody = new JSONObject();
        buildBody.put("values", values);
        buildBody.put("providerSnapshots", snapshots);
        JenkinsRule.JSONWebResponse built =
                postJson(jenkins, project.getUrl() + "unified-parameters/api/v1/build", buildBody);
        assertEquals(201, built.getStatusCode(), built.getJSONObject().toString());
        jenkins.waitUntilNoActivity();

        UnifiedChoiceParameterValue value = (UnifiedChoiceParameterValue)
                project.getLastBuild().getAction(ParametersAction.class).getParameter("ARTIFACT");
        assertEquals("staging-artifact", value.getValue());
        assertNotNull(value.getProviderSnapshot());
        assertEquals(snapshotId, value.getProviderSnapshot().getSnapshotId());
        assertEquals("artifacts", value.getProviderSnapshot().getProviderId());
        assertTrue(value.getProviderSnapshot().getDependencyDigest().startsWith("sha256:"));
    }

    @Test
    void snapshotCannotCrossDependencyOrDiagnosticBoundary(JenkinsRule jenkins) throws Exception {
        DependencyProvider provider = new DependencyProvider("artifacts");
        UnifiedParametersGlobalConfiguration.get().setProviders(List.of(provider));
        ProviderSnapshotStore.get().clear();
        FreeStyleProject project = reactiveJob(jenkins, "reactive-binding");

        JSONObject dependencies = new JSONObject();
        dependencies.put("ENVIRONMENT", "staging");
        JSONObject resolveBody = new JSONObject();
        resolveBody.put("parameter", "ARTIFACT");
        resolveBody.put("dependencies", dependencies);
        JenkinsRule.JSONWebResponse resolved =
                postJson(jenkins, project.getUrl() + "unified-parameters/api/v1/resolve", resolveBody);
        String snapshotId = resolved.getJSONObject().getString("snapshotId");

        JSONObject values = new JSONObject();
        values.put("ENVIRONMENT", "production");
        values.put("ARTIFACT", "production-artifact");
        JSONObject snapshots = new JSONObject();
        snapshots.put("ARTIFACT", snapshotId);
        JSONObject body = new JSONObject();
        body.put("values", values);
        body.put("providerSnapshots", snapshots);
        JenkinsRule.JSONWebResponse response =
                postJson(jenkins, project.getUrl() + "unified-parameters/api/v1/validate", body);

        assertEquals(409, response.getStatusCode());
        assertEquals(
                "UNIFIED_PARAMETER_" + ErrorCode.SNAPSHOT_INVALID.name(),
                response.getJSONObject().getJSONArray("errors").getJSONObject(0).getString("code"));

        UnifiedChoiceParameterDefinition artifact = (UnifiedChoiceParameterDefinition)
                project.getProperty(ParametersDefinitionProperty.class).getParameterDefinition("ARTIFACT");
        Map<String, String> canonicalDependencies = Map.of("ENVIRONMENT", "staging");
        var providerResolution =
                ProviderResolutionService.resolve(project, artifact, ProviderOperation.HEALTH, canonicalDependencies);
        String diagnosticId = ProviderSnapshotStore.get()
                .create(project, artifact, canonicalDependencies, providerResolution, SnapshotAudience.DIAGNOSTIC)
                .snapshotId();
        assertThrows(
                io.jenkins.plugins.unifiedparameters.evaluation.EvaluationException.class,
                () -> ProviderSnapshotStore.get()
                        .resolveForBuild(
                                project, artifact, canonicalDependencies, diagnosticId, ProviderOperation.BUILD));

        FreeStyleProject other = reactiveJob(jenkins, "reactive-binding-other");
        UnifiedChoiceParameterDefinition otherArtifact = (UnifiedChoiceParameterDefinition)
                other.getProperty(ParametersDefinitionProperty.class).getParameterDefinition("ARTIFACT");
        assertThrows(
                io.jenkins.plugins.unifiedparameters.evaluation.EvaluationException.class,
                () -> ProviderSnapshotStore.get()
                        .resolveForBuild(
                                other, otherArtifact, canonicalDependencies, snapshotId, ProviderOperation.BUILD));
    }

    @Test
    void cliResolvesReactiveDependenciesFromBuildParameters(JenkinsRule jenkins) throws Exception {
        UnifiedParametersGlobalConfiguration.get().setProviders(List.of(new DependencyProvider("artifacts")));
        ProviderSnapshotStore.get().clear();
        FreeStyleProject project = reactiveJob(jenkins, "reactive-cli");

        CLICommandInvoker.Result result = new CLICommandInvoker(jenkins, "build")
                .invokeWithArgs(
                        project.getFullName(),
                        "-p",
                        "ENVIRONMENT=production",
                        "-p",
                        "ARTIFACT=production-artifact",
                        "-s");

        assertEquals(0, result.returnCode(), result.stderr());
        UnifiedChoiceParameterValue value = (UnifiedChoiceParameterValue)
                project.getLastBuild().getAction(ParametersAction.class).getParameter("ARTIFACT");
        assertEquals("production-artifact", value.getValue());
        assertNotNull(value.getProviderSnapshot());
    }

    @Test
    void cliOmittedReactiveParameterUsesDependencyAwareDefault(JenkinsRule jenkins) throws Exception {
        UnifiedParametersGlobalConfiguration.get().setProviders(List.of(new DependencyProvider("artifacts")));
        ProviderSnapshotStore.get().clear();
        FreeStyleProject project = reactiveJob(jenkins, "reactive-cli-omitted");

        CLICommandInvoker.Result result = new CLICommandInvoker(jenkins, "build")
                .invokeWithArgs(project.getFullName(), "-p", "ENVIRONMENT=production", "-s");

        assertEquals(0, result.returnCode(), result.stderr());
        UnifiedChoiceParameterValue environment = (UnifiedChoiceParameterValue)
                project.getLastBuild().getAction(ParametersAction.class).getParameter("ENVIRONMENT");
        UnifiedChoiceParameterValue artifact = (UnifiedChoiceParameterValue)
                project.getLastBuild().getAction(ParametersAction.class).getParameter("ARTIFACT");
        assertEquals("production", environment.getValue());
        assertEquals("production-artifact", artifact.getValue());
        assertNotNull(artifact.getProviderSnapshot());
    }

    @Test
    void cliOmittedReactiveParameterUsesDependencyDefault(JenkinsRule jenkins) throws Exception {
        UnifiedParametersGlobalConfiguration.get().setProviders(List.of(new DependencyProvider("artifacts")));
        ProviderSnapshotStore.get().clear();
        FreeStyleProject project = reactiveJob(jenkins, "reactive-cli-defaults");

        CLICommandInvoker.Result result =
                new CLICommandInvoker(jenkins, "build").invokeWithArgs(project.getFullName(), "-s");

        assertEquals(0, result.returnCode(), result.stderr());
        UnifiedChoiceParameterValue environment = (UnifiedChoiceParameterValue)
                project.getLastBuild().getAction(ParametersAction.class).getParameter("ENVIRONMENT");
        UnifiedChoiceParameterValue artifact = (UnifiedChoiceParameterValue)
                project.getLastBuild().getAction(ParametersAction.class).getParameter("ARTIFACT");
        assertEquals("staging", environment.getValue());
        assertEquals("staging-artifact", artifact.getValue());
        assertNotNull(artifact.getProviderSnapshot());
    }

    @Test
    void cliOmittedReactiveMultiChoiceUsesDependencyAwareDefault(JenkinsRule jenkins) throws Exception {
        UnifiedParametersGlobalConfiguration.get()
                .setProviders(List.of(new MultiDependencyProvider("multi-artifacts")));
        ProviderSnapshotStore.get().clear();
        FreeStyleProject project = reactiveMultiJob(jenkins, "reactive-cli-multi-omitted");

        CLICommandInvoker.Result result = new CLICommandInvoker(jenkins, "build")
                .invokeWithArgs(project.getFullName(), "-p", "ENVIRONMENT=production", "-s");

        assertEquals(0, result.returnCode(), result.stderr());
        UnifiedMultiChoiceParameterValue targets = (UnifiedMultiChoiceParameterValue)
                project.getLastBuild().getAction(ParametersAction.class).getParameter("TARGETS");
        assertEquals(List.of("production-android"), targets.getValue());
        assertNotNull(targets.getProviderSnapshot());
    }

    @Test
    void cliOmittedReactiveRequiredValueFailsBeforeQueueing(JenkinsRule jenkins) throws Exception {
        UnifiedParametersGlobalConfiguration.get().setProviders(List.of(new DependencyProvider("artifacts")));
        ProviderSnapshotStore.get().clear();
        FreeStyleProject project = reactiveJob(jenkins, "reactive-cli-required");
        UnifiedChoiceParameterDefinition artifact = (UnifiedChoiceParameterDefinition)
                project.getProperty(ParametersDefinitionProperty.class).getParameterDefinition("ARTIFACT");
        artifact.setDefaultMode("none");
        artifact.setRequired(true);
        project.save();

        CLICommandInvoker.Result result = new CLICommandInvoker(jenkins, "build")
                .invokeWithArgs(project.getFullName(), "-p", "ENVIRONMENT=production", "-s");

        assertTrue(result.returnCode() != 0);
        assertTrue(result.stderr().contains("No default value for the parameter 'ARTIFACT'"));
        assertNull(project.getLastBuild());
        assertEquals(0, jenkins.jenkins.getQueue().getItems().length);
    }

    @Test
    void jobValidationRejectsReactiveCycles(JenkinsRule jenkins) throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject("reactive-cycle");
        UnifiedChoiceParameterDefinition first = new UnifiedChoiceParameterDefinition("FIRST");
        first.setProviderId("unused");
        first.setDependsOn(List.of("SECOND"));
        UnifiedChoiceParameterDefinition second = new UnifiedChoiceParameterDefinition("SECOND");
        second.setProviderId("unused");
        second.setDependsOn(List.of("FIRST"));
        ParametersDefinitionProperty property = new ParametersDefinitionProperty(first, second);

        var validation =
                io.jenkins.plugins.unifiedparameters.validation.DefinitionValidationService.validateJobDefinitions(
                        property.getParameterDefinitions());

        assertTrue(validation.getErrors().stream().anyMatch(error -> error.contains("Reactive dependency cycle")));
    }

    private static FreeStyleProject reactiveJob(JenkinsRule jenkins, String name) throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject(name);
        UnifiedChoiceParameterDefinition environment = new UnifiedChoiceParameterDefinition("ENVIRONMENT");
        environment.setChoices(List.of(new Option("staging"), new Option("production")));
        environment.setDefaultMode("explicit");
        environment.setDefaultValue("staging");

        UnifiedChoiceParameterDefinition artifact = new UnifiedChoiceParameterDefinition("ARTIFACT");
        artifact.setProviderId("artifacts");
        artifact.setDependsOn(List.of("ENVIRONMENT"));
        artifact.setDefaultMode("first");
        project.addProperty(new ParametersDefinitionProperty(environment, artifact));
        return project;
    }

    private static FreeStyleProject reactiveMultiJob(JenkinsRule jenkins, String name) throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject(name);
        UnifiedChoiceParameterDefinition environment = new UnifiedChoiceParameterDefinition("ENVIRONMENT");
        environment.setChoices(List.of(new Option("staging"), new Option("production")));
        environment.setDefaultMode("explicit");
        environment.setDefaultValue("staging");

        UnifiedMultiChoiceParameterDefinition targets = new UnifiedMultiChoiceParameterDefinition("TARGETS");
        targets.setProviderId("multi-artifacts");
        targets.setDependsOn(List.of("ENVIRONMENT"));
        targets.setDefaultMode("first");
        project.addProperty(new ParametersDefinitionProperty(environment, targets));
        return project;
    }

    private static JenkinsRule.JSONWebResponse postJson(JenkinsRule jenkins, String path, JSONObject body)
            throws Exception {
        JenkinsRule.WebClient client = jenkins.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false)
                .withRedirectEnabled(false);
        WebRequest request = new WebRequest(new URL(jenkins.getURL(), path), HttpMethod.POST);
        request.setAdditionalHeader("Content-Type", "application/json");
        request.setRequestBody(body.toString());
        hudson.security.csrf.CrumbIssuer issuer = jenkins.jenkins.getCrumbIssuer();
        if (issuer != null) {
            request.setAdditionalHeader(issuer.getCrumbRequestField(), issuer.getCrumb());
        }
        return new JenkinsRule.JSONWebResponse(client.loadWebResponse(request));
    }

    private static final class DependencyProvider extends SharedProviderDefinition {
        DependencyProvider(String id) {
            super(id);
        }

        @Override
        public List<Option> load(ProviderLoadContext context) {
            String environment = context.request().dependencyValues().get("ENVIRONMENT");
            return List.of(new Option(environment + "-artifact"));
        }

        @Override
        protected String providerFingerprint() {
            return "dependency-provider";
        }
    }

    private static final class MultiDependencyProvider extends SharedProviderDefinition {
        MultiDependencyProvider(String id) {
            super(id);
        }

        @Override
        public List<Option> load(ProviderLoadContext context) {
            String environment = context.request().dependencyValues().get("ENVIRONMENT");
            return List.of(new Option(environment + "-android"), new Option(environment + "-ios"));
        }

        @Override
        protected String providerFingerprint() {
            return "multi-dependency-provider";
        }
    }
}
