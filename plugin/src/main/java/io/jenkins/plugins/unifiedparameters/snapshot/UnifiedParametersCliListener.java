package io.jenkins.plugins.unifiedparameters.snapshot;

import hudson.Extension;
import hudson.model.Job;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jenkins.cli.listeners.CLIContext;
import jenkins.cli.listeners.CLIListener;
import jenkins.model.Jenkins;

@Extension
public final class UnifiedParametersCliListener implements CLIListener {

    private static final ThreadLocal<CliBuildContext> CURRENT_BUILD = new ThreadLocal<>();

    static CliBuildContext currentBuild() {
        return CURRENT_BUILD.get();
    }

    @Override
    public void onExecution(CLIContext context) {
        CURRENT_BUILD.remove();
        if (!"build".equals(context.getCommand())) {
            return;
        }
        CliBuildContext build = parseBuild(context.getArgs());
        if (build != null) {
            CURRENT_BUILD.set(build);
        }
    }

    @Override
    public void onCompleted(CLIContext context, int exitCode) {
        CURRENT_BUILD.remove();
    }

    @Override
    public void onThrowable(CLIContext context, Throwable throwable) {
        CURRENT_BUILD.remove();
    }

    private static CliBuildContext parseBuild(List<String> args) {
        Map<String, String> parameters = new LinkedHashMap<>();
        List<String> positional = new ArrayList<>();
        for (int index = 0; index < args.size(); index++) {
            String arg = args.get(index);
            if ("-p".equals(arg)) {
                if (index + 1 >= args.size()) {
                    return null;
                }
                if (!addParameter(parameters, args.get(++index))) {
                    return null;
                }
            } else if (arg.startsWith("-p=")) {
                if (!addParameter(parameters, arg.substring(3))) {
                    return null;
                }
            } else if (!arg.startsWith("-")) {
                positional.add(arg);
            }
        }
        if (positional.size() != 1) {
            return null;
        }
        String jobName = positional.get(0);
        Job<?, ?> job = Jenkins.get().getItemByFullName(jobName, Job.class);
        if (job == null) {
            return null;
        }
        return new CliBuildContext(job, Map.copyOf(parameters));
    }

    private static boolean addParameter(Map<String, String> parameters, String encoded) {
        int separator = encoded.indexOf('=');
        if (separator <= 0) {
            return false;
        }
        parameters.put(encoded.substring(0, separator), encoded.substring(separator + 1));
        return true;
    }
}

record CliBuildContext(Job<?, ?> job, Map<String, String> parameters) {}
