package io.jenkins.plugins.unifiedparameters.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.FreeStyleProject;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import io.jenkins.plugins.unifiedparameters.definition.UnifiedChoiceParameterDefinition;
import io.jenkins.plugins.unifiedparameters.form.UnifiedFormParameterDefinition;
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
                        job('job-dsl-unified') {
                          parameters {
                            unifiedChoice('CHANNEL') {
                              option('staging', 'Staging')
                              option('production', 'Production')
                              renderer('searchable')
                              defaultValue('staging')
                            }
                            unifiedChoice('TARGETS') {
                              selectionMode('multiple')
                              option('android', 'Android')
                              option('ios', 'iOS')
                              renderer('checkbox')
                              selectedDisplay('chips')
                              defaultValue('ios')
                              defaultValue('android')
                              selectionRange(1, 2)
                            }
                            unifiedChoice('MULTI') {
                              option('a', 'A')
                              option('b', 'B')
                              selectionMode('multiple')
                              renderer('list')
                              defaultValue('a')
                              selectionRange(1, 2)
                            }
                            unifiedForm('DEPLOYMENT') {
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

        FreeStyleProject project = jenkins.jenkins.getItemByFullName("job-dsl-unified", FreeStyleProject.class);
        assertNotNull(project);
        jenkins.configRoundtrip(project);
        ParametersDefinitionProperty property = project.getProperty(ParametersDefinitionProperty.class);
        assertNotNull(property);
        ParameterDefinition channel = property.getParameterDefinition("CHANNEL");
        ParameterDefinition targets = property.getParameterDefinition("TARGETS");
        ParameterDefinition multi = property.getParameterDefinition("MULTI");
        ParameterDefinition deployment = property.getParameterDefinition("DEPLOYMENT");
        assertInstanceOf(UnifiedChoiceParameterDefinition.class, channel);
        UnifiedChoiceParameterDefinition multiple = assertInstanceOf(UnifiedChoiceParameterDefinition.class, targets);
        assertEquals("multiple", multiple.getSelectionMode());
        assertTrue(multiple.isMultiple());
        assertEquals(List.of("ios", "android"), multiple.getDefaultValues());
        assertEquals(1, multiple.getMinSelections());
        assertEquals(2, multiple.getMaxSelections());
        UnifiedChoiceParameterDefinition unifiedMultiple =
                assertInstanceOf(UnifiedChoiceParameterDefinition.class, multi);
        assertEquals("multiple", unifiedMultiple.getSelectionMode());
        assertEquals("list", unifiedMultiple.getRenderer());
        assertEquals(List.of("a"), unifiedMultiple.getDefaultValues());
        UnifiedFormParameterDefinition form = assertInstanceOf(UnifiedFormParameterDefinition.class, deployment);
        assertEquals(List.of("service", "region"), form.getLayouts().get(0).getFieldNames());
        assertEquals("SERVICE_NAME", form.getFields().get(0).getEnvironmentVariable());
    }
}
