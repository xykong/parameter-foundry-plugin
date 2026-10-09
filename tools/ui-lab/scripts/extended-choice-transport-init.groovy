import hudson.model.*
import hudson.tasks.Shell
import hudson.tasks.ArtifactArchiver
import jenkins.model.Jenkins
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition
import io.jenkins.plugins.parameterfoundry.model.Option

def j=Jenkins.get()
jenkins.model.JenkinsLocationConfiguration.get().setUrl('http://127.0.0.1:18081/')
j.setSecurityRealm(hudson.security.SecurityRealm.NO_AUTHENTICATION)
j.setAuthorizationStrategy(hudson.security.AuthorizationStrategy.UNSECURED)
j.setCrumbIssuer(null)
j.setNumExecutors(2)
def job=j.createProject(FreeStyleProject,'extended-choice-transport')
def parameter=new FoundryChoiceParameterDefinition('TARGETS')
parameter.selectionMode='multiple'
parameter.choices=[new Option('alpha'),new Option('beta'),new Option('gamma')]
parameter.standardApiInputMode='extended-choice'
parameter.environmentExportOrder='submitted'
parameter.duplicatePolicy='deduplicate'
parameter.defaultMode='none'
job.addProperty(new ParametersDefinitionProperty(parameter))
job.buildersList.add(new Shell('printf "%s" "$TARGETS" > result.txt'))
job.publishersList.add(new ArtifactArchiver('result.txt'))
job.save()
def defaultJob=j.createProject(FreeStyleProject,'extended-choice-default')
def defaultParameter=new FoundryChoiceParameterDefinition('TARGETS')
defaultParameter.selectionMode='multiple'
defaultParameter.choices=[new Option('alpha'),new Option('beta'),new Option('gamma')]
defaultParameter.standardApiInputMode='extended-choice'
defaultParameter.environmentExportOrder='submitted'
defaultParameter.duplicatePolicy='deduplicate'
defaultParameter.defaultMode='explicit'
defaultParameter.defaultValues=['gamma','alpha']
defaultParameter.legacyDefaultLiteral=' gamma,unknown,alpha,gamma '
defaultJob.addProperty(new ParametersDefinitionProperty(defaultParameter))
defaultJob.buildersList.add(new Shell('printf "%s" "$TARGETS" > result.txt'))
defaultJob.publishersList.add(new ArtifactArchiver('result.txt'))
defaultJob.save()
j.save()
new File(j.rootDir,'extended-choice-transport-ready').text='ready\n'
