package io.jenkins.plugins.parameterfoundry.companion.history;

import hudson.PluginManager;
import hudson.PluginWrapper;
import hudson.util.CachingClassLoader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.restrictions.suppressions.SuppressRestrictedWarnings;

/** Narrow reviewed exception for the restart-bound companion; not a stable Core SPI. */
final class HistoryReaderClasspath {
    private static final Set<String> VERIFIED_CORES = Set.of("2.528.3", "2.568.3");

    private HistoryReaderClasspath() {}

    @SuppressRestrictedWarnings({PluginWrapper.class, CachingClassLoader.class})
    static void expose(PluginManager manager, PluginWrapper foundry, Path archive) throws Exception {
        if (!VERIFIED_CORES.contains(Jenkins.VERSION) || !(manager.uberClassLoader instanceof CachingClassLoader)) {
            throw new IOException("History reader Core/classloader combination is not verified");
        }
        try {
            foundry.injectJarsToClasspath(archive.toFile());
        } catch (AssertionError unsupportedLoader) {
            throw new IOException("History reader plugin classloader shape is unsupported", unsupportedLoader);
        }
        ((CachingClassLoader) manager.uberClassLoader).clearCacheMisses();
    }
}
