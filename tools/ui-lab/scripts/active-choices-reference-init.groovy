import jenkins.model.Jenkins
import hudson.model.FreeStyleProject
import hudson.model.ParametersDefinitionProperty
import hudson.model.ChoiceParameterDefinition
import io.jenkins.plugins.parameterfoundry.definition.FoundryReferenceParameterDefinition
import org.jenkinsci.plugins.structs.describable.DescribableModel
import org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.SecureGroovyScript

// Synthetic literal-only oracle on an empty, loopback, zero-executor Lab.
def controller=Jenkins.get()
assert controller.items.isEmpty()&&controller.nodes.isEmpty()
assert jenkins.model.JenkinsLocationConfiguration.get().url.startsWith('http://127.0.0.1:')
assert controller.pluginManager.getPlugin('uno-choice').version=='2.8.10'
controller.numExecutors=0;controller.crumbIssuer=null
def script=new org.biouno.unochoice.model.GroovyScript(
 new SecureGroovyScript("return '<b>presentation only</b>'",true,null),
 new SecureGroovyScript("return '<b>fallback</b>'",true,null))
def source=DescribableModel.of(org.biouno.unochoice.DynamicReferenceParameter).instantiate([
 name:'NOTICE',description:'Literal reference',randomName:'literal-reference',script:script,
 choiceType:'ET_FORMATTED_HTML',referencedParameters:'MODE',omitValueField:false])
def target=new FoundryReferenceParameterDefinition('NOTICE')
target.description='Literal reference';target.html='<b>presentation only</b>'
['source':source,'target':target].each{role,definition->
 def project=controller.createProject(FreeStyleProject,'active-reference-'+role)
 project.addProperty(new ParametersDefinitionProperty(
  new ChoiceParameterDefinition('MODE',['staging','production']as String[],null),definition))
 assert project.builders.isEmpty()
}
def unsafe=new FoundryReferenceParameterDefinition('NOTICE')
unsafe.html='<b onclick="alert(1)">safe</b><script>alert(1)</script><input name=value value=evil>'
controller.createProject(FreeStyleProject,'active-reference-sanitizer').addProperty(new ParametersDefinitionProperty(unsafe))
assert controller.queue.items.length==0
controller.save()
println groovy.json.JsonOutput.toJson([seeded:true,businessBuildsScheduled:0])
