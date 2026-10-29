package io.jenkins.plugins.parameterfoundry.companion.history;

import hudson.model.Item;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Run;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerResponse2;

/** Shared Run-bound download policy for the historical binary reader entry points. */
@Restricted(NoExternalUse.class)
public final class HistoricalFileDownload {
    private HistoricalFileDownload() {}

    @FunctionalInterface
    public interface Source {
        InputStream open(Run<?, ?> run) throws IOException, InterruptedException;
    }

    /** The source must enforce live reader state and file budgets; it is opened only after authorization. */
    public static void send(ParameterValue value, Run<?, ?> run, StaplerResponse2 response, Source source)
            throws IOException, InterruptedException {
        if (run == null) throw new IOException("A build is required to download a file parameter");
        run.getParent().checkPermission(Item.READ);
        ParametersAction parameters = run.getAction(ParametersAction.class);
        if (parameters == null || parameters.getParameters().stream().noneMatch(parameter -> parameter == value)) {
            response.sendError(404);
            return;
        }
        response.setHeader("Content-Disposition", "attachment");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setContentType("application/octet-stream");
        try (InputStream input = source.open(run);
                OutputStream output = response.getOutputStream()) {
            input.transferTo(output);
        }
    }
}
