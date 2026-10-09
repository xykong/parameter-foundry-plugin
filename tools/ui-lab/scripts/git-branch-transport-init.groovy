import hudson.model.*
import hudson.tasks.Shell
import hudson.tasks.ArtifactArchiver
import jenkins.model.Jenkins
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition
import io.jenkins.plugins.parameterfoundry.model.Option
import io.jenkins.plugins.parameterfoundry.provider.*

def j=Jenkins.get()
j.setSecurityRealm(hudson.security.SecurityRealm.NO_AUTHENTICATION)
j.setAuthorizationStrategy(hudson.security.AuthorizationStrategy.UNSECURED)
j.setCrumbIssuer(null)
j.setNumExecutors(2)
def provider=new StaticProviderDefinition('git-fixture')
provider.options=['main','release/prod-v2','release/prod'].collect{new Option(it)}
ParameterFoundryGlobalConfiguration.get().providers=[provider]
[['exact','default','release/prod'],['approximate','default','release'],['missing','default','unlisted'],['top','top','release/prod'],['none','none','release/prod']].each{row ->
 def job=j.createProject(FreeStyleProject,'git-transport-'+row[0])
 def parameter=new FoundryChoiceParameterDefinition('REF')
 parameter.providerId='git-fixture'
 parameter.standardApiInputMode='list-git-branches'
 parameter.gitSelectionMode=row[1]
 parameter.defaultMode='explicit'
 parameter.defaultValue=row[2]
 job.addProperty(new ParametersDefinitionProperty(parameter))
 job.buildersList.add(new Shell('printf "%s" "$REF" > result.txt'))
 job.publishersList.add(new ArtifactArchiver('result.txt'))
 job.save()
}
j.save()
new File(j.rootDir,'git-branch-transport-ready').text='ready\n'
