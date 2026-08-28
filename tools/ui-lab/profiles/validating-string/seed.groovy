import hudson.model.*
import hudson.plugins.validating_string_parameter.ValidatingStringParameterDefinition
import hudson.tasks.Shell
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob

def definitions = {
    [new ValidatingStringParameterDefinition(
        'VERSION', '1.2.3', '\\d+\\.\\d+\\.\\d+',
        'Use semantic version format, for example 1.2.3.',
        'Validated release version.'
    )]
}
def freestyle = jenkins.createProject(FreeStyleProject.class, 'ui-lab-freestyle')
freestyle.addProperty(new ParametersDefinitionProperty(definitions()))
freestyle.buildersList.add(new Shell('echo "$VERSION"'))
freestyle.save()
def pipeline = jenkins.createProject(WorkflowJob.class, 'ui-lab-pipeline')
pipeline.addProperty(new ParametersDefinitionProperty(definitions()))
pipeline.definition = new CpsFlowDefinition('echo "${params.VERSION}"', true)
pipeline.save()
