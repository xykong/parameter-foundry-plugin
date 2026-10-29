#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = ["playwright>=1.55,<2"]
# ///
"""Characterize synthetic UNO values/UI without running a Job-supplied script or business build."""
from pathlib import Path
import argparse,asyncio,hashlib,importlib.util,json,shutil,subprocess,time,urllib.request,zipfile
from playwright.async_api import async_playwright
PUBLIC=Path(__file__).resolve().parents[3]
ROOT=PUBLIC/'tools/ui-lab/.cache/active-choices-single'
ROOT.mkdir(parents=True,exist_ok=True)
spec=importlib.util.spec_from_file_location('transport',PUBLIC/'tools/ui-lab/scripts/check-extended-choice-transport.py');check=importlib.util.module_from_spec(spec);spec.loader.exec_module(check)
lab=check.lab;lab.CONTAINER='parameter-foundry-uno-single-contract-lab';lab.HOME_DIR=ROOT/'uno-single-contract-home';lab.PLUGINS_DIR=ROOT/'uno-single-contract-plugins';args=argparse.ArgumentParser();args.add_argument('--skip-build',action='store_true');options=args.parse_args()
if options.skip_build:lab.build_artifacts=lambda:None
original_prepare=lab.prepare_plugins

def prepare():
 original_prepare()
 source=ROOT/'uno-choice-2.8.10-source-oracle.jpi'
 if not source.exists():
  with urllib.request.urlopen('https://updates.jenkins.io/download/plugins/uno-choice/2.8.10/uno-choice.hpi',timeout=30) as response:source.write_bytes(response.read())
 assert hashlib.sha256(source.read_bytes()).hexdigest()=='3875250959e41c849894eb719b06b8e4a5fee4c6dae15d92a34b8415d9e0d35f'
 shutil.copy2(source,lab.PLUGINS_DIR/'uno-choice.jpi')
 for name,version in [('jquery3-api','3.7.1-594.vb_3864f326cf0'),('antisamy-markup-formatter','173.v680e3a_b_69ff3')]:
  target=lab.PLUGINS_DIR/(name+'.jpi')
  with urllib.request.urlopen('https://updates.jenkins.io/download/plugins/'+name+'/'+version+'/'+name+'.hpi',timeout=30) as response:target.write_bytes(response.read())
lab.prepare_plugins=prepare
init=(PUBLIC/'tools/ui-lab/scripts/extended-choice-transport-init.groovy').read_text()+r'''
import org.biouno.unochoice.ChoiceParameter
import org.biouno.unochoice.model.Script

['PT_RADIO','PT_SINGLE_SELECT'].eachWithIndex{type,index ->
 def project=j.createProject(FreeStyleProject,'uno-single-contract-'+index)
 def literal=new org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.SecureGroovyScript("return ['built-in','agent-z','agent-a']",true,null)
 def fallback=new org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.SecureGroovyScript('return []',true,null)
 def script=new org.biouno.unochoice.model.GroovyScript(literal,fallback)
 project.addProperty(new ParametersDefinitionProperty(new ChoiceParameter('NODE','Synthetic literal fixture only','synthetic-'+type,script,type,false,1)))
 project.save()
}
assert j.pluginManager.getPlugin('uno-choice').version=='2.8.10' && j.pluginManager.getPlugin('uno-choice').active
j.save()
['radio','select'].eachWithIndex{renderer,index ->
 def project=j.createProject(FreeStyleProject,'foundry-active-single-'+index)
 def activeParameter=new FoundryChoiceParameterDefinition('NODE')
 activeParameter.choices=['built-in','agent-z','agent-a'].collect{new Option(it)}
 activeParameter.standardApiInputMode='active-choices';activeParameter.renderer=renderer
 activeParameter.defaultMode=index==0?'none':'first'
 project.addProperty(new ParametersDefinitionProperty(activeParameter));project.save()
}

['agent-z','agent-a','\u2003',' ',' padded-agent '].each{name ->
 def node=new hudson.slaves.DumbSlave(name,'/tmp/'+name,new hudson.slaves.JNLPLauncher())
 node.setNumExecutors(0);j.addNode(node)
}
def configuredNames=([j.selfLabel.name]+j.nodes*.nodeName).findAll{it?.trim()}.unique()
new File(j.rootDir,'configured-node-expected.json').text=groovy.json.JsonOutput.toJson(configuredNames)
def whitespaceLiteral=new org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.SecureGroovyScript("return ['\\u2003',' ',' padded-agent '].findAll{it?.trim()}",true,null)
def whitespaceFallback=new org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.SecureGroovyScript('return []',true,null)
def whitespaceScript=new org.biouno.unochoice.model.GroovyScript(whitespaceLiteral,whitespaceFallback)
def whitespaceSource=new ChoiceParameter('NODE','Synthetic whitespace fixture only','synthetic-whitespace',whitespaceScript,'PT_RADIO',false,1)
def sourceWhitespaceNames=whitespaceSource.choices.keySet().toList()
assert sourceWhitespaceNames==['\u2003',' padded-agent ']
new File(j.rootDir,'node-whitespace-source-oracle.json').text=groovy.json.JsonOutput.toJson(sourceWhitespaceNames)
def orderedProvider=new io.jenkins.plugins.parameterfoundry.provider.NodeLabelProviderDefinition('ordered-nodes')
orderedProvider.source='configured-nodes';orderedProvider.cacheTtlSeconds=0;orderedProvider.failurePolicy='empty'
def emptyProvider=new io.jenkins.plugins.parameterfoundry.provider.NodeLabelProviderDefinition('empty-nodes')
emptyProvider.source='configured-nodes';emptyProvider.includeBuiltIn=false;emptyProvider.includePattern='never-matches';emptyProvider.cacheTtlSeconds=0;emptyProvider.failurePolicy='empty'
io.jenkins.plugins.parameterfoundry.provider.ParameterFoundryGlobalConfiguration.get().setProviders([orderedProvider,emptyProvider])
['radio','select'].eachWithIndex{kind,index ->
 def project=j.createProject(FreeStyleProject,'active-dynamic-'+index)
 def choice=new FoundryChoiceParameterDefinition('NODE')
 choice.providerId='ordered-nodes';choice.standardApiInputMode='active-choices';choice.renderer=kind;choice.defaultMode=index==0?'none':'first'
 project.addProperty(new ParametersDefinitionProperty(choice))
 project.buildersList.add(new Shell('printf "%s" "$NODE" > result.txt'));project.publishersList.add(new ArtifactArchiver('result.txt'));project.save()
}
def emptyProject=j.createProject(FreeStyleProject,'active-dynamic-empty')
def emptyChoice=new FoundryChoiceParameterDefinition('NODE');emptyChoice.providerId='empty-nodes';emptyChoice.standardApiInputMode='active-choices';emptyChoice.defaultMode='none'
emptyProject.addProperty(new ParametersDefinitionProperty(emptyChoice));emptyProject.save()
['return []','return 42'].eachWithIndex{text,index ->
 def project=j.createProject(FreeStyleProject,'uno-empty-'+index)
 def literal=new org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.SecureGroovyScript(text,true,null)
 def fallback=new org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.SecureGroovyScript('return []',true,null)
 project.addProperty(new ParametersDefinitionProperty(new ChoiceParameter('NODE','Synthetic empty sample','empty-'+index,new org.biouno.unochoice.model.GroovyScript(literal,fallback),'PT_RADIO',false,1)));project.save()
}

def legacyHistoryJob=j.getItemByFullName('uno-single-contract-0')
legacyHistoryJob.buildersList.add(new Shell('printf "%s" "$NODE" > result.txt'))
legacyHistoryJob.publishersList.add(new ArtifactArchiver('result.txt'));legacyHistoryJob.save()
new File(j.rootDir,'uno-single-contract-ready').text='ready'
'''
path=ROOT/'uno-single-contract-init.groovy';path.write_text(init);lab.render_init_groovy=lambda:path
FACTORIES=r'''
import jenkins.model.Jenkins
import hudson.cli.CLICommand
import hudson.EnvVars
import hudson.model.ParametersDefinitionProperty
import hudson.model.StringParameterValue
import groovy.json.JsonOutput
import net.sf.json.JSONObject
import org.kohsuke.stapler.StaplerRequest2
import java.lang.reflect.Proxy
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition
import io.jenkins.plugins.parameterfoundry.model.Option

def j=Jenkins.get();def rows=[]
def snapshot={p ->if(p==null)return [exists:false];def env=new EnvVars();p.buildEnvironment(null,env);return [exists:true,value:p.value,literal:env.get('NODE'),valueType:p.value?.class?.name]}
def request={fields ->Proxy.newProxyInstance(StaplerRequest2.classLoader,[StaplerRequest2] as Class[],{proxy,method,args ->
 if(method.name=='getParameterValues')return fields==null?null:(fields as String[])
 if(method.name=='bindJSON'){def data=args[1];return new StringParameterValue(data.getString('name'),data.getString('value'))}
 if(method.returnType==Boolean.TYPE)return false
 if(method.returnType==Integer.TYPE)return 0
 return null
} as java.lang.reflect.InvocationHandler) as StaplerRequest2}
def invoke={d,channel,input ->
 try{
  def value
  if(channel=='DEFAULT')value=d.defaultParameterValue
  else if(channel=='CLI')value=d.class.getMethod('createValue',CLICommand,String).invoke(d,null,input)
  else if(channel=='STANDARD')value=d.createValue(request(input))
  else {def form=JSONObject.fromObject(input);value=d.createValue(request(null),form)}
  snapshot(value)
 }catch(Throwable error){while(error.cause!=null&&error.cause!=error)error=error.cause;return [error:error.class.name]}
}
def cases=[[channel:'DEFAULT',input:null]]
[null,'',' built-in ','agent-a','agent-a || agent-z','unknown'].each{cases.add([channel:'CLI',input:it])}
[null,[],[''],['agent-a'],['agent-a','agent-z'],['agent-a || agent-z']].each{cases.add([channel:'STANDARD',input:it])}
['{"name":"NODE"}','{"name":"NODE","value":null}','{"name":"NODE","value":""}','{"name":"NODE","value":" agent-a || agent-z "}','{"name":"NODE","value":["agent-z","agent-a"]}','{"name":"NODE","value":42}','{"name":"NODE","value":true}','{"name":"NODE","value":[42,true,null]}','{"name":"NODE","value":{"nested":"x"}}'].each{cases.add([channel:'FORM',input:it])}
(0..1).each{index ->
 def old=j.getItemByFullName('uno-single-contract-'+index).getProperty(ParametersDefinitionProperty).getParameterDefinition('NODE')
 def target=new FoundryChoiceParameterDefinition('NODE');target.choices=[new Option('built-in'),new Option('agent-z'),new Option('agent-a')];target.standardApiInputMode='active-choices';target.defaultMode=index==0?'none':'first';target.renderer=index==0?'radio':'select'
 cases.each{c ->def source=invoke(old,c.channel,c.input);def candidate=invoke(target,c.channel,c.input);rows.add([type:old.choiceType,channel:c.channel,input:c.input,source:source,target:candidate,equivalent:source.exists==candidate.exists&&source.literal==candidate.literal&&source.error==candidate.error])}
}
def emptyDefaults=['uno-empty-0','uno-empty-1','active-dynamic-empty'].collect{name -> def d=j.getItemByFullName(name).getProperty(ParametersDefinitionProperty).getParameterDefinition('NODE');[fixture:name,result:snapshot(d.defaultParameterValue)]}
assert emptyDefaults.every{it.result.exists && it.result.literal=='' && it.result.value==''}
println JsonOutput.toJson([sourceVersion:j.pluginManager.getPlugin('uno-choice').version,targetVersion:j.pluginManager.getPlugin('parameter-foundry').version,emptyDefaults:emptyDefaults,rows:rows,sourceJobScriptsExecuted:false,syntheticLiteralScriptOnly:true,businessJobsExecuted:false])
'''

async def main():
 lab.prepare_plugins();lab.start()
 try:
  lab.wait_ready()
  deadline=time.monotonic()+60
  while time.monotonic()<deadline:
   if (lab.HOME_DIR/'uno-single-contract-ready').exists():break
   await asyncio.sleep(.5)
  else:raise RuntimeError('UNO fixture initialization failed; controller log retained')
  jar=ROOT/'uno-single-contract-cli.jar'
  with urllib.request.urlopen(lab.BASE_URL+'/jnlpJars/jenkins-cli.jar') as response:jar.write_bytes(response.read())
  with zipfile.ZipFile(PUBLIC/'target/parameter-foundry.hpi') as archive:
   manifest=archive.read('META-INF/MANIFEST.MF').decode();lines=[]
   for line in manifest.splitlines():
    if line.startswith(' ') and lines:lines[-1]+=line[1:]
    else:lines.append(line)
   expected_version=next(line.split(': ',1)[1] for line in lines if line.startswith('Plugin-Version: '))
  expected_sha=hashlib.sha256((PUBLIC/'target/parameter-foundry.hpi').read_bytes()).hexdigest()
  verified_factories=FACTORIES.replace('def j=Jenkins.get();def rows=[]', 'def j=Jenkins.get();assert j.pluginManager.getPlugin(\'parameter-foundry\').version=='+json.dumps(expected_version)+';assert java.security.MessageDigest.getInstance(\'SHA-256\').digest(j.pluginManager.getPlugin(\'parameter-foundry\').archive.bytes).encodeHex().toString()=='+json.dumps(expected_sha)+';def rows=[]')
  result=subprocess.run(['docker','run','--rm','-i','--network','host','-v',str(jar)+':/work/cli.jar:ro','maven:3.9.11-eclipse-temurin-21','java','-jar','/work/cli.jar','-s',lab.BASE_URL,'-webSocket','groovy','='],input=verified_factories.encode(),capture_output=True)
  (ROOT/'uno-single-contract-factories-stderr.log').write_bytes(result.stderr)
  assert result.returncode==0,'Factory characterization failed; private stderr preserved'
  factories=json.loads(result.stdout);(ROOT/'uno-single-runtime-empty-defaults-factories.json').write_text(json.dumps(factories,ensure_ascii=False,indent=2)+'\n')
  async with async_playwright() as pw:
   browser=await pw.chromium.launch();page=await browser.new_page();ui=[]
   for index in range(2):
    response=await page.goto(lab.BASE_URL+'/job/uno-single-contract-'+str(index)+'/build?delay=0sec')
    await page.locator('input[type=radio][name=NODE],select[name=value]').first.wait_for()
    values=await page.locator('input[type=radio][name=NODE],select[name=value] option').evaluate_all('(nodes)=>nodes.map(n=>({value:n.value,checked:!!n.checked,selected:!!n.selected}))')
    ui.append({'type':'PT_RADIO' if index==0 else 'PT_SINGLE_SELECT','httpStatus':response.status,'values':values})
   for index in range(2):
    await page.goto(lab.BASE_URL+'/job/foundry-active-single-'+str(index)+'/build?delay=0sec')
    field=page.locator('[data-parameter-name="NODE"]')
    await page.wait_for_function("document.querySelector('[data-parameter-name=NODE]').dataset.upState === 'ready'")
    if index==0:
     values=await field.locator('input[type=radio]').evaluate_all('(nodes)=>nodes.map(n=>({value:n.value,checked:!!n.checked}))')
     assert [x['value'] for x in values]==['built-in','agent-z','agent-a'] and not any(x['checked'] for x in values),values
    else:
     values=await field.locator('[data-up-enhanced] option').evaluate_all('(nodes)=>nodes.map(n=>({value:n.value,selected:!!n.selected}))')
     assert [x['value'] for x in values]==['built-in','agent-z','agent-a'] and values[0]['selected'],values
    ui.append({'target':True,'type':'PT_RADIO' if index==0 else 'PT_SINGLE_SELECT','values':values})
   assert all(x['equivalent'] for x in factories['rows']), 'Candidate source mismatch'

   expected_nodes=json.loads((lab.HOME_DIR/'configured-node-expected.json').read_text())
   source_whitespace=json.loads((lab.HOME_DIR/'node-whitespace-source-oracle.json').read_text())
   assert source_whitespace==[chr(0x2003),' padded-agent ']
   assert all(value in expected_nodes for value in source_whitespace) and ' ' not in expected_nodes
   ui.append({'configuredNodeSourceWhitespacePreserved':True,'asciiEmptyNodeOmitted':True,'retainedNamesNotTrimmed':True})
   for index in range(2):
    await page.goto(lab.BASE_URL+'/job/active-dynamic-'+str(index)+'/build?delay=0sec')
    field=page.locator('[data-parameter-name="NODE"]')
    await page.wait_for_function("document.querySelector('[data-parameter-name=NODE]').dataset.upState === 'ready'")
    if index==0:
     values=await field.locator('[data-up-enhanced] input[type=radio]').evaluate_all('(nodes)=>nodes.map(n=>({value:n.value,checked:n.checked}))')
     assert [x['value'] for x in values]==expected_nodes and not any(x['checked'] for x in values),values
    else:
     values=await field.locator('[data-up-enhanced] option').evaluate_all('(nodes)=>nodes.map(n=>({value:n.value,selected:n.selected}))')
     assert [x['value'] for x in values]==expected_nodes and values[0]['selected'],values
    assert await field.locator('[data-up-snapshot-id]').input_value()==''
    ui.append({'dynamic':True,'type':'PT_RADIO' if index==0 else 'PT_SINGLE_SELECT','values':values,'zeroCacheSnapshotOmitted':True})
   async def completed(number,expected):
    base=lab.BASE_URL+'/job/active-dynamic-0'
    for _ in range(90):
     response=await page.request.get(base+f'/{number}/api/json?tree=building,result')
     if response.ok:
      body=await response.json()
      if not body['building']:
       assert body['result']=='SUCCESS',body
       break
     await asyncio.sleep(.25)
    else:raise AssertionError('Neutral echo build timed out')
    response=await page.request.get(base+f'/{number}/artifact/result.txt');assert await response.text()==expected
   literal=' agent-a || agent-z '
   response=await page.request.post(lab.BASE_URL+'/job/active-dynamic-0/buildWithParameters?delay=0sec',form={'NODE':literal})
   assert response.status in [200,201,302],response.status
   await completed(1,literal)
   await page.goto(lab.BASE_URL+'/job/active-dynamic-0/1/rebuild/parameterized')
   assert await page.locator('input[name=value]').input_value()==literal
   await page.locator('input[name=value]').fill('<old "label"> || agent-z')
   await page.get_by_role('button',name='Rebuild',exact=True).click()
   await completed(2,'<old "label"> || agent-z')
   legacy_base=lab.BASE_URL+'/job/uno-single-contract-0'
   old_literal=' old-node || agent-z '
   response=await page.request.post(legacy_base+'/buildWithParameters?delay=0sec',form={'NODE':old_literal})
   assert response.status in [200,201,302],response.status
   for _ in range(90):
    response=await page.request.get(legacy_base+'/1/api/json?tree=building,result')
    if response.ok and not (await response.json())['building']:break
    await asyncio.sleep(.25)
   replace_script=b"import jenkins.model.Jenkins; import hudson.model.ParametersDefinitionProperty; def j=Jenkins.get(); def old=j.getItemByFullName('uno-single-contract-0'); def target=j.getItemByFullName('active-dynamic-0').getProperty(ParametersDefinitionProperty).getParameterDefinition('NODE'); old.removeProperty(ParametersDefinitionProperty);old.addProperty(new ParametersDefinitionProperty(Jenkins.XSTREAM2.fromXML(Jenkins.XSTREAM2.toXML(target))));old.save();println 'NEUTRAL_HISTORY_DEFINITION_REPLACED'"
   switched=subprocess.run(['docker','run','--rm','-i','--network','host','-v',str(jar)+':/work/cli.jar:ro','maven:3.9.11-eclipse-temurin-21','java','-jar','/work/cli.jar','-s',lab.BASE_URL,'-webSocket','groovy','='],input=replace_script,capture_output=True)
   assert switched.returncode==0,'Neutral old-history definition update failed'
   await page.goto(legacy_base+'/1/rebuild/parameterized')
   assert await page.locator('input[name=value]').input_value()==old_literal
   await page.get_by_role('button',name='Rebuild',exact=True).click()
   for _ in range(90):
    response=await page.request.get(legacy_base+'/2/api/json?tree=building,result')
    if response.ok and not (await response.json())['building']:
     assert (await response.json())['result']=='SUCCESS'
     break
    await asyncio.sleep(.25)
   response=await page.request.get(legacy_base+'/2/artifact/result.txt');assert await response.text()==old_literal
   pipeline_script=b"""import jenkins.model.Jenkins
import hudson.model.ParametersDefinitionProperty
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import java.util.concurrent.TimeUnit
import hudson.model.Result
 def j=Jenkins.get()
 def target=j.getItemByFullName('active-dynamic-0').getProperty(ParametersDefinitionProperty).getParameterDefinition('NODE')
 def child=j.createProject(WorkflowJob,'neutral-active-string-child')
 child.addProperty(new ParametersDefinitionProperty(Jenkins.XSTREAM2.fromXML(Jenkins.XSTREAM2.toXML(target))))
 child.definition=new CpsFlowDefinition('assert params.NODE instanceof String; echo \"NODE_SCALAR=${params.NODE}\"; echo \"NODE_ENV=${env.NODE}\"',true);child.save()
 def parent=j.createProject(WorkflowJob,'neutral-active-string-parent')
 parent.definition=new CpsFlowDefinition(\"build job:'neutral-active-string-child',wait:true,parameters:[string(name:'NODE',value:' unlisted || other ')]\",true);parent.save()
 assert parent.scheduleBuild2(0).get(45,TimeUnit.SECONDS).result==Result.SUCCESS
 assert child.lastBuild.result==Result.SUCCESS
 assert child.lastBuild.getLog(100).any{it.contains('NODE_SCALAR= unlisted || other ')}
 assert child.lastBuild.getLog(100).any{it.contains('NODE_ENV= unlisted || other ')}
 println 'NEUTRAL_CORE_STRING_CALLER_AND_SCALAR_CONSUMER_PASS'
"""
   pipeline=subprocess.run(['docker','run','--rm','-i','--network','host','-v',str(jar)+':/work/cli.jar:ro','maven:3.9.11-eclipse-temurin-21','java','-jar','/work/cli.jar','-s',lab.BASE_URL,'-webSocket','groovy','='],input=pipeline_script,capture_output=True)
   (ROOT/'uno-single-upstream-caller-stderr.log').write_bytes(pipeline.stderr)
   assert pipeline.returncode==0,'Neutral upstream caller contract failed; private stderr retained'
   ui.append({'upstreamCoreStringCallerPreserved':True,'pipelineParamsRemainString':True,'neutralPipelineBuilds':2})
   ui.append({'oldUnoCoreStringHistoryRebuildAfterDefinitionMigration':True,'neutralLegacyEchoBuilds':2})
   ui.append({'realHttpLiteralPreserved':True,'coreStringRebuildEditableAndEscaped':True,'neutralEchoBuilds':2})
   (ROOT/'uno-single-complete-runtime-ui.json').write_text(json.dumps(ui,indent=2)+'\n')
   await browser.close()
  print(json.dumps({'targetVersion':expected_version,'targetSha256':expected_sha,'cases':len(factories['rows']),'matches':sum(row['equivalent'] for row in factories['rows']),'ui':ui,'sourceJobScriptsExecuted':False,'syntheticLiteralScriptOnly':True,'businessJobsExecuted':False}))
 finally:
  logs=subprocess.run(['docker','logs',lab.CONTAINER],capture_output=True)
  (ROOT/'uno-single-dynamic-runtime-controller.log').write_bytes(logs.stdout+logs.stderr)
  lab.stop()
asyncio.run(main())
