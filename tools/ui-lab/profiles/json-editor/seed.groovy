import hudson.model.*
import io.jenkins.plugins.json_editor_parameter.JsonEditorParameterDefinition
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob

def schema = '''{
  "type":"object",
  "title":"Deployment",
  "properties":{
    "environment":{"type":"string","enum":["development","staging","production"]},
    "replicas":{"type":"integer","minimum":1,"maximum":10},
    "runTests":{"type":"boolean"}
  },
  "required":["environment","replicas"]
}'''
def definitions = {
    def parameter = new JsonEditorParameterDefinition('DEPLOYMENT')
    parameter.setDescription('JSON Schema deployment form.')
    parameter.setSchema(schema)
    parameter.setStartval('{"environment":"staging","replicas":3,"runTests":true}')
    parameter.setOptions('{"disable_collapse":true,"disable_edit_json":true,"disable_properties":true}')
    [parameter]
}
def freestyle = jenkins.createProject(FreeStyleProject.class, 'ui-lab-freestyle')
freestyle.addProperty(new ParametersDefinitionProperty(definitions()))
freestyle.save()
def pipeline = jenkins.createProject(WorkflowJob.class, 'ui-lab-pipeline')
pipeline.addProperty(new ParametersDefinitionProperty(definitions()))
pipeline.definition = new CpsFlowDefinition('echo "${params.DEPLOYMENT}"', true)
pipeline.save()
