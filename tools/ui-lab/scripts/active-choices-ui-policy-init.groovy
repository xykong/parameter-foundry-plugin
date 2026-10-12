import jenkins.model.Jenkins
import hudson.model.FreeStyleProject
import hudson.model.ParametersDefinitionProperty
import hudson.model.ChoiceParameterDefinition
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition
import io.jenkins.plugins.parameterfoundry.model.Option
import io.jenkins.plugins.parameterfoundry.provider.*
import org.jenkinsci.plugins.structs.describable.DescribableModel
import org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.SecureGroovyScript

// Isolated zero-executor Lab only. No business logic, builds or external services.
def controller=Jenkins.get()
assert controller.items.isEmpty()&&controller.nodes.isEmpty()
assert jenkins.model.JenkinsLocationConfiguration.get().url.startsWith('http://127.0.0.1:')
assert controller.pluginManager.getPlugin('uno-choice').version=='2.8.10'
controller.numExecutors=0

def scriptFor={text->new org.biouno.unochoice.model.GroovyScript(
 new SecureGroovyScript(text,true,null),new SecureGroovyScript("return ['fallback']",true,null))}
def source=DescribableModel.of(org.biouno.unochoice.ChoiceParameter).instantiate([
 name:'REGIONS',randomName:'filter-source',script:scriptFor("return ['fallback','other','ALPHA','a&b']"),
 choiceType:'PT_MULTI_SELECT',filterable:true,filterLength:5])
def sourceProject=controller.createProject(FreeStyleProject,'active-filter-source')
sourceProject.addProperty(new ParametersDefinitionProperty(source))
def nativeOptions=source.getChoices()
def target=new FoundryChoiceParameterDefinition('REGIONS')
target.choices=nativeOptions.collect{key,value->def option=new Option(String.valueOf(key));option.label=String.valueOf(value);option}
target.selectionMode='multiple';target.renderer='list';target.visibleItemCount=source.visibleItemCount
assert target.visibleItemCount==4
target.defaultMode='none';target.standardApiInputMode='active-choices';target.environmentExport='string'
target.activeChoicesFilterMode='regex';target.activeChoicesFilterLength=5
controller.createProject(FreeStyleProject,'active-filter-target').addProperty(new ParametersDefinitionProperty(target))

def cascade=DescribableModel.of(org.biouno.unochoice.CascadeChoiceParameter).instantiate([
 name:'SIZE',randomName:'cascade-source',script:scriptFor("return ['small','large']"),
 choiceType:'PT_SINGLE_SELECT',referencedParameters:'MODE',filterable:false,filterLength:5])
def parent=new FoundryChoiceParameterDefinition('MODE');parent.choices=[new Option('staging'),new Option('production')]
controller.createProject(FreeStyleProject,'active-retain-source').addProperty(new ParametersDefinitionProperty(parent,cascade))
def coreParent=new ChoiceParameterDefinition('MODE',['staging','production']as String[],null)
def coreCascade=jenkins.model.Jenkins.XSTREAM2.fromXML(jenkins.model.Jenkins.XSTREAM2.toXML(cascade))
controller.createProject(FreeStyleProject,'active-retain-core-source').addProperty(new ParametersDefinitionProperty(coreParent,coreCascade))
def provider=new StaticProviderDefinition('constant-sizes');provider.options=[new Option('small'),new Option('large')]
ParameterFoundryGlobalConfiguration.get().providers=[provider]
def dropProvider=new MappingProviderDefinition('drop-sizes','MODE');dropProvider.defaultOptions=[new Option('small'),new Option('large')]
def productionCase=new MappingCase('production');productionCase.options=[new Option('small')];dropProvider.cases=[productionCase];dropProvider.cacheTtlSeconds=0
ParameterFoundryGlobalConfiguration.get().providers=[provider,dropProvider]
['retain-valid','default','drop-invalid'].each{policy->
 def base=new FoundryChoiceParameterDefinition('MODE');base.choices=[new Option('staging'),new Option('production')]
 def child=new FoundryChoiceParameterDefinition('SIZE');child.providerId=policy=='drop-invalid'?dropProvider.id:provider.id;child.dependsOn=['MODE']
 child.standardApiInputMode='active-choices';child.activeChoicesApiDefaultMode='literal';child.activeChoicesApiDefaultLiteral='small'
 child.activeChoicesFilterMode='none';child.activeChoicesRefreshMode=policy=='drop-invalid'?'retain-valid':policy;child.renderer='select';child.defaultMode='first'
 controller.createProject(FreeStyleProject,'active-retain-'+policy).addProperty(new ParametersDefinitionProperty(base,child))
}
def largeValues=(0..<181).collect{String.format('region-%03d',it)}
def largeSource=DescribableModel.of(org.biouno.unochoice.ChoiceParameter).instantiate([
 name:'REGIONS',randomName:'large-source',script:scriptFor('return '+largeValues.collect{"'"+it+"'"}.join(',').with{'['+it+']'}),
 choiceType:'PT_MULTI_SELECT',filterable:false,filterLength:5])
controller.createProject(FreeStyleProject,'active-large-source').addProperty(new ParametersDefinitionProperty(largeSource))
largeSource.getChoices()
def largeTarget=new FoundryChoiceParameterDefinition('REGIONS');largeTarget.choices=largeValues.collect{new Option(it)}
largeTarget.selectionMode='multiple';largeTarget.renderer='list';largeTarget.visibleItemCount=largeSource.visibleItemCount
largeTarget.defaultMode='none';largeTarget.standardApiInputMode='active-choices';largeTarget.environmentExport='string';largeTarget.activeChoicesFilterMode='none'
controller.createProject(FreeStyleProject,'active-large-target').addProperty(new ParametersDefinitionProperty(largeTarget))
assert controller.getAllItems(FreeStyleProject).every{it.builders.isEmpty()}
controller.save()
println groovy.json.JsonOutput.toJson([neutralJobs:9,executors:0,businessBuildsScheduled:0])
