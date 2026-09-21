package io.jenkins.plugins.unifiedparameters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.ParametersDefinitionProperty;
import io.jenkins.plugins.unifiedparameters.model.Option;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class LocalizedChoicePageTest {
    @Test
    void injectsDefaultLocaleTemplatesAndShipsTranslations(JenkinsRule jenkins) throws Exception {
        var project = jenkins.createFreeStyleProject();
        var parameter = MultipleChoiceFixtures.multipleChoice("TARGETS");
        parameter.setChoices(List.of(new Option("unchanged-value")));
        project.addProperty(new ParametersDefinitionProperty(parameter));
        try (var client =
                jenkins.createWebClient().withJavaScriptEnabled(false).withThrowExceptionOnFailingStatusCode(false)) {
            HtmlPage page = client.getPage(project, "build?delay=0sec");
            HtmlElement field = page.querySelector("[data-unified-parameter]");
            assertEquals(
                    "{0} matches; showing {1}. Refine the search. {2} selected.",
                    field.getAttribute("data-up-limited-matches"));
        }
        for (String suffix : List.of("", "_zh_CN", "_ja", "_ko")) {
            Properties messages = new Properties();
            String resource = "/io/jenkins/plugins/unifiedparameters/Messages" + suffix + ".properties";
            try (InputStream stream = getClass().getResourceAsStream(resource)) {
                assertTrue(stream != null, resource);
                messages.load(stream);
            }
            assertTrue(messages.containsKey("LimitedMatches"), resource);
            assertTrue(messages.containsKey("CustomValueSelected"), resource);
        }
    }
}
