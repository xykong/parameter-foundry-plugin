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
def singleFixture={name,renderer,uiDefault,apiDefault,options ->
    def project=j.createProject(FreeStyleProject,name)
    def p=new FoundryChoiceParameterDefinition('SINGLE')
    p.choices=options.collect{new Option(it)}
    p.standardApiInputMode='extended-choice'
    p.renderer=renderer
    p.defaultMode=uiDefault==null ? (renderer=='radio' ? 'none' : 'first') : 'explicit'
    p.defaultValue=uiDefault
    p.legacyDefaultLiteral=apiDefault
    project.addProperty(new ParametersDefinitionProperty(p))
    project.buildersList.add(new Shell('printf "%s" "$SINGLE" > result.txt'))
    project.publishersList.add(new ArtifactArchiver('result.txt'))
    project.save()
}
singleFixture('extended-single-unknown','select',null,'unlisted-default',['alpha','beta','gamma'])
singleFixture('extended-single-last','select','beta','beta,alpha',['alpha','beta','gamma'])
singleFixture('extended-radio-empty','radio',null,null,['alpha','beta','gamma'])
singleFixture('extended-radio-last','radio','beta','beta,alpha',['alpha','beta','gamma'])
singleFixture('extended-single-long','select',null,null,(0..<240).collect{String.format('option-%03d',it)})
def emptyJob=j.createProject(FreeStyleProject,'extended-choice-empty-transport')
def emptyParameter=new FoundryChoiceParameterDefinition('TARGETS')
emptyParameter.selectionMode='multiple'
emptyParameter.choices=[new Option('alpha'),new Option('beta')]
emptyParameter.standardApiInputMode='extended-choice'
emptyParameter.environmentExportOrder='submitted'
emptyParameter.duplicatePolicy='deduplicate'
emptyParameter.defaultMode='none'
emptyParameter.legacyEmptyChoiceAllowed=true
emptyJob.addProperty(new ParametersDefinitionProperty(emptyParameter))
emptyJob.buildersList.add(new Shell('printf "%s" "$TARGETS" > result.txt'))
emptyJob.publishersList.add(new ArtifactArchiver('result.txt'))
emptyJob.save()
def orderFixture={name,renderer,mode ->
    def project=j.createProject(FreeStyleProject,name)
    def p=new FoundryChoiceParameterDefinition('TARGETS')
    p.selectionMode='multiple'
    p.renderer=renderer
    p.choices=['gamma','alpha','beta'].collect{new Option(it)}
    p.standardApiInputMode=mode
    p.environmentExportOrder=mode=='extended-choice'?'submitted':'canonical'
    p.duplicatePolicy='deduplicate'
    p.defaultMode='none'
    project.addProperty(new ParametersDefinitionProperty(p))
    project.buildersList.add(new Shell('printf "%s" "$TARGETS" > result.txt'))
    project.publishersList.add(new ArtifactArchiver('result.txt'))
    project.save()
}
orderFixture('extended-order-list','list','extended-choice')
orderFixture('extended-order-checkbox','checkbox','extended-choice')
orderFixture('canonical-order-list','list','repeated')
j.save()
new File(j.rootDir,'extended-choice-transport-ready').text='ready\n'
