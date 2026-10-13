package io.jenkins.plugins.parameterfoundry.companion.history;

import com.google.inject.Singleton;
import hudson.Extension;
import hudson.ExtensionFinder;
import hudson.ExtensionList;
import hudson.PluginManager;
import hudson.PluginWrapper;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.AdministrativeMonitor;
import hudson.model.PeriodicWork;
import hudson.tasks.BuildWrapperDescriptor;
import io.jenkins.plugins.parameterfoundry.provider.ParameterFoundryGlobalConfiguration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildWrapper;
import org.jenkinsci.plugins.structs.SymbolLookup;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/** Restart-bound completed-value reader; active native wrappers and pending uploads remain retirement gates. */
@Extension
@Singleton
@Restricted(NoExternalUse.class)
public final class LegacyFileHistoryReader extends AdministrativeMonitor {
    private static final Logger LOGGER = Logger.getLogger(LegacyFileHistoryReader.class.getName());
    private static final String SOURCE = "file-parameters";
    private static final String WRAPPER = "io.jenkins.plugins.file_parameters.FileParameterWrapper";
    private static final List<String> TYPES = List.of(
            "io.jenkins.plugins.file_parameters.AbstractFileParameterValue",
            "io.jenkins.plugins.file_parameters.Base64FileParameterValue",
            "io.jenkins.plugins.file_parameters.StashedFileParameterValue",
            WRAPPER);
    private State state = State.STARTING;
    private boolean initialized;
    private boolean bootEnabled;
    private long bootMaximum;
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
        ExtensionList.lookupSingleton(LegacyFileHistoryReader.class).start();
    }

    synchronized void start() {
        if (initialized) return;
        initialized = true;
        state = State.DISABLED;
        try {
            var configuration = configuration();
            bootEnabled = configuration.isLegacyFileHistoryEnabled();
            bootMaximum = configuration.getLegacyFileHistoryMaxBytes();
            if (!bootEnabled) return;
            if (bootMaximum < 1) throw new IOException("Historical file byte limit must be positive");
            PluginManager manager = Jenkins.get().getPluginManager();
            PluginWrapper source = manager.getPlugin(SOURCE);
            if (source != null) {
                state = source.isActive() && source.isEnabled() && ownedBy(manager.uberClassLoader, source)
                        ? State.NATIVE
                        : State.SOURCE_UNAVAILABLE;
            } else if (manager.getFailedPlugins().stream().anyMatch(failure -> SOURCE.equals(failure.name))) {
                state = State.SOURCE_UNAVAILABLE;
            } else if (staged(manager)) {
                state = State.STAGED_SOURCE;
            } else if (anyLegacy(manager.uberClassLoader)) {
                state = State.OTHER_EXPORTER;
            } else {
                byte[] bytes = HistoryReaderArchive.packagedBytes(HistoryReaderArchive.Family.FILE);
                Path archive = HistoryReaderArchive.retain(managedRoot(), bytes, HistoryReaderArchive.Family.FILE);
                if (manager.getPlugin(SOURCE) != null || staged(manager) || anyLegacy(manager.uberClassLoader)) {
                    state = State.CONFLICT;
                } else {
                    PluginWrapper host = manager.getPlugin("parameter-foundry");
                    if (host == null || !host.isActive()) throw new IOException("File history host is unavailable");
                    HistoryReaderClasspath.expose(manager, host, archive);
                    Class<?> descriptorType = Class.forName(WRAPPER + "$DescriptorImpl", true, host.classLoader);
                    if (descriptorType.getClassLoader() != host.classLoader)
                        throw new IOException("Metadata owner mismatch");
                    Object descriptor = descriptorType.getConstructor().newInstance();
                    FileHistoryMetadataFinder finder = Jenkins.get().getExtensionList(ExtensionFinder.class).stream()
                            .filter(FileHistoryMetadataFinder.class::isInstance)
                            .map(FileHistoryMetadataFinder.class::cast)
                            .findFirst()
                            .orElseThrow(() -> new IOException("Registered file metadata finder unavailable"));
                    finder.register(descriptor);
                    Jenkins.get().refreshExtensions();
                    if (Jenkins.get().getDescriptorByType(descriptorType.asSubclass(BuildWrapperDescriptor.class))
                                    != descriptor
                            || SymbolLookup.get().findDescriptor(SimpleBuildWrapper.class, "withFileParameter")
                                    != descriptor) {
                        throw new IOException("File metadata discovery failed or symbol miss is cached");
                    }
                    if (!ownedBy(manager.uberClassLoader, host))
                        throw new IOException("File reader ownership mismatch");
                    readerClass = lookup(WRAPPER, host.classLoader);
                    state = State.ACTIVE;
                }
            }
        } catch (Exception | LinkageError failure) {
            state = State.ERROR;
            LOGGER.log(Level.WARNING, "File history reader startup failed; no acceptance", failure);
        } finally {
            persist();
        }
    }

    ParameterFoundryGlobalConfiguration configuration() {
        return ParameterFoundryGlobalConfiguration.get();
    }

    public synchronized State getState() {
        if (!initialized || state == State.ERROR || state == State.CONFLICT) return state;
        State before = state;
        try {
            PluginManager manager = Jenkins.get().getPluginManager();
            if (readerClass != null) {
                if (manager.getPlugin(SOURCE) != null || otherExporter(manager)) state = State.CONFLICT;
                else if (staged(manager)) state = State.RESTART_REQUIRED;
            }
            var configuration = configuration();
            if (state != State.CONFLICT
                    && (bootEnabled != configuration.isLegacyFileHistoryEnabled()
                            || bootMaximum != configuration.getLegacyFileHistoryMaxBytes()))
                state = State.RESTART_REQUIRED;
        } catch (RuntimeException | LinkageError failure) {
            state = State.ERROR;
            LOGGER.log(Level.WARNING, "File history reader detection failed; no acceptance", failure);
        }
        if (before != state) persist();
        return state;
    }

    public boolean isCompatibilityAccepted() {
        return getState() == State.ACTIVE;
    }

    public static long maximumFileBytes() throws IOException {
        var reader = ExtensionList.lookupSingleton(LegacyFileHistoryReader.class);
        if (!reader.isCompatibilityAccepted())
            throw new IOException("File history reader is not fully active: " + reader.getState());
        return reader.bootMaximum;
    }

    public static long addBudget(long maximum, long overhead) {
        if (maximum < 0 || overhead < 0) throw new IllegalArgumentException("Negative archive budget");
        return maximum > Long.MAX_VALUE - overhead ? Long.MAX_VALUE : maximum + overhead;
    }

    @Override
    public boolean isActivated() {
        return !Set.of(State.STARTING, State.DISABLED, State.NATIVE, State.ACTIVE)
                .contains(getState());
    }

    @Override
    public String getDisplayName() {
        return "Parameter Foundry File history compatibility";
    }

    boolean otherExporter(PluginManager manager) {
        for (PluginWrapper plugin : manager.getPlugins()) {
            if (!plugin.isActive() || plugin.classLoader == readerClass.getClassLoader()) continue;
            for (String name : TYPES) {
                Class<?> type = lookup(name, plugin.classLoader);
                if (type != null && type.getClassLoader() == plugin.classLoader) return true;
            }
        }
        return false;
    }

    private static boolean ownedBy(ClassLoader loader, PluginWrapper owner) {
        return TYPES.stream().allMatch(name -> {
            Class<?> type = lookup(name, loader);
            return type != null && type.getClassLoader() == owner.classLoader;
        });
    }

    private static boolean anyLegacy(ClassLoader loader) {
        return TYPES.stream().anyMatch(name -> lookup(name, loader) != null);
    }

    private static Class<?> lookup(String name, ClassLoader loader) {
        try {
            return Class.forName(name, false, loader);
        } catch (ClassNotFoundException absent) {
            return null;
        }
    }

    private static boolean staged(PluginManager manager) {
        return List.of(".jpi", ".hpi", ".jpi.disabled", ".hpi.disabled").stream()
                .anyMatch(suffix ->
                        Files.exists(manager.rootDir.toPath().resolve(SOURCE + suffix), LinkOption.NOFOLLOW_LINKS));
    }

    Path managedRoot() {
        return Jenkins.get().getRootDir().toPath().resolve("parameter-foundry-history");
    }

    private void persist() {
        try {
            Path root = managedRoot();
            if (!bootEnabled && state != State.ERROR && !Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
            HistoryReaderArchive.ensureManagedRoot(root);
            Path target = root.resolve("file-reader-state.txt");
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Invalid file history state target");
            }
            Path temporary = Files.createTempFile(root, "state-", ".tmp");
            try {
                Files.writeString(temporary, state.name() + "\n", StandardCharsets.US_ASCII);
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException | RuntimeException | LinkageError failure) {
            state = State.ERROR;
            LOGGER.log(Level.WARNING, "File reader status could not be retained; no acceptance", failure);
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
            ExtensionList.lookupSingleton(LegacyFileHistoryReader.class).getState();
        }
    }
}
