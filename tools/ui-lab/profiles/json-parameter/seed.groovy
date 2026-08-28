import com.github.cyanbaz.jenkins.plugins.jsonparameter.*
import hudson.model.*
import hudson.tasks.Shell
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob

def userContent = new File(jenkins.rootDir, 'userContent')
userContent.mkdirs()
new File(userContent, 'people.json').text = '''[
  {"team":"Client","name":"Alice","email":"alice@example.test"},
  {"team":"Client","name":"Bob","email":"bob@example.test"},
  {"team":"Server","name":"Carol","email":"carol@example.test"}
]'''
def source = { new RemoteSource('http://127.0.0.1:8080/userContent/people.json', '') }
def definitions = {
    [
        new JsonParameterDefinition('USER', 'Alice', source(), '$[*].name'),
        new JsonParameterReferenceDefinition(
            'EMAIL', '', source(), '$[?(@.name == "${USER}")].email', 'USER'
        ),
    ]
}
def freestyle = jenkins.createProject(FreeStyleProject.class, 'ui-lab-freestyle')
freestyle.addProperty(new ParametersDefinitionProperty(definitions()))
freestyle.buildersList.add(new Shell('echo "$USER $EMAIL"'))
freestyle.save()
def pipeline = jenkins.createProject(WorkflowJob.class, 'ui-lab-pipeline')
pipeline.addProperty(new ParametersDefinitionProperty(definitions()))
pipeline.definition = new CpsFlowDefinition('echo "${params.USER} ${params.EMAIL}"', true)
pipeline.save()
