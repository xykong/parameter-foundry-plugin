import hudson.model.*
import hudson.tasks.Shell
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob

def definitions = {
    [
        new ChoiceParameterDefinition('CHANNEL', ['production', 'staging', 'testing'] as String[], 'Core single choice.'),
        new StringParameterDefinition('BRANCH', 'main', 'Core free-form string.'),
        new BooleanParameterDefinition('RUN_TESTS', true, 'Core boolean parameter.'),
        new TextParameterDefinition('NOTES', 'Release candidate', 'Core multiline text.'),
    ]
}

def freestyle = jenkins.createProject(FreeStyleProject.class, 'ui-lab-freestyle')
freestyle.description = 'Jenkins Core parameter visual benchmark.'
freestyle.addProperty(new ParametersDefinitionProperty(definitions()))
freestyle.buildersList.add(new Shell('env | sort'))
freestyle.save()

def pipeline = jenkins.createProject(WorkflowJob.class, 'ui-lab-pipeline')
pipeline.addProperty(new ParametersDefinitionProperty(definitions()))
pipeline.definition = new CpsFlowDefinition('echo "CHANNEL=${params.CHANNEL} BRANCH=${params.BRANCH}"', true)
pipeline.save()
