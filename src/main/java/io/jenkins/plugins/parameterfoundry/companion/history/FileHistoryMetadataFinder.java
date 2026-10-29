package io.jenkins.plugins.parameterfoundry.companion.history;

import com.google.inject.Singleton;
import hudson.Extension;
import hudson.ExtensionComponent;
import hudson.ExtensionFinder;
import hudson.model.Hudson;
import java.util.Collection;
import java.util.List;
import jenkins.ExtensionComponentSet;

/** Owns one startup-bound metadata descriptor without linking legacy types on the normal classpath. */
@Extension
@Singleton
public final class FileHistoryMetadataFinder extends ExtensionFinder {
    private ExtensionComponent<?> component;

    synchronized void register(Object descriptor) {
        if (component != null) throw new IllegalStateException("File metadata is already registered");
        component = new ExtensionComponent<>(descriptor);
    }

    @Override
    public boolean isRefreshable() {
        return true;
    }

    @Override
    public ExtensionComponentSet refresh() {
        return ExtensionComponentSet.allOf(this);
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized <T> Collection<ExtensionComponent<T>> find(Class<T> type, Hudson controller) {
        return component != null && type.isInstance(component.getInstance())
                ? List.of((ExtensionComponent<T>) component)
                : List.of();
    }
}
