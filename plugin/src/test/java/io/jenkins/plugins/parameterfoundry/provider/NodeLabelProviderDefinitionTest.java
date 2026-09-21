package io.jenkins.plugins.parameterfoundry.provider;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class NodeLabelProviderDefinitionTest {

    @Test
    void offersAgentNamesAndTheBuiltInNodeByDefault(JenkinsRule jenkins) throws Exception {
        jenkins.createSlave("agent-a", "linux", null);
        NodeLabelProviderDefinition provider = new NodeLabelProviderDefinition("nodes");

        List<String> values = values(provider.load(context()));

        assertTrue(values.contains("built-in"), values.toString());
        assertTrue(values.contains("agent-a"), values.toString());
    }

    @Test
    void offersLabelNamesWhenConfigured(JenkinsRule jenkins) throws Exception {
        NodeLabelProviderDefinition provider = new NodeLabelProviderDefinition("labels");
        provider.setSource(NodeLabelProviderDefinition.SOURCE_LABELS);

        List<String> values = values(provider.load(context()));

        // The built-in node always carries the built-in label; agent labels appear once an agent is
        // online, which a unit test cannot rely on.
        assertTrue(values.contains("built-in"), values.toString());
    }

    @Test
    void patternsAndFlagsNarrowTheOffer(JenkinsRule jenkins) throws Exception {
        jenkins.createSlave("agent-c", "linux", null);
        NodeLabelProviderDefinition provider = new NodeLabelProviderDefinition("filtered");
        provider.setIncludePattern("agent-.*");
        provider.setIncludeBuiltIn(false);

        List<String> values = values(provider.load(context()));

        assertTrue(values.contains("agent-c"), values.toString());
        assertTrue(!values.contains("built-in"), values.toString());
    }

    @Test
    void excludesAPatternAndRejectsAnInvalidOne() {
        NodeLabelProviderDefinition provider = new NodeLabelProviderDefinition("invalid");

        assertThrows(IllegalArgumentException.class, () -> provider.setExcludePattern("(["));
        assertThrows(IllegalArgumentException.class, () -> provider.setSource("hosts"));
    }

    @Test
    void theFingerprintTracksTheConfiguration() {
        NodeLabelProviderDefinition nodes = new NodeLabelProviderDefinition("fingerprint");
        NodeLabelProviderDefinition labels = new NodeLabelProviderDefinition("fingerprint");
        labels.setSource(NodeLabelProviderDefinition.SOURCE_LABELS);

        assertNotEquals(nodes.configurationDigest(), labels.configurationDigest());
    }

    private static ProviderLoadContext context() {
        ProviderRequest request = new ProviderRequest(
                null,
                "nodes",
                "digest",
                ProviderScope.GLOBAL,
                "jenkins",
                Map.of(),
                ProviderOperation.RESOLVE,
                Instant.now().plusSeconds(60),
                "tester");
        return new ProviderLoadContext(request, Optional.empty());
    }

    private static List<String> values(List<io.jenkins.plugins.parameterfoundry.model.Option> options) {
        return options.stream()
                .map(io.jenkins.plugins.parameterfoundry.model.Option::getValue)
                .toList();
    }
}
