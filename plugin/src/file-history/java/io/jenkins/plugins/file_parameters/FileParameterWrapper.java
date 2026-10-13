package io.jenkins.plugins.file_parameters;
/** Historical metadata descriptor for completed graph labels. Only passive model construction is allowed; both execution entry points reject. */
public final class FileParameterWrapper extends jenkins.tasks.SimpleBuildWrapper {
    public final String name;
    private boolean allowNoFile;
    @org.kohsuke.stapler.DataBoundConstructor
    public FileParameterWrapper(String name) {
        this.name=name;
    }
    public boolean isAllowNoFile() { return allowNoFile; }
    @org.kohsuke.stapler.DataBoundSetter
    public void setAllowNoFile(boolean value) { allowNoFile=value; }
    @Override public boolean requiresWorkspace() { return false; }
    @Override public void setUp(Context context,hudson.model.Run<?,?> run,hudson.model.TaskListener listener,
            hudson.EnvVars env) throws java.io.IOException {
        throw new hudson.AbortException("Historical wrapper metadata cannot execute");
    }
    @Override public void setUp(Context context,hudson.model.Run<?,?> run,hudson.FilePath workspace,
            hudson.Launcher launcher,hudson.model.TaskListener listener,hudson.EnvVars env) throws java.io.IOException {
        throw new hudson.AbortException("Historical wrapper metadata cannot execute");
    }
    @org.jenkinsci.Symbol("withFileParameter")
    public static final class DescriptorImpl extends hudson.tasks.BuildWrapperDescriptor {
        public DescriptorImpl() { super(FileParameterWrapper.class); }
        @Override public FileParameterWrapper newInstance(org.kohsuke.stapler.StaplerRequest2 request,
                net.sf.json.JSONObject data) throws FormException {
            throw new FormException("Historical wrapper metadata cannot bind executions","name");
        }
        @Override public String getDisplayName() { return "Bind file parameter"; }
        @Override public boolean isApplicable(hudson.model.AbstractProject<?,?> project) { return false; }
    }
}
