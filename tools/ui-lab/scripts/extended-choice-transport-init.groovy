import hudson.model.*
import hudson.tasks.Shell
import hudson.tasks.ArtifactArchiver
import jenkins.model.Jenkins
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition
import io.jenkins.plugins.parameterfoundry.model.Option

def j=Jenkins.get()
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
j.save()
new File(j.rootDir,'extended-choice-transport-ready').text='ready\n'
