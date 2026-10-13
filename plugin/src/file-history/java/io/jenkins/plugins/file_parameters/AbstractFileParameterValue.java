package io.jenkins.plugins.file_parameters;

import hudson.EnvVars;
import hudson.model.ParameterValue;
import hudson.model.Run;
import io.jenkins.plugins.parameterfoundry.companion.history.HistoricalFileDownload;
import java.io.IOException;
import java.io.InputStream;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.WebMethod;

/** Historical serialization shape, without upload factories or controller-path access. */
public abstract class AbstractFileParameterValue extends ParameterValue {
    private static final long serialVersionUID = -3637270082524081687L;
    private String filename;

    protected AbstractFileParameterValue(String name) {
        super(name);
    }

    public final String getFilename() {
        return filename;
    }

    @Override
    public void buildEnvironment(Run<?, ?> run, EnvVars env) {
        if (filename != null) env.put(getName() + "_FILENAME", filename);
    }

    @WebMethod(name = "download")
    public final void doDownload(@AncestorInPath Run<?, ?> run, StaplerResponse2 response)
            throws IOException, InterruptedException {
        HistoricalFileDownload.send(this, run, response, this::open);
    }

    protected abstract InputStream open(Run<?, ?> run) throws IOException, InterruptedException;
}
