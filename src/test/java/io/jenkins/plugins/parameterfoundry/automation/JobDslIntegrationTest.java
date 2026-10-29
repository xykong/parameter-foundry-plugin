package io.jenkins.plugins.parameterfoundry.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.FreeStyleProject;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition;
import io.jenkins.plugins.parameterfoundry.form.FoundryFormParameterDefinition;
import java.io.File;
import java.util.List;
import java.util.Map;
import javaposse.jobdsl.dsl.DslScriptLoader;
import javaposse.jobdsl.dsl.ScriptRequest;
import javaposse.jobdsl.plugin.JenkinsJobManagement;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class JobDslIntegrationTest {

    @Test
    void publicJobDslMethodsCreateRoundTrippableDefinitions(JenkinsRule jenkins) throws Exception {
        JenkinsJobManagement management = new JenkinsJobManagement(System.out, Map.of(), new File("."));
        new DslScriptLoader(management).runScripts(List.of(new ScriptRequest("""
                        job('job-dsl-foundry') {
                          parameters {
                            foundryChoice('CHANNEL') {
                              option('staging', 'Staging')
                              option('production', 'Production')
                              renderer('searchable')
                              defaultValue('staging')
                            }
                            foundryChoice('TARGETS') {
                              selectionMode('multiple')
                              option('android', 'Android')
                              option('ios', 'iOS')
                              renderer('checkbox')
                              selectedDisplay('chips')
                              defaultValue('ios')
                              defaultValue('android')
                              selectionRange(1, 2)
                            }
                            foundryChoice('MULTI') {
                              option('a', 'A')
                              option('b', 'B')
                              selectionMode('multiple')
                              renderer('list')
                              defaultValue('a')
                              selectionRange(1, 2)
                            }
                            foundryForm('DEPLOYMENT') {
                              field('service', 'string') {
                                required(true)
                                stringRange(2, 32, '[a-z][a-z0-9-]+')
                                environmentVariable('SERVICE_NAME')
                              }
                              field('region', 'choice') {
                                choice('us-east', 'US East')
                                choice('eu-central', 'EU Central')
                                defaultValue('us-east')
                              }
                              layout('primary', 'grid') {
                                columns(2)
                                fields('service', 'region')
                              }
                              output('map', 'json')
                            }
                          }
                        }
                        """)));

        FreeStyleProject project = jenkins.jenkins.getItemByFullName("job-dsl-foundry", FreeStyleProject.class);
        assertNotNull(project);
        jenkins.configRoundtrip(project);
        ParametersDefinitionProperty property = project.getProperty(ParametersDefinitionProperty.class);
        assertNotNull(property);
        ParameterDefinition channel = property.getParameterDefinition("CHANNEL");
        ParameterDefinition targets = property.getParameterDefinition("TARGETS");
        ParameterDefinition multi = property.getParameterDefinition("MULTI");
        ParameterDefinition deployment = property.getParameterDefinition("DEPLOYMENT");
        assertInstanceOf(FoundryChoiceParameterDefinition.class, channel);
        FoundryChoiceParameterDefinition multiple = assertInstanceOf(FoundryChoiceParameterDefinition.class, targets);
        assertEquals("multiple", multiple.getSelectionMode());
        assertTrue(multiple.isMultiple());
        assertEquals(List.of("ios", "android"), multiple.getDefaultValues());
        assertEquals(1, multiple.getMinSelections());
        assertEquals(2, multiple.getMaxSelections());
        FoundryChoiceParameterDefinition foundryMultiple =
                assertInstanceOf(FoundryChoiceParameterDefinition.class, multi);
        assertEquals("multiple", foundryMultiple.getSelectionMode());
        assertEquals("list", foundryMultiple.getRenderer());
        assertEquals(List.of("a"), foundryMultiple.getDefaultValues());
        FoundryFormParameterDefinition form = assertInstanceOf(FoundryFormParameterDefinition.class, deployment);
        assertEquals(List.of("service", "region"), form.getLayouts().get(0).getFieldNames());
        assertEquals("SERVICE_NAME", form.getFields().get(0).getEnvironmentVariable());
    }
}
