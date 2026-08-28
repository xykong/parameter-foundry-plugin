import de.westemeyer.plugins.multiselect.*
import hudson.model.*
import hudson.tasks.Shell
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob

def csv = '''H,Platform,Channel,Variant
V,PLATFORM,CHANNEL,VARIANT
C,Mobile,Android,Release
C,Mobile,Android,Debug
C,Mobile,iOS,AppStore
C,Desktop,Windows,Steam
C,Desktop,macOS,Notarized
'''
def definitions = {
    [new MultiselectParameterDefinition(
        'TARGET_MATRIX', 'Dependent dropdown decision tree.',
        MultiselectDecisionTree.parse(csv), MultiselectConfigurationFormat.CSV
    )]
}
def freestyle = jenkins.createProject(FreeStyleProject.class, 'ui-lab-freestyle')
freestyle.addProperty(new ParametersDefinitionProperty(definitions()))
freestyle.buildersList.add(new Shell('env | sort'))
freestyle.save()
def pipeline = jenkins.createProject(WorkflowJob.class, 'ui-lab-pipeline')
pipeline.addProperty(new ParametersDefinitionProperty(definitions()))
pipeline.definition = new CpsFlowDefinition('echo "${env.PLATFORM} ${env.CHANNEL} ${env.VARIANT}"', true)
pipeline.save()
