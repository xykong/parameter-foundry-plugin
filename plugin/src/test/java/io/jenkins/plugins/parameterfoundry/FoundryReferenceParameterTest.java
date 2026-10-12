package io.jenkins.plugins.parameterfoundry;

import static org.junit.jupiter.api.Assertions.*;

import hudson.EnvVars;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterValue;
import io.jenkins.plugins.parameterfoundry.definition.FoundryReferenceParameterDefinition;
import java.nio.charset.StandardCharsets;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.StaplerRequest2;

@WithJenkins
class FoundryReferenceParameterTest {
    @Test
    void preservesNativeLiteralTransportsAndDefaults(JenkinsRule j) throws Exception {
        JSONObject fixture;
        try (var stream = getClass().getResourceAsStream("active-choices-reference-2.8.10.json")) {
            assertNotNull(stream);
            fixture = JSONObject.fromObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
        var definition = new FoundryReferenceParameterDefinition("REFERENCE");
        definition.setDescription("Example");
        for (Object raw : fixture.getJSONArray("cli")) {
            var row = (JSONObject) raw;
            check(row, definition.createValue(row.get("input") instanceof JSONNull ? null : row.getString("input")));
        }
        for (Object raw : fixture.getJSONArray("form")) {
            var row = (JSONObject) raw;
            check(row, definition.createValue((StaplerRequest2) null, row.getJSONObject("input")));
        }
        var empty = assertInstanceOf(StringParameterValue.class, definition.getDefaultParameterValue());
        assertEquals("", empty.getValue());
        assertNull(empty.getDescription());
        assertSame(definition, definition.copyWithDefaultValue(new StringParameterValue("REFERENCE", "history")));
    }

    @Test
    void rendersSanitizedPresentationAfterConfigurationRoundtrip(JenkinsRule j) throws Exception {
        var project = j.createFreeStyleProject();
        var definition = new FoundryReferenceParameterDefinition("REFERENCE");
        definition.setHtml("<b>presentation only</b><script>alert(1)</script><input name=value value=evil>");
        project.addProperty(new ParametersDefinitionProperty(definition));
        j.configRoundtrip(project);
        var stored = (FoundryReferenceParameterDefinition)
                project.getProperty(ParametersDefinitionProperty.class).getParameterDefinition("REFERENCE");
        assertEquals(definition.getHtml(), stored.getHtml());
        assertEquals("<b>presentation only</b>", stored.getSanitizedHtml());
        var page = j.createWebClient()
                .withJavaScriptEnabled(false)
                .withThrowExceptionOnFailingStatusCode(false)
                .getPage(project, "build?delay=0sec");
        assertEquals(
                "presentation only",
                page.<org.htmlunit.html.HtmlElement>getFirstByXPath("//div[@data-foundry-reference-content='true']/b")
                        .getTextContent());
        assertEquals(
                1,
                page.getByXPath("//div[@data-foundry-reference='true']//input[@name='value' and @value='']")
                        .size());
        assertEquals(0, j.jenkins.getQueue().getItems().length);
        assertThrows(IllegalArgumentException.class, () -> stored.setHtml("x".repeat(65537)));
        var xml = jenkins.model.Jenkins.XSTREAM2.toXML(stored);
        var oversized = (FoundryReferenceParameterDefinition)
                jenkins.model.Jenkins.XSTREAM2.fromXML(xml.replace("<html>", "<html>" + "x".repeat(65537)));
        assertThrows(IllegalArgumentException.class, oversized::getSanitizedHtml);
    }

    private static void check(JSONObject row, hudson.model.ParameterValue raw) {
        var value = assertInstanceOf(StringParameterValue.class, raw);
        assertEquals(row.getString("type"), value.getClass().getName());
        assertEquals(row.getString("value"), value.getValue());
        EnvVars env = new EnvVars();
        value.buildEnvironment(null, env);
        assertEquals(row.getString("env"), env.get("REFERENCE"));
    }
}
