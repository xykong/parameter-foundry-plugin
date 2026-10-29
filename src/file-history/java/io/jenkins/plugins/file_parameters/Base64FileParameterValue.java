package io.jenkins.plugins.file_parameters;

import hudson.EnvVars;
import hudson.model.Run;
import io.jenkins.plugins.parameterfoundry.companion.history.HistoricalFileStreams;
import io.jenkins.plugins.parameterfoundry.companion.history.LegacyFileHistoryReader;
import java.io.IOException;
import java.io.InputStream;

/** Stored native encoding; read acceptance is checked before decoding. */
public final class Base64FileParameterValue extends AbstractFileParameterValue {
    private static final long serialVersionUID = -5447497467668302565L;
    private String base64;

    public Base64FileParameterValue(String name) {
        super(name);
    }

    @Override
    public Object getValue() {
        return base64;
    }

    @Override
    public void buildEnvironment(Run<?, ?> run, EnvVars env) {
        super.buildEnvironment(run, env);
        env.put(getName(), base64);
    }

    @Override
    protected InputStream open(Run<?, ?> run) throws IOException {
        return HistoricalFileStreams.base64(base64, LegacyFileHistoryReader.maximumFileBytes());
    }
}
