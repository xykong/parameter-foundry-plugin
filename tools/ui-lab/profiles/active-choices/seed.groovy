import hudson.model.FreeStyleProject
import hudson.model.ParametersDefinitionProperty
import hudson.tasks.Shell
import org.biouno.unochoice.ChoiceParameter
import org.biouno.unochoice.model.GroovyScript
import org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.SecureGroovyScript
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob

def script = { List<String> values, List<String> selected ->
    def entries = values.collect { value ->
        "'${value}${selected.contains(value) ? ':selected' : ''}'"
    }.join(', ')
    new GroovyScript(
        new SecureGroovyScript("return [${entries}]", true, []),
        new SecureGroovyScript("return ['fallback:selected']", true, [])
    )
}

def choice = { String name, String type, List<String> values, List<String> selected, boolean filterable ->
    new ChoiceParameter(
        name,
        "Active Choices ${type} example.",
        "ui-lab-${name.toLowerCase()}-${UUID.randomUUID()}",
        script(values, selected),
        type,
        filterable,
        1
    )
}

def definitions = {
    [
        choice('TARGETS', 'PT_MULTI_SELECT', ['android', 'ios', 'ohos', 'web'], ['android', 'ios'], true),
        choice('FEATURES', 'PT_CHECKBOX', ['login', 'payment', 'analytics', 'notifications'], ['login', 'analytics'], false),
        choice('MODE', 'PT_RADIO', ['release', 'debug', 'profile'], ['release'], false),
        choice('CHANNEL', 'PT_SINGLE_SELECT', ['production', 'staging', 'testing'], ['staging'], true),
    ]
}

def freestyle = jenkins.createProject(FreeStyleProject.class, 'ui-lab-freestyle')
freestyle.description = 'Active Choices visual benchmark.'
freestyle.addProperty(new ParametersDefinitionProperty(definitions()))
freestyle.buildersList.add(new Shell('printf "TARGETS=%s\\nFEATURES=%s\\nMODE=%s\\nCHANNEL=%s\\n" "$TARGETS" "$FEATURES" "$MODE" "$CHANNEL"'))
freestyle.save()

def pipeline = jenkins.createProject(WorkflowJob.class, 'ui-lab-pipeline')
pipeline.description = 'Active Choices Pipeline visual benchmark.'
pipeline.addProperty(new ParametersDefinitionProperty(definitions()))
pipeline.definition = new CpsFlowDefinition(
    'echo "TARGETS=${params.TARGETS} FEATURES=${params.FEATURES} MODE=${params.MODE} CHANNEL=${params.CHANNEL}"',
    true
)
pipeline.save()
