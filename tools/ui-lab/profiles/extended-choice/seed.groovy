import com.cwctravel.hudson.plugins.extended_choice_parameter.ExtendedChoiceParameterDefinition
import hudson.model.FreeStyleProject
import hudson.model.ParametersDefinitionProperty
import hudson.tasks.Shell
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob

def extended = { String name, String type, String values, String defaults, int visible ->
    new ExtendedChoiceParameterDefinition(
        name,
        type,
        values,
        '',
        '',
        '',
        '',
        '',
        '',
        '',
        defaults,
        '',
        '',
        '',
        '',
        '',
        '',
        '',
        '',
        '',
        '',
        '',
        '',
        '',
        '',
        '',
        false,
        false,
        visible,
        "Legacy ${type} example.",
        ','
    )
}

def definitions = {
    [
        extended('TARGETS', 'PT_MULTI_SELECT', 'android,ios,ohos,web', 'android,ios', 4),
        extended('FEATURES', 'PT_CHECKBOX', 'login,payment,analytics,notifications', 'login,analytics', 4),
        extended('MODE', 'PT_RADIO', 'release,debug,profile', 'release', 3),
        extended('CHANNEL', 'PT_SINGLE_SELECT', 'production,staging,testing', 'staging', 3),
    ]
}

def freestyle = jenkins.createProject(FreeStyleProject.class, 'ui-lab-freestyle')
freestyle.description = 'Extended Choice Parameter visual benchmark.'
freestyle.addProperty(new ParametersDefinitionProperty(definitions()))
freestyle.buildersList.add(new Shell('printf "TARGETS=%s\\nFEATURES=%s\\nMODE=%s\\nCHANNEL=%s\\n" "$TARGETS" "$FEATURES" "$MODE" "$CHANNEL"'))
freestyle.save()

def pipeline = jenkins.createProject(WorkflowJob.class, 'ui-lab-pipeline')
pipeline.description = 'Extended Choice Parameter Pipeline visual benchmark.'
pipeline.addProperty(new ParametersDefinitionProperty(definitions()))
pipeline.definition = new CpsFlowDefinition(
    'echo "TARGETS=${params.TARGETS} FEATURES=${params.FEATURES} MODE=${params.MODE} CHANNEL=${params.CHANNEL}"',
    true
)
pipeline.save()
