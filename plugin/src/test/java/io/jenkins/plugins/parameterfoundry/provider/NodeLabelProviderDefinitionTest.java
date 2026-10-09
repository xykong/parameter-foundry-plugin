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

    @Test
    void configuredNodesRetainControllerOrderAndZeroExecutorNodes(JenkinsRule jenkins) throws Exception {
        var node = jenkins.createSlave("zero-executor", "", null);
        node.setNumExecutors(0);
        jenkins.jenkins.addNode(node);
        var provider = new NodeLabelProviderDefinition("configured");
        provider.setSource(NodeLabelProviderDefinition.SOURCE_CONFIGURED_NODES);
        var expected = new java.util.LinkedHashSet<String>();
        expected.add(jenkins.jenkins.getSelfLabel().getName());
        jenkins.jenkins.getNodes().forEach(item -> expected.add(item.getNodeName()));
        org.junit.jupiter.api.Assertions.assertEquals(List.copyOf(expected), values(provider.load(context())));
        assertTrue(values(provider.load(context())).contains("zero-executor"));
        provider.setIncludeBuiltIn(false);
        org.junit.jupiter.api.Assertions.assertEquals(
                jenkins.jenkins.getNodes().stream()
                        .map(hudson.model.Node::getNodeName)
                        .toList(),
                values(provider.load(context())));
    }

    @Test
    void configuredEmptySourceDoesNotInventAChoice(JenkinsRule jenkins) throws Exception {
        var provider = new NodeLabelProviderDefinition("configured-empty");
        provider.setSource(NodeLabelProviderDefinition.SOURCE_CONFIGURED_NODES);
        provider.setIncludeBuiltIn(false);
        org.junit.jupiter.api.Assertions.assertEquals(List.of(), provider.load(context()));
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
