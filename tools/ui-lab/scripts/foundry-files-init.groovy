import hudson.model.*
import hudson.tasks.Shell
import jenkins.model.Jenkins
import io.jenkins.plugins.parameterfoundry.companion.file.*
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition

def j = Jenkins.get()
j.setSecurityRealm(hudson.security.SecurityRealm.NO_AUTHENTICATION)
j.setAuthorizationStrategy(hudson.security.AuthorizationStrategy.UNSECURED)
j.setNumExecutors(2)
j.setCrumbIssuer(null)

def file = { String name, String mode, String path, long limit ->
  def definition = new FoundryFileParameterDefinition(name)
  definition.setDeliveryMode(mode)
  if (path != null) definition.setWorkspacePath(path)
  definition.setMaxUploadBytes(limit)
  definition
}
def flow = j.getItem('foundry-files') ?: j.createProject(WorkflowJob, 'foundry-files')
flow.removeProperty(ParametersDefinitionProperty)
flow.addProperty(new ParametersDefinitionProperty(
    file('UPLOAD', 'stash', null, 0),
    file('SMALL', 'base64', null, 2097152),
    file('WORK', 'workspace', 'inputs/data.txt', 16)))
flow.setDefinition(new CpsFlowDefinition('''
node {
  unstash 'UPLOAD'
  if (readFile('UPLOAD') != 'synthetic upload\\n') { error 'stash bytes differ' }
  withFoundryFileParameter('UPLOAD') {
    if (readFile(env.UPLOAD) != 'synthetic upload\\n') { error 'wrapper bytes differ' }
  }
  withFoundryFileParameter('SMALL') {
    if (readFile(env.SMALL) != 'small upload\\n') { error 'Base64 bytes differ' }
  }
  withFoundryFileParameter('WORK') {
    if (readFile('inputs/data.txt') != 'workspace\\n') { error 'workspace bytes differ' }
    if (env.WORK != 'workspace.txt') { error 'workspace filename differs' }
  }
  if (!fileExists('inputs/data.txt')) { error 'workspace file removed' }
  echo 'FILE_LIFECYCLE_PASSED'
}
''', true))
flow.save()

def freestyle = j.getItem('foundry-files-freestyle') ?: j.createProject(FreeStyleProject, 'foundry-files-freestyle')
while (freestyle.getProperty(ParametersDefinitionProperty) != null) freestyle.removeProperty(ParametersDefinitionProperty)
freestyle.addProperty(new ParametersDefinitionProperty(file('UPLOAD', 'workspace', 'incoming/upload.txt', 32)))
freestyle.getBuildersList().replace(new Shell('test -f incoming/upload.txt && test "$(cat incoming/upload.txt)" = "synthetic upload" && test "$UPLOAD" = "sample.txt"'))
freestyle.save()

def source = j.getItem('foundry-core-file-source') ?: j.createProject(FreeStyleProject, 'foundry-core-file-source')
while (source.getProperty(ParametersDefinitionProperty) != null) source.removeProperty(ParametersDefinitionProperty)
// Intentional identical duplicates exercise migration normalization on real Jenkins.
source.addProperty(new ParametersDefinitionProperty(new FileParameterDefinition('inputs/data.txt', 'Core relative file')))
source.addProperty(new ParametersDefinitionProperty(new FileParameterDefinition('inputs/data.txt', 'Core relative file')))
source.getBuildersList().replace(new Shell('test "$(cat inputs/data.txt)" = "core upload"'))
source.save()
j.save()
new File(j.rootDir, "parameter-foundry-files-ready").text = "ready\n"
