import hudson.model.FreeStyleProject
import hudson.model.ParametersDefinitionProperty
import hudson.tasks.Shell
import io.jenkins.plugins.restlistparam.RestListParameterDefinition
import io.jenkins.plugins.restlistparam.model.MimeType
import io.jenkins.plugins.restlistparam.model.ValueOrder
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob

def userContent = new File(jenkins.rootDir, 'userContent')
userContent.mkdirs()
new File(userContent, 'channels.json').text =
    '["production", "staging", "testing", "canary"]'
new File(userContent, 'empty.json').text = '[]'
new File(userContent, 'invalid.json').text = '{"not":"an array"'

def definition = { String endpoint, String defaultValue ->
    def channel = new RestListParameterDefinition(
        'CHANNEL',
        'REST-backed single choice with JSONPath, filtering, validation, and caching.',
        endpoint,
        '',
        MimeType.APPLICATION_JSON,
        '$[*]',
        '$'
    )
    channel.setDefaultValue(defaultValue)
    channel.setValueOrder(ValueOrder.NONE)
    channel.setFilter('.*')
    channel.setCacheTime(60)
    channel.setAllowEmptyValue(false)
    channel.setEnableValidation(true)
    channel
}
def definitions = {
    [definition('http://127.0.0.1:8080/userContent/channels.json', 'staging')]
}

def freestyle = jenkins.createProject(FreeStyleProject.class, 'ui-lab-freestyle')
freestyle.description = 'REST List Parameter visual benchmark.'
freestyle.addProperty(new ParametersDefinitionProperty(definitions()))
freestyle.buildersList.add(new Shell('printf "CHANNEL=%s\\n" "$CHANNEL"'))
freestyle.save()

def pipeline = jenkins.createProject(WorkflowJob.class, 'ui-lab-pipeline')
pipeline.description = 'REST List Parameter Pipeline visual benchmark.'
pipeline.addProperty(new ParametersDefinitionProperty(definitions()))
pipeline.definition = new CpsFlowDefinition(
    'echo "CHANNEL=${params.CHANNEL}"',
    true
)
pipeline.save()

def empty = jenkins.createProject(FreeStyleProject.class, 'ui-lab-empty')
empty.addProperty(new ParametersDefinitionProperty([
    definition('http://127.0.0.1:8080/userContent/empty.json', '')
]))
empty.save()

def error = jenkins.createProject(FreeStyleProject.class, 'ui-lab-error')
error.addProperty(new ParametersDefinitionProperty([
    definition('http://127.0.0.1:8080/userContent/invalid.json', '')
]))
error.save()
