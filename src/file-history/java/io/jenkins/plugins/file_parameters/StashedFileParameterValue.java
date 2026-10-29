package io.jenkins.plugins.file_parameters;

import hudson.AbortException;
import hudson.model.Run;
import io.jenkins.plugins.parameterfoundry.companion.history.HistoricalStashFile;
import io.jenkins.plugins.parameterfoundry.companion.history.LegacyFileHistoryReader;
import java.io.IOException;
import java.io.InputStream;

/** Run-owned completed stash only; serialized pending-upload paths are never consumed. */
public final class StashedFileParameterValue extends AbstractFileParameterValue {
    private static final long serialVersionUID = 1L;
    private String tmpFile;

    public StashedFileParameterValue(String name) {
        super(name);
    }

    @Override
    protected InputStream open(Run<?, ?> run) throws IOException {
        long maximum = LegacyFileHistoryReader.maximumFileBytes();
        long expanded = LegacyFileHistoryReader.addBudget(maximum, 131072);
        long compressed = LegacyFileHistoryReader.addBudget(expanded, maximum / 1000);
        try {
            return HistoricalStashFile.open(run, getName(), maximum, expanded, compressed, 30_000_000_000L);
        } catch (AbortException missing) {
            if (tmpFile != null) {
                throw new IOException("No authoritative stash; pending source requires ownership verification", missing);
            }
            throw missing;
        }
    }
}
