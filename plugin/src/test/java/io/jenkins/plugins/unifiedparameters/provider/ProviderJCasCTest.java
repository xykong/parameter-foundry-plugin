package io.jenkins.plugins.unifiedparameters.provider;

import static io.jenkins.plugins.casc.misc.Util.toYamlString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import hudson.ExtensionList;
import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.ConfiguratorRegistry;
import io.jenkins.plugins.casc.impl.configurators.GlobalConfigurationCategoryConfigurator;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.misc.junit.jupiter.WithJenkinsConfiguredWithCode;
import io.jenkins.plugins.casc.model.CNode;
import io.jenkins.plugins.casc.model.Mapping;
import java.util.Objects;
import jenkins.model.GlobalConfigurationCategory;
import org.junit.jupiter.api.Test;

@WithJenkinsConfiguredWithCode
class ProviderJCasCTest {

    @Test
    @ConfiguredWithCode("ProviderJCasC.yml")
    void importsAndExportsSharedProvidersWithoutCredentialMaterial(JenkinsConfiguredWithCodeRule jenkins)
            throws Exception {
        UnifiedParametersGlobalConfiguration configuration = UnifiedParametersGlobalConfiguration.get();

        StaticProviderDefinition provider = assertInstanceOf(
                StaticProviderDefinition.class, configuration.getProviders().get(0));
        assertEquals("shared-releases", provider.getId());
        assertEquals("android", provider.getOptions().get(0).getValue());
        ConnectionProfile profile = configuration.getConnectionProfiles().get(0);
        assertEquals("release-service", profile.getId());
        assertEquals("service-credential", profile.getCredentialId());

        ConfigurationContext context = new ConfigurationContext(ConfiguratorRegistry.get());
        CNode node = unclassifiedRoot(context).get("unifiedParameters");
        String exported = toYamlString(node);

        assertFalse(exported.contains("password"));
        assertFalse(exported.contains("Authorization"));
        assertFalse(exported.contains("secret"));
        assertEquals(1, count(exported, "service-credential"));
    }

    private static Mapping unclassifiedRoot(ConfigurationContext context) {
        GlobalConfigurationCategory category = ExtensionList.lookup(GlobalConfigurationCategory.Unclassified.class)
                .get(0);
        GlobalConfigurationCategoryConfigurator configurator = new GlobalConfigurationCategoryConfigurator(category);
        return Objects.requireNonNull(configurator.describe(configurator.getTargetComponent(context), context))
                .asMapping();
    }

    private static int count(String text, String value) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(value, offset)) >= 0) {
            count++;
            offset += value.length();
        }
        return count;
    }
}
