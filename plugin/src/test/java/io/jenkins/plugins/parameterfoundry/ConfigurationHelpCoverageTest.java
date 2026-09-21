package io.jenkins.plugins.parameterfoundry;

import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.FreeStyleProject;
import hudson.model.ParametersDefinitionProperty;
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition;
import io.jenkins.plugins.parameterfoundry.model.Option;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Keeps the configuration UI self-documenting: every field declared by a {@code config.jelly}
 * must ship a {@code help-<field>.html} next to it, and the descriptor must serve that help.
 */
@WithJenkins
class ConfigurationHelpCoverageTest {

    private static final Pattern FIELD = Pattern.compile("field=\"([A-Za-z0-9_]+)\"");

    @Test
    void everyConfiguredFieldShipsHelp() throws Exception {
        Path root = Path.of("target", "classes", "io", "jenkins", "plugins", "parameterfoundry");
        assertTrue(Files.isDirectory(root), root.toString());
        List<String> missing = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path jelly : paths.filter(path -> path.getFileName().toString().equals("config.jelly"))
                    .toList()) {
                String text = Files.readString(jelly);
                Matcher matcher = FIELD.matcher(text);
                Set<String> fields = new TreeSet<>();
                while (matcher.find()) {
                    fields.add(matcher.group(1));
                }
                for (String field : fields) {
                    Path help = jelly.getParent().resolve("help-" + field + ".html");
                    if (!Files.isRegularFile(help)) {
                        missing.add(root.relativize(help).toString());
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(), "Fields without help: " + missing);
    }

    @Test
    void descriptorServesFieldHelp(JenkinsRule jenkins) throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject("help-coverage");
        FoundryChoiceParameterDefinition definition = new FoundryChoiceParameterDefinition("CHANNEL");
        definition.setChoices(List.of(new Option("staging")));
        project.addProperty(new ParametersDefinitionProperty(definition));

        String page = jenkins.createWebClient()
                .withJavaScriptEnabled(false)
                .getPage(project, "configure")
                .asXml();
        assertTrue(page.contains("help/selectionMode"), "the configure page must link field help");

        String help = jenkins.createWebClient()
                .goTo("descriptor/io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition"
                        + "/help/selectionMode")
                .asNormalizedText();
        assertTrue(help.contains("How many values the parameter submits"), help);
    }
}
