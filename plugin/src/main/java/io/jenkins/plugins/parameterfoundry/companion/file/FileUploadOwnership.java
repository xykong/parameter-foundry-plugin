package io.jenkins.plugins.parameterfoundry.companion.file;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.init.Terminator;
import hudson.model.AsyncPeriodicWork;
import hudson.model.Job;
import hudson.model.ParametersAction;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.model.queue.QueueListener;
import hudson.util.PluginServletFilter;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.cli.listeners.CLIContext;
import jenkins.cli.listeners.CLIListener;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;

/** An admitted queue item must take ownership before its submission scope exits. */
final class FileUploadOwnership {
    private static final Logger LOGGER = Logger.getLogger(FileUploadOwnership.class.getName());
    private static final ThreadLocal<Deque<Scope>> SCOPES = ThreadLocal.withInitial(ArrayDeque::new);
    private static final Set<String> LIVE = ConcurrentHashMap.newKeySet();
    static final long ORPHAN_AGE_MILLIS = Duration.ofMinutes(15).toMillis();

    private FileUploadOwnership() {}

    static Scope open(Object key) {
        var scope = new Scope(key);
        SCOPES.get().push(scope);
        return scope;
    }

    static void finish(Object key) {
        var scopes = SCOPES.get();
        if (!scopes.isEmpty() && scopes.peek().key.equals(key)) {
            scopes.peek().close();
        } else if (scopes.isEmpty()) {
            SCOPES.remove();
        }
    }

    static void track(FoundryStashedFileParameterValue value) throws IOException {
        var scopes = SCOPES.get();
        if (scopes.isEmpty()) {
            SCOPES.remove();
            throw new IOException("Stored file uploads require an HTTP or CLI submission scope.");
        }
        scopes.peek().values.add(value);
        LIVE.add(value.pendingUploadId());
    }

    static void claimed(String id) {
        LIVE.add(id);
    }

    static void released(String id) {
        LIVE.remove(id);
    }

    static final class Scope implements AutoCloseable {
        private final Object key;
        private final ArrayList<FoundryStashedFileParameterValue> values = new ArrayList<>();
        private boolean closed;

        private Scope(Object key) {
            this.key = key;
        }

        @Override
        public void close() {
            if (closed) return;
            var scopes = SCOPES.get();
            if (scopes.isEmpty() || scopes.peek() != this) {
                throw new IllegalStateException("Upload scopes must close in nesting order.");
            }
            scopes.pop();
            closed = true;
            try {
                for (var value : values) {
                    try {
                        value.discardIfUnclaimed();
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Cannot clean rejected file upload; orphan recovery will retry.", e);
                    }
                }
            } finally {
                values.clear();
                if (scopes.isEmpty()) SCOPES.remove();
            }
        }
    }

    @Extension
    public static final class HttpScope implements Filter {
        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            try (var scope = open(new Object())) {
                chain.doFilter(request, response);
            }
        }

        @Initializer(after = InitMilestone.EXTENSIONS_AUGMENTED)
        public static void register() throws ServletException {
            PluginServletFilter.addFilter(ExtensionList.lookupSingleton(HttpScope.class));
        }

        @Terminator
        public static void unregister() throws ServletException {
            PluginServletFilter.removeFilter(ExtensionList.lookupSingleton(HttpScope.class));
        }
    }

    @Extension
    public static final class CliScope implements CLIListener {
        @Override
        public void onExecution(CLIContext context) {
            open(context.getCorrelationId());
        }

        @Override
        public void onCompleted(CLIContext context, int result) {
            finish(context.getCorrelationId());
        }

        @Override
        public void onThrowable(CLIContext context, Throwable error) {
            finish(context.getCorrelationId());
        }
    }

    private static void claim(Queue.Item item) {
        for (var action : item.getActions(ParametersAction.class)) {
            for (var value : action.getAllParameters()) {
                if (value instanceof FoundryStashedFileParameterValue file) file.markQueued();
            }
        }
    }

    @Extension
    public static final class AdmittedUploads extends QueueListener {
        @Override
        public void onEnterWaiting(Queue.WaitingItem item) {
            claim(item);
        }

        @Override
        public void onEnterBlocked(Queue.BlockedItem item) {
            claim(item);
        }

        @Override
        public void onEnterBuildable(Queue.BuildableItem item) {
            claim(item);
        }
    }

    @Extension
    public static final class RetainCompletedUploads extends RunListener<Run<?, ?>> {
        @Override
        public void onCompleted(Run<?, ?> run, TaskListener listener) {
            boolean retained = false;
            for (var action : run.getActions(ParametersAction.class)) {
                for (var value : action.getAllParameters()) {
                    if (value instanceof FoundryStashedFileParameterValue file) {
                        try {
                            retained |= file.retainUnusedUpload(run);
                        } catch (IOException e) {
                            LOGGER.log(Level.WARNING, "Cannot retain unused upload; disposing its pending data.", e);
                            try {
                                file.discardPending();
                            } catch (IOException cleanup) {
                                e.addSuppressed(cleanup);
                            }
                        }
                    }
                }
            }
            if (retained) {
                try {
                    run.save();
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Cannot save retained upload ownership.", e);
                }
            }
        }
    }

    /** Crash recovery only; ordinary HTTP/CLI rejection cleans immediately. */
    @Extension
    public static final class OrphanRecovery extends AsyncPeriodicWork {
        public OrphanRecovery() {
            super("Parameter Foundry upload orphan recovery");
        }

        @Override
        public long getRecurrencePeriod() {
            return Duration.ofMinutes(5).toMillis();
        }

        @Override
        protected void execute(TaskListener listener) throws IOException {
            if (Jenkins.get().getInitLevel() != InitMilestone.COMPLETED) return;
            recover(System.currentTimeMillis());
        }

        static void recover(long now) throws IOException {
            var root = Jenkins.get().getRootDir().toPath().resolve("parameter-foundry-uploads");
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) return;
            Set<String> protectedIds = new HashSet<>(LIVE);
            for (var item : Jenkins.get().getQueue().getItems()) {
                for (var action : item.getActions(ParametersAction.class)) {
                    for (var value : action.getAllParameters()) {
                        if (value instanceof FoundryStashedFileParameterValue file)
                            protectedIds.add(file.pendingUploadId());
                    }
                }
            }
            // Pipelines can remain running without an executor after controller recovery.
            for (var job : Jenkins.get().getAllItems(Job.class)) {
                for (Run<?, ?> run = job.getLastBuild(); run != null; run = run.getPreviousBuildInProgress()) {
                    if (!run.isBuilding()) continue;
                    for (var action : run.getActions(ParametersAction.class)) {
                        for (var value : action.getAllParameters()) {
                            if (value instanceof FoundryStashedFileParameterValue file)
                                protectedIds.add(file.pendingUploadId());
                        }
                    }
                }
            }
            try (var paths = Files.list(root)) {
                for (var path : paths.toList()) {
                    var fileName = path.getFileName();
                    if (fileName == null) continue;
                    String id = fileName.toString();
                    try {
                        if (!UUID.fromString(id).toString().equals(id)) continue;
                    } catch (IllegalArgumentException e) {
                        continue;
                    }
                    if (protectedIds.contains(id)
                            || LIVE.contains(id)
                            || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) continue;
                    if (now
                                    - Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS)
                                            .toMillis()
                            >= ORPHAN_AGE_MILLIS) {
                        FileUtils.deleteDirectory(path.toFile());
                    }
                }
            }
        }
    }
}
