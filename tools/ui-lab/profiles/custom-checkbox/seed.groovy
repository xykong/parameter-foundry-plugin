import com.bluersw.CheckboxParameterDefinition
import com.bluersw.analyze.Format
import com.bluersw.source.Protocol
import hudson.model.*
import hudson.tasks.Shell
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob

def content = '''{
  "CheckboxParameter": [
    {"key":"Android","value":"android","checked":true},
    {"key":"iOS","value":"ios","checked":true},
    {"key":"OpenHarmony","value":"ohos","checked":false},
    {"key":"Web","value":"web","checked":false}
  ]
}'''
def definitions = {
    [new CheckboxParameterDefinition(
        'TARGETS', 'Inline JSON checkbox list.', Protocol.HTTP_HTTPS,
        Format.JSON, '', '', '', null, content
    )]
}
def freestyle = jenkins.createProject(FreeStyleProject.class, 'ui-lab-freestyle')
freestyle.addProperty(new ParametersDefinitionProperty(definitions()))
freestyle.buildersList.add(new Shell('echo "$TARGETS"'))
freestyle.save()
def pipeline = jenkins.createProject(WorkflowJob.class, 'ui-lab-pipeline')
pipeline.addProperty(new ParametersDefinitionProperty(definitions()))
pipeline.definition = new CpsFlowDefinition('echo "${params.TARGETS}"', true)
pipeline.save()
