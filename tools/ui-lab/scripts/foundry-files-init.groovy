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

def flow = j.getItem('foundry-files') ?: j.createProject(WorkflowJob, 'foundry-files')
flow.addProperty(new ParametersDefinitionProperty(
    new FoundryStashedFileParameterDefinition('UPLOAD'),
    new FoundryBase64FileParameterDefinition('SMALL')))
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
  echo 'FILE_LIFECYCLE_PASSED'
}
''', true))
flow.save()

def freestyle = j.getItem('foundry-files-freestyle') ?: j.createProject(FreeStyleProject, 'foundry-files-freestyle')
freestyle.addProperty(new ParametersDefinitionProperty(new FoundryStashedFileParameterDefinition('UPLOAD')))
freestyle.getBuildWrappersList().replace(new FoundryFileParameterWrapper('UPLOAD'))
freestyle.getBuildersList().add(new Shell('test -f "$UPLOAD" && test "$(cat "$UPLOAD")" = "synthetic upload"'))
freestyle.save()
j.save()

new File(j.rootDir, "parameter-foundry-files-ready").text = "ready\n"
