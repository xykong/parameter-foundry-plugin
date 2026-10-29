package io.jenkins.plugins.parameterfoundry.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.FreeStyleProject;
import hudson.model.ParametersDefinitionProperty;
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition;
import io.jenkins.plugins.parameterfoundry.model.Option;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

class MappingProviderDefinitionTest {

    @Test
    void servesTheOptionsOfTheSelectedDependencyValue() {
        MappingProviderDefinition provider = buildTypeMapping();

        assertEquals(List.of("store"), values(provider.load(context(Map.of("BUILD_MODE", "production")))));
        assertEquals(List.of("debug", "release"), values(provider.load(context(Map.of("BUILD_MODE", "development")))));
    }

    @Test
    void fallsBackToTheDefaultOptionsForAnUnlistedValue() {
        MappingProviderDefinition provider = buildTypeMapping();

        assertEquals(List.of("debug", "release"), values(provider.load(context(Map.of("BUILD_MODE", "hotfix")))));
        assertEquals(List.of("debug", "release"), values(provider.load(context(Map.of()))));
    }

    @Test
    void failsClosedWhenNoCaseAndNoDefaultMatch() {
        MappingProviderDefinition provider = new MappingProviderDefinition("strict", "BUILD_MODE");

        ProviderException exception =
                assertThrows(ProviderException.class, () -> provider.load(context(Map.of("BUILD_MODE", "production"))));
        assertTrue(exception.getMessage().contains("no options for BUILD_MODE"), exception.getMessage());
    }

    @Test
    void rejectsDuplicateCaseValues() {
        MappingProviderDefinition provider = new MappingProviderDefinition("duplicate", "BUILD_MODE");

        assertThrows(
                IllegalArgumentException.class,
                () -> provider.setCases(List.of(new MappingCase("production"), new MappingCase("production"))));
    }

    @Test
    void theFingerprintTracksTheMapping() {
        MappingProviderDefinition first = buildTypeMapping();
        MappingProviderDefinition second = buildTypeMapping();
        assertEquals(first.configurationDigest(), second.configurationDigest());

        MappingCase extra = new MappingCase("hotfix");
        extra.setOptions(List.of(new Option("patch")));
        second.setCases(List.of(second.getCases().get(0), extra));
        assertNotEquals(first.configurationDigest(), second.configurationDigest());
    }

    @WithJenkins
    static class IntegrationTest {

        @Test
        void aDependentParameterResolvesPerDependencyValue(JenkinsRule jenkins) throws Exception {
            ParameterFoundryGlobalConfiguration.get().setProviders(List.of(buildTypeMapping()));
            FreeStyleProject job = jenkins.createFreeStyleProject("mapping-integration");
            FoundryChoiceParameterDefinition mode = new FoundryChoiceParameterDefinition("BUILD_MODE");
            mode.setChoices(List.of(new Option("production"), new Option("development")));
            FoundryChoiceParameterDefinition type = new FoundryChoiceParameterDefinition("BUILD_TYPE");
            type.setProviderId("build-type");
            type.setDependsOn(List.of("BUILD_MODE"));
            job.addProperty(new ParametersDefinitionProperty(mode, type));

            assertEquals(
                    List.of("store"),
                    values(ProviderResolutionService.resolve(
                                    job, type, ProviderOperation.RESOLVE, Map.of("BUILD_MODE", "production"))
                            .options()));
            assertEquals(
                    List.of("debug", "release"),
                    values(ProviderResolutionService.resolve(
                                    job, type, ProviderOperation.RESOLVE, Map.of("BUILD_MODE", "development"))
                            .options()));
        }
    }

    private static MappingProviderDefinition buildTypeMapping() {
        MappingProviderDefinition provider = new MappingProviderDefinition("build-type", "BUILD_MODE");
        MappingCase production = new MappingCase("production");
        production.setOptions(List.of(new Option("store")));
        provider.setCases(List.of(production));
        provider.setDefaultOptions(List.of(new Option("debug"), new Option("release")));
        return provider;
    }

    private static ProviderLoadContext context(Map<String, String> dependencies) {
        ProviderRequest request = new ProviderRequest(
                null,
                "build-type",
                "digest",
                ProviderScope.GLOBAL,
                "jenkins",
                dependencies,
                ProviderOperation.RESOLVE,
                Instant.now().plusSeconds(60),
                "tester");
        return new ProviderLoadContext(request, Optional.empty());
    }

    private static List<String> values(List<Option> options) {
        return options.stream().map(Option::getValue).toList();
    }
}
