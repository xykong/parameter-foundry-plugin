package io.jenkins.plugins.parameterfoundry.provider;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import hudson.model.ItemGroup;
import hudson.model.Job;
import java.util.List;
import java.util.Optional;

public final class ProviderRegistry {

    private ProviderRegistry() {}

    public static Optional<LocatedProvider> findProvider(Job<?, ?> job, String id) {
        ItemGroup<?> parent = job.getParent();
        int folderDistance = 0;
        while (parent instanceof AbstractFolder<?> folder) {
            ParameterFoundryFolderProperty property = folder.getProperties().get(ParameterFoundryFolderProperty.class);
            if (property != null) {
                Optional<SharedProviderDefinition> provider =
                        findVisibleProvider(property.getProviders(), id, folderDistance);
                if (provider.isPresent()) {
                    return Optional.of(new LocatedProvider(provider.get(), ProviderScope.FOLDER, folder.getFullName()));
                }
            }
            parent = folder.getParent();
            folderDistance++;
        }
        return findById(ParameterFoundryGlobalConfiguration.get().getProviders(), id)
                .map(provider -> new LocatedProvider(provider, ProviderScope.GLOBAL, "jenkins"));
    }

    public static Optional<LocatedProfile> findConnectionProfile(Job<?, ?> job, String id) {
        ItemGroup<?> parent = job.getParent();
        int folderDistance = 0;
        while (parent instanceof AbstractFolder<?> folder) {
            ParameterFoundryFolderProperty property = folder.getProperties().get(ParameterFoundryFolderProperty.class);
            if (property != null) {
                Optional<ConnectionProfile> profile =
                        findVisibleProfile(property.getConnectionProfiles(), id, folderDistance);
                if (profile.isPresent()) {
                    return Optional.of(new LocatedProfile(profile.get(), ProviderScope.FOLDER, folder.getFullName()));
                }
            }
            parent = folder.getParent();
            folderDistance++;
        }
        return findProfileById(ParameterFoundryGlobalConfiguration.get().getConnectionProfiles(), id)
                .map(profile -> new LocatedProfile(profile, ProviderScope.GLOBAL, "jenkins"));
    }

    private static Optional<SharedProviderDefinition> findVisibleProvider(
            List<SharedProviderDefinition> providers, String id, int folderDistance) {
        return findById(providers, id).filter(provider -> folderDistance == 0 || provider.isUsableByChildren());
    }

    private static Optional<ConnectionProfile> findVisibleProfile(
            List<ConnectionProfile> profiles, String id, int folderDistance) {
        return findProfileById(profiles, id).filter(profile -> folderDistance == 0 || profile.isUsableByChildren());
    }

    private static Optional<SharedProviderDefinition> findById(List<SharedProviderDefinition> providers, String id) {
        return providers.stream()
                .filter(provider -> provider.getId().equals(id))
                .findFirst();
    }

    private static Optional<ConnectionProfile> findProfileById(List<ConnectionProfile> profiles, String id) {
        return profiles.stream().filter(profile -> profile.getId().equals(id)).findFirst();
    }

    public record LocatedProvider(SharedProviderDefinition provider, ProviderScope scope, String ownerId) {}

    public record LocatedProfile(ConnectionProfile profile, ProviderScope scope, String ownerId) {}
}
