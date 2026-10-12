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
provider.cacheTtlSeconds=0
provider.options=['main','release/prod-v2','release/prod'].collect{new Option(it)}
def large=new StaticProviderDefinition('git-large')
large.cacheTtlSeconds=0
large.options=(0..<240).collect{new Option(String.format('branch-%03d',it))}
ParameterFoundryGlobalConfiguration.get().providers=[provider,large]
[['exact','default','release/prod'],['approximate','default','release'],['missing','default','unlisted'],['top','top','release/prod'],['none','none','release/prod'],['filter','default','release/prod'],['large','default','branch-239'],['loading','default','release/prod']].each{row ->
 def job=j.createProject(FreeStyleProject,'git-transport-'+row[0])
 def parameter=new FoundryChoiceParameterDefinition('REF')
 parameter.providerId=row[0]=='large'?'git-large':'git-fixture'
 parameter.renderer='searchable'
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
[['none-list', 'list', 20], ['none-one-row', 'list', 1], ['none-filter-list', 'searchable', 20]].each { row ->
 def job=j.createProject(FreeStyleProject,'git-transport-'+row[0])
 def parameter=new FoundryChoiceParameterDefinition('REF')
 parameter.providerId='git-fixture'
 parameter.renderer=row[1]
 parameter.visibleItemCount=row[2]
 parameter.standardApiInputMode='list-git-branches'
 parameter.gitSelectionMode='none'
 parameter.defaultMode='explicit'
 parameter.defaultValue='release/prod'
 job.addProperty(new ParametersDefinitionProperty(parameter))
 job.buildersList.add(new Shell('printf "%s" "$REF" > result.txt'))
 job.publishersList.add(new ArtifactArchiver('result.txt'))
 job.save()
}
new File(j.rootDir,'git-branch-transport-ready').text='ready\n'
