import jenkins.model.Jenkins
import hudson.model.FreeStyleProject
import hudson.model.ParametersDefinitionProperty
import hudson.model.ChoiceParameterDefinition
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition
import io.jenkins.plugins.parameterfoundry.provider.*
import io.jenkins.plugins.parameterfoundry.model.Option
import org.jenkinsci.plugins.structs.describable.DescribableModel
import org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.SecureGroovyScript

// Run only on an empty, isolated UI Lab controller, never on a business controller.
def j=Jenkins.get()
assert j.items.empty && j.nodes.empty
assert jenkins.model.JenkinsLocationConfiguration.get().url.startsWith('http://127.0.0.1:')
j.numExecutors=0
assert j.pluginManager.getPlugin('uno-choice').version=='2.8.10'
def provider=new MappingProviderDefinition('choice-by-mode','MODE')
provider.defaultOptions=[new Option('false'),new Option('true')]
def production=new MappingCase('production');production.options=[new Option('true'),new Option('false')]
provider.cases=[production];provider.cacheTtlSeconds=0
provider.fallbackOptions=[new Option('false'),new Option('true')]
ParameterFoundryGlobalConfiguration.get().providers=[provider]
def script=new org.biouno.unochoice.model.GroovyScript(
 new SecureGroovyScript("return MODE == 'production' ? ['true','false'] : ['false','true']",true,null),
 new SecureGroovyScript("return ['false','true']",true,null))
def source=DescribableModel.of(org.biouno.unochoice.CascadeChoiceParameter).instantiate([
 name:'CHOICE',randomName:'mapping-source-oracle',script:script,choiceType:'PT_SINGLE_SELECT',
 referencedParameters:'MODE',filterable:false,filterLength:1])
def project=j.createProject(FreeStyleProject,'active-map-source')
project.addProperty(new ParametersDefinitionProperty(new ChoiceParameterDefinition('MODE',['development','production'] as String[],'Synthetic parent'),source));project.save()
['target','ordinary','none'].each{role ->
 def parent=new FoundryChoiceParameterDefinition('MODE');parent.choices=[new Option('development'),new Option('production')];parent.renderer='radio';parent.defaultMode='first'
 def child=new FoundryChoiceParameterDefinition('CHOICE');child.providerId=provider.id;child.dependsOn=['MODE'];child.renderer=role=='none'?'radio':'select';child.defaultMode=role=='none'?'none':'first'
 if(role!='ordinary'){child.standardApiInputMode='active-choices';child.activeChoicesApiDefaultMode='literal';child.activeChoicesApiDefaultLiteral='false'}
 def job=j.createProject(FreeStyleProject,'active-map-'+role);job.addProperty(new ParametersDefinitionProperty(parent,child));assert job.builders.empty;job.save()
}
j.save()
