import jenkins.model.Jenkins
import hudson.model.FreeStyleProject
import hudson.model.ParametersDefinitionProperty
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition
import io.jenkins.plugins.parameterfoundry.model.Option
import org.jenkinsci.plugins.structs.describable.DescribableModel
import org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.SecureGroovyScript

// Only for an empty, loopback UI Lab. No builders or production source scripts.
def controller=Jenkins.get()
assert controller.items.isEmpty()&&controller.nodes.isEmpty()
assert jenkins.model.JenkinsLocationConfiguration.get().url.startsWith('http://127.0.0.1:')
assert controller.pluginManager.getPlugin('uno-choice').version=='2.8.10'
controller.numExecutors=0
controller.crumbIssuer=null

def script=new org.biouno.unochoice.model.GroovyScript(
 new SecureGroovyScript("return ['fallback','other']",true,null),
 new SecureGroovyScript("return ['fallback']",true,null))
def source=DescribableModel.of(org.biouno.unochoice.ChoiceParameter).instantiate([
 name:'REGIONS',description:'Literal source',randomName:'multiple-literal-source',script:script,
 choiceType:'PT_MULTI_SELECT',filterable:false,filterLength:1])
def sourceProject=controller.createProject(FreeStyleProject,'active-multiple-source')
sourceProject.addProperty(new ParametersDefinitionProperty(source))
assert source.getChoices().keySet().collect{String.valueOf(it)}==['fallback','other']
def target=new FoundryChoiceParameterDefinition('REGIONS')
target.description='Literal source';target.choices=[new Option('fallback'),new Option('other')]
target.selectionMode='multiple';target.renderer='list';target.visibleItemCount=source.visibleItemCount
assert target.visibleItemCount==2
target.defaultMode='none';target.standardApiInputMode='active-choices';target.environmentExport='string'
def targetProject=controller.createProject(FreeStyleProject,'active-multiple-target')
targetProject.addProperty(new ParametersDefinitionProperty(target))
assert sourceProject.builders.isEmpty()&&targetProject.builders.isEmpty()
assert controller.queue.items.length==0
controller.save()
println groovy.json.JsonOutput.toJson([neutralJobs:2,executors:0,builders:0,businessBuildsScheduled:0])
