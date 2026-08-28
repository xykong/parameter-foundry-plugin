import hudson.model.FreeStyleProject
import hudson.model.ParametersDefinitionProperty
import hudson.tasks.Shell
import jp.ikedam.jenkins.plugins.extensible_choice_parameter.ExtensibleChoiceParameterDefinition
import jp.ikedam.jenkins.plugins.extensible_choice_parameter.TextareaChoiceListProvider
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob

def definitions = {
    def channelProvider = new TextareaChoiceListProvider(
        'production\nstaging\ntesting',
        'staging',
        false,
        null
    )
    def regionProvider = new TextareaChoiceListProvider(
        'us-east\nus-west\neu-central\nap-southeast',
        'ap-southeast',
        false,
        null
    )
    def channel = new ExtensibleChoiceParameterDefinition(
        'CHANNEL',
        channelProvider,
        false,
        'Provider-backed fixed dropdown.'
    )
    def region = new ExtensibleChoiceParameterDefinition(
        'REGION',
        regionProvider,
        true,
        'Editable provider-backed dropdown.'
    )
    region.setEditableType(ExtensibleChoiceParameterDefinition.EditableType.Filter)
    [channel, region]
}

def freestyle = jenkins.createProject(FreeStyleProject.class, 'ui-lab-freestyle')
freestyle.description = 'Extensible Choice Parameter visual benchmark.'
freestyle.addProperty(new ParametersDefinitionProperty(definitions()))
freestyle.buildersList.add(new Shell('printf "CHANNEL=%s\\nREGION=%s\\n" "$CHANNEL" "$REGION"'))
freestyle.save()

def pipeline = jenkins.createProject(WorkflowJob.class, 'ui-lab-pipeline')
pipeline.description = 'Extensible Choice Parameter Pipeline visual benchmark.'
pipeline.addProperty(new ParametersDefinitionProperty(definitions()))
pipeline.definition = new CpsFlowDefinition(
    'echo "CHANNEL=${params.CHANNEL} REGION=${params.REGION}"',
    true
)
pipeline.save()
