package io.jenkins.plugins.parameterfoundry.companion.file;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Run;
import hudson.tasks.BuildWrapper;
import java.io.IOException;
import java.io.InputStream;

/** Build-retained upload delivered to a validated workspace-relative location. */
public final class FoundryWorkspaceFileParameterValue extends FoundryStashedFileParameterValue {
    private static final long serialVersionUID = 1L;
    private String workspacePath;

    FoundryWorkspaceFileParameterValue(String name, InputStream src, long limit, String path) throws IOException {
        super(name, src, limit);
        setWorkspacePath(path);
    }

    private FoundryWorkspaceFileParameterValue(String name, String id, String buildId, String path) {
        super(name, id, buildId);
        setWorkspacePath(path);
    }

    public String getWorkspacePath() {
        return workspacePath;
    }

    void setWorkspacePath(String path) {
        workspacePath = FileUploadService.checkWorkspacePath(path);
    }

    @Override
    protected boolean exportsFilenameSuffix() {
        return false;
    }

    @Override
    protected boolean usesStash() {
        return false;
    }

    @Override
    protected String safeName() {
        return "upload";
    }

    @Override
    public void buildEnvironment(Run<?, ?> build, EnvVars env) {
        super.buildEnvironment(build, env);
        if (getFilename() != null) {
            env.put(name, getFilename());
        }
    }

    void place(Run<?, ?> build, FilePath workspace) throws IOException, InterruptedException {
        buildEnvironment(build, new EnvVars());
        var property = build.getParent().getProperty(hudson.model.ParametersDefinitionProperty.class);
        var definition = property == null ? null : property.getParameterDefinition(name);
        if (!(definition instanceof FoundryFileParameterDefinition file)
                || !"workspace".equals(file.getDeliveryMode())) {
            throw new IOException("Workspace upload requires a matching workspace-mode definition.");
        }
        try (InputStream content = open(build)) {
            WorkspaceFileDelivery.place(workspace, file.getWorkspacePath(), content);
        }
    }

    @Override
    public BuildWrapper createBuildWrapper(AbstractBuild<?, ?> build) {
        return new BuildWrapper() {
            @Override
            public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener)
                    throws IOException, InterruptedException {
                FilePath workspace = build.getWorkspace();
                if (workspace == null) {
                    throw new IOException("A workspace is required for file delivery.");
                }
                place(build, workspace);
                return new Environment() {};
            }
        };
    }

    @Override
    protected FoundryStashedFileParameterValue newRebuildValue(String id, String buildId) {
        return new FoundryWorkspaceFileParameterValue(name, id, buildId, workspacePath);
    }

    @Override
    public boolean equals(Object other) {
        return super.equals(other)
                && other instanceof FoundryWorkspaceFileParameterValue value
                && java.util.Objects.equals(workspacePath, value.workspacePath);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(super.hashCode(), workspacePath);
    }
}
