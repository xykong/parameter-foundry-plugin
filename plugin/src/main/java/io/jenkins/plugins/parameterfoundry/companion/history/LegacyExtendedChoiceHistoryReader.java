package io.jenkins.plugins.parameterfoundry.companion.history;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.PluginManager;
import hudson.PluginWrapper;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.AdministrativeMonitor;
import hudson.model.PeriodicWork;
import io.jenkins.plugins.parameterfoundry.provider.ParameterFoundryGlobalConfiguration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;

/** Restart-bound, explicitly enabled reader for the characterized post-migration Extended Choice string family. */
@Extension
public final class LegacyExtendedChoiceHistoryReader extends AdministrativeMonitor {
    private static final Logger LOGGER = Logger.getLogger(LegacyExtendedChoiceHistoryReader.class.getName());
    private static final String SOURCE = "extended-choice-parameter";
    private State state = State.STARTING;
    private boolean bootEnabled;
    private boolean initialized;
    private Class<?> readerClass;

    public enum State {
        STARTING,
        DISABLED,
        NATIVE,
        SOURCE_UNAVAILABLE,
        STAGED_SOURCE,
        OTHER_EXPORTER,
        ACTIVE,
        RESTART_REQUIRED,
        CONFLICT,
        ERROR
    }

    @Initializer(after = InitMilestone.EXTENSIONS_AUGMENTED, before = InitMilestone.SYSTEM_CONFIG_LOADED)
    public static void initialize() {
        ExtensionList.lookupSingleton(LegacyExtendedChoiceHistoryReader.class).start();
    }

    private synchronized void start() {
        if (initialized) return;
        initialized = true;
        bootEnabled = ParameterFoundryGlobalConfiguration.get().isLegacyExtendedChoiceHistoryEnabled();
        state = State.DISABLED;
        if (bootEnabled) {
            try {
                PluginManager manager = Jenkins.get().getPluginManager();
                PluginWrapper source = manager.getPlugin(SOURCE);
                if (source != null) {
                    Class<?> nativeType = lookup(manager.uberClassLoader);
                    state = source.isActive()
                                    && source.isEnabled()
                                    && nativeType != null
                                    && nativeType.getClassLoader() == source.classLoader
                            ? State.NATIVE
                            : State.SOURCE_UNAVAILABLE;
                } else if (manager.getFailedPlugins().stream().anyMatch(failed -> SOURCE.equals(failed.name))) {
                    state = State.SOURCE_UNAVAILABLE;
                } else if (stagedSource(manager)) {
                    state = State.STAGED_SOURCE;
                } else if (lookup(manager.uberClassLoader) != null) {
                    state = State.OTHER_EXPORTER;
                } else {
                    byte[] bytes = HistoryReaderArchive.packagedBytes(HistoryReaderArchive.Family.EXTENDED_CHOICE);
                    Path archive = HistoryReaderArchive.retain(
                            managedRoot(), bytes, HistoryReaderArchive.Family.EXTENDED_CHOICE);
                    // Recheck immediately before exposing the fixed verified archive.
                    if (manager.getPlugin(SOURCE) != null
                            || stagedSource(manager)
                            || lookup(manager.uberClassLoader) != null) {
                        state = State.CONFLICT;
                    } else {
                        PluginWrapper foundry = manager.getPlugin("parameter-foundry");
                        if (foundry == null || !foundry.isActive()) throw new IOException("Foundry is unavailable");
                        HistoryReaderClasspath.expose(manager, foundry, archive);
                        readerClass = lookup(manager.uberClassLoader);
                        if (readerClass == null || readerClass.getClassLoader() != foundry.classLoader) {
                            throw new IOException("History reader class ownership check failed");
                        }
                        state = State.ACTIVE;
                    }
                }
            } catch (Exception | LinkageError failure) {
                state = State.ERROR;
                LOGGER.log(
                        Level.WARNING,
                        "Extended Choice history reader is unavailable; no compatibility acceptance",
                        failure);
            }
        }
        persist();
    }

    public synchronized State getState() {
        if (!initialized || state == State.CONFLICT || state == State.ERROR) return state;
        State before = state;
        try {
            if (readerClass != null) {
                PluginManager manager = Jenkins.get().getPluginManager();
                if (manager.getPlugin(SOURCE) != null || hasOtherExporter(manager)) {
                    state = State.CONFLICT;
                } else if (stagedSource(manager)) {
                    state = State.RESTART_REQUIRED;
                }
            }
            if (state != State.CONFLICT
                    && bootEnabled
                            != ParameterFoundryGlobalConfiguration.get().isLegacyExtendedChoiceHistoryEnabled()) {
                state = State.RESTART_REQUIRED;
            }
        } catch (RuntimeException | LinkageError failure) {
            state = State.ERROR;
            LOGGER.log(
                    Level.WARNING,
                    "Extended Choice history reader detection failed; no compatibility acceptance",
                    failure);
        }
        if (state != before) persist();
        return state;
    }

    public boolean isCompatibilityAccepted() {
        return getState() == State.ACTIVE;
    }

    @Override
    public boolean isActivated() {
        State current = getState();
        return current != State.DISABLED
                && current != State.NATIVE
                && current != State.ACTIVE
                && current != State.STARTING;
    }

    @Override
    public String getDisplayName() {
        return "Parameter Foundry Extended Choice history compatibility";
    }

    boolean hasOtherExporter(PluginManager manager) {
        for (PluginWrapper plugin : manager.getPlugins()) {
            if (!plugin.isActive() || plugin.classLoader == readerClass.getClassLoader()) continue;
            Class<?> type = lookup(plugin.classLoader);
            if (type != null && type != readerClass && type.getClassLoader() == plugin.classLoader) return true;
        }
        return false;
    }

    private static Class<?> lookup(ClassLoader loader) {
        try {
            return Class.forName(HistoryReaderArchive.Family.EXTENDED_CHOICE.legacy, false, loader);
        } catch (ClassNotFoundException absent) {
            return null;
        }
    }

    private static boolean stagedSource(PluginManager manager) {
        return Files.exists(manager.rootDir.toPath().resolve(SOURCE + ".jpi"), LinkOption.NOFOLLOW_LINKS)
                || Files.exists(manager.rootDir.toPath().resolve(SOURCE + ".hpi"), LinkOption.NOFOLLOW_LINKS)
                || Files.exists(manager.rootDir.toPath().resolve(SOURCE + ".jpi.disabled"), LinkOption.NOFOLLOW_LINKS)
                || Files.exists(manager.rootDir.toPath().resolve(SOURCE + ".hpi.disabled"), LinkOption.NOFOLLOW_LINKS);
    }

    private static Path managedRoot() {
        return Jenkins.get().getRootDir().toPath().resolve("parameter-foundry-history");
    }

    private void persist() {
        Path root = managedRoot();
        if (!bootEnabled && !Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        try {
            HistoryReaderArchive.ensureManagedRoot(root);
            Path target = root.resolve("extended-choice-reader-state.txt");
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Invalid history state file");
            }
            Path temporary = Files.createTempFile(root, "state-", ".tmp");
            try {
                Files.writeString(temporary, state.name() + "\n", StandardCharsets.US_ASCII);
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException failure) {
            state = State.ERROR;
            LOGGER.log(Level.WARNING, "Extended Choice history reader state could not be retained", failure);
        }
    }

    @Extension
    public static final class ConflictDetection extends PeriodicWork {
        @Override
        public long getRecurrencePeriod() {
            return 30000;
        }

        @Override
        protected void doRun() {
            ExtensionList.lookupSingleton(LegacyExtendedChoiceHistoryReader.class)
                    .getState();
        }
    }
}
