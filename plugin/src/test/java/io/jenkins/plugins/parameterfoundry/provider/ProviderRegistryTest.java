package io.jenkins.plugins.parameterfoundry.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudbees.hudson.plugins.folder.Folder;
import hudson.model.FreeStyleProject;
import hudson.model.Items;
import io.jenkins.plugins.parameterfoundry.model.Option;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ProviderRegistryTest {

    @Test
    void nearestFolderOverridesGlobalAndSiblingProvidersStayIsolated(JenkinsRule jenkins) throws Exception {
        StaticProviderDefinition global = provider("shared", "global");
        ParameterFoundryGlobalConfiguration.get().setProviders(List.of(global));

        Folder team = jenkins.createProject(Folder.class, "team");
        StaticProviderDefinition local = provider("shared", "team");
        ParameterFoundryFolderProperty teamProperty = new ParameterFoundryFolderProperty();
        teamProperty.setProviders(List.of(local));
        team.addProperty(teamProperty);

        FreeStyleProject direct = team.createProject(FreeStyleProject.class, "direct");
        ProviderRegistry.LocatedProvider directProvider =
                ProviderRegistry.findProvider(direct, "shared").orElseThrow();
        assertEquals(
                "team",
                ((StaticProviderDefinition) directProvider.provider())
                        .getOptions()
                        .get(0)
                        .getValue());
        assertEquals(ProviderScope.FOLDER, directProvider.scope());
        assertEquals("team", directProvider.ownerId());

        Folder nested = team.createProject(Folder.class, "nested");
        FreeStyleProject nestedJob = nested.createProject(FreeStyleProject.class, "job");
        assertEquals(
                "global",
                ((StaticProviderDefinition) ProviderRegistry.findProvider(nestedJob, "shared")
                                .orElseThrow()
                                .provider())
                        .getOptions()
                        .get(0)
                        .getValue());

        local.setUsableByChildren(true);
        assertEquals(
                "team",
                ((StaticProviderDefinition) ProviderRegistry.findProvider(nestedJob, "shared")
                                .orElseThrow()
                                .provider())
                        .getOptions()
                        .get(0)
                        .getValue());

        Folder sibling = jenkins.createProject(Folder.class, "sibling");
        FreeStyleProject siblingJob = sibling.createProject(FreeStyleProject.class, "job");
        ProviderRegistry.LocatedProvider siblingProvider =
                ProviderRegistry.findProvider(siblingJob, "shared").orElseThrow();
        assertEquals(ProviderScope.GLOBAL, siblingProvider.scope());
        assertEquals(
                "global",
                ((StaticProviderDefinition) siblingProvider.provider())
                        .getOptions()
                        .get(0)
                        .getValue());
    }

    @Test
    void connectionProfileConstructsOnlyAllowedRelativeUris() {
        ConnectionProfile profile = new ConnectionProfile("service", "https://example.test/api/v1");
        profile.setAllowedPathPrefixes(List.of("/api/v1/options/"));
        profile.setAllowedQueryParameters(List.of("environment"));

        URI uri = profile.buildUri("options/releases", Map.of("environment", "staging us"));

        assertEquals("https://example.test/api/v1/options/releases?environment=staging%20us", uri.toString());
        assertThrows(IllegalArgumentException.class, () -> profile.buildUri("../admin", Map.of()));
        assertThrows(
                IllegalArgumentException.class, () -> profile.buildUri("options/releases", Map.of("token", "secret")));
        assertThrows(IllegalArgumentException.class, () -> profile.buildUri("//attacker.invalid/options", Map.of()));
    }

    @Test
    void registryRejectsDuplicateStableIds() {
        StaticProviderDefinition first = provider("duplicate", "one");
        StaticProviderDefinition second = provider("duplicate", "two");

        assertThrows(
                IllegalArgumentException.class,
                () -> ParameterFoundryGlobalConfiguration.requireUniqueIds(
                        List.of(first.getId(), second.getId()), "Provider"));
        assertTrue(first.getDisplayName().equals("duplicate"));
        assertFalse(first.getOptions().isEmpty());
    }

    @Test
    void movingNestedFolderRecomputesProviderVisibility(JenkinsRule jenkins) throws Exception {
        Folder source = jenkins.createProject(Folder.class, "source");
        StaticProviderDefinition inherited = provider("move-sensitive", "source");
        inherited.setUsableByChildren(true);
        ParameterFoundryFolderProperty property = new ParameterFoundryFolderProperty();
        property.setProviders(List.of(inherited));
        source.addProperty(property);
        Folder nested = source.createProject(Folder.class, "nested");
        FreeStyleProject job = nested.createProject(FreeStyleProject.class, "job");
        Folder target = jenkins.createProject(Folder.class, "target");

        assertTrue(ProviderRegistry.findProvider(job, "move-sensitive").isPresent());

        Items.move(nested, target);

        assertTrue(ProviderRegistry.findProvider(job, "move-sensitive").isEmpty());
    }

    private static StaticProviderDefinition provider(String id, String value) {
        StaticProviderDefinition provider = new StaticProviderDefinition(id);
        provider.setOptions(List.of(new Option(value)));
        return provider;
    }
}
