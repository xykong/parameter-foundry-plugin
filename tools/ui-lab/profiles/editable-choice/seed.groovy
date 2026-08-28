import hudson.model.FreeStyleProject
import hudson.model.ParametersDefinitionProperty
import hudson.tasks.Shell
import io.jenkins.plugins.editable_choice.EditableChoiceParameterDefinition
import io.jenkins.plugins.editable_choice.FilterConfig
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob

def editable = { String name, boolean restrict, String defaultValue ->
    def definition = new EditableChoiceParameterDefinition(name)
        .withDescription(restrict ? 'Restricted autocomplete choice.' : 'Editable autocomplete choice.')
        .withChoices(['production', 'staging', 'testing', 'canary'])
        .withDefaultValue(defaultValue)
        .withRestrict(restrict)
    definition.withFilterConfig(
        new FilterConfig()
            .withPrefix(false)
            .withCaseInsensitive(true)
    )
    definition
}

def definitions = {
    [
        editable('CHANNEL', false, 'staging'),
        editable('RESTRICTED_CHANNEL', true, 'production'),
    ]
}

def freestyle = jenkins.createProject(FreeStyleProject.class, 'ui-lab-freestyle')
freestyle.description = 'Editable Choice visual benchmark.'
freestyle.addProperty(new ParametersDefinitionProperty(definitions()))
freestyle.buildersList.add(new Shell('printf "CHANNEL=%s\\nRESTRICTED_CHANNEL=%s\\n" "$CHANNEL" "$RESTRICTED_CHANNEL"'))
freestyle.save()

def pipeline = jenkins.createProject(WorkflowJob.class, 'ui-lab-pipeline')
pipeline.description = 'Editable Choice Pipeline visual benchmark.'
pipeline.addProperty(new ParametersDefinitionProperty(definitions()))
pipeline.definition = new CpsFlowDefinition(
    'echo "CHANNEL=${params.CHANNEL} RESTRICTED_CHANNEL=${params.RESTRICTED_CHANNEL}"',
    true
)
pipeline.save()
