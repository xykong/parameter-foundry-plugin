#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = ["playwright>=1.55,<2"]
# ///
"""Verify empty inline options against neutral UNO fixtures, never production Jobs."""
from pathlib import Path
import asyncio, json, subprocess, time, urllib.request, hashlib, zipfile, shutil
from playwright.async_api import async_playwright

SCRIPT = Path(__file__).with_name('check-active-choices-single.py')
source = SCRIPT.read_text()
assert source.endswith('asyncio.run(main())\n')
ns = {'__file__': str(SCRIPT), '__name__': 'empty_option_oracle'}
exec(compile(source.removesuffix('asyncio.run(main())\n'), str(SCRIPT), 'exec'), ns)
ROOT = ns['PUBLIC'] / 'tools/ui-lab/.cache/active-choices-empty-options'
ROOT.mkdir(parents=True, exist_ok=True)
ns['ROOT'] = ROOT
lab = ns['lab']
lab.CONTAINER = 'parameter-foundry-empty-option-lab'
lab.HOME_DIR = ROOT / 'home'
lab.PLUGINS_DIR = ROOT / 'plugins'
RESULT = ns['PUBLIC'] / 'docs/testing/results/active-choices-empty-options.json'

def prepare_cached_plugins():
    ns['original_prepare']()
    oracle = ROOT / 'uno-choice-2.8.10-source-oracle.jpi'
    if not oracle.exists():
        with urllib.request.urlopen('https://updates.jenkins.io/download/plugins/uno-choice/2.8.10/uno-choice.hpi', timeout=30) as response:
            oracle.write_bytes(response.read())
    assert hashlib.sha256(oracle.read_bytes()).hexdigest() == '3875250959e41c849894eb719b06b8e4a5fee4c6dae15d92a34b8415d9e0d35f'
    shutil.copy2(oracle, lab.PLUGINS_DIR / 'uno-choice.jpi')
    for name, version in [('jquery3-api','3.7.1-594.vb_3864f326cf0'),('antisamy-markup-formatter','173.v680e3a_b_69ff3')]:
        target = lab.PLUGINS_DIR / (name + '.jpi')
        cached = False
        if target.exists():
            with zipfile.ZipFile(target) as archive:
                manifest = archive.read('META-INF/MANIFEST.MF').decode()
                cached = ('Plugin-Version: ' + version + '\n') in manifest.replace('\r\n','\n')
        if not cached:
            with urllib.request.urlopen('https://updates.jenkins.io/download/plugins/'+name+'/'+version+'/'+name+'.hpi', timeout=30) as response:
                target.write_bytes(response.read())
lab.prepare_plugins = prepare_cached_plugins

init = r'''
import jenkins.model.Jenkins
import hudson.model.FreeStyleProject
import hudson.model.ParametersDefinitionProperty
import hudson.security.SecurityRealm
import hudson.security.AuthorizationStrategy
import org.biouno.unochoice.ChoiceParameter
import org.biouno.unochoice.model.GroovyScript
import org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.SecureGroovyScript
import io.jenkins.plugins.parameterfoundry.migration.activechoices.ActiveChoicesMigrationAdapter
import io.jenkins.plugins.parameterfoundry.validation.DefinitionValidationService

def j=Jenkins.get();j.securityRealm=SecurityRealm.NO_AUTHENTICATION;j.authorizationStrategy=AuthorizationStrategy.UNSECURED;j.numExecutors=2
jenkins.model.JenkinsLocationConfiguration.get().url='http://127.0.0.1:18081/';j.save()
["PT_SINGLE_SELECT","PT_RADIO"].eachWithIndex{type,typeIndex ->
["return ['', 'flags']", "return ['flags', '', 'other']", "return ['flags', '']"].eachWithIndex{text,position ->
 def index=position+typeIndex*3
 def script=new GroovyScript(new SecureGroovyScript(text,true,null),new SecureGroovyScript(text,true,null))
 def old=new ChoiceParameter('NODE','Neutral inline empty option','neutral-empty-'+index,script,type,false,1)
 def target=new ActiveChoicesMigrationAdapter().inspect(old).target
 assert target!=null&&target.standardApiInputMode=='active-choices'&&target.defaultMode==(type=='PT_RADIO'?'none':'first')
 assert DefinitionValidationService.validateStoredDefinition(target).valid
 ['source','target'].each{kind->def job=j.createProject(FreeStyleProject,kind+'-'+index);job.addProperty(new ParametersDefinitionProperty(kind=='source'?old:target));job.save()}
}
}
new File(j.rootDir,'empty-option-ready').text='ready'
'''
path = ROOT / 'init.groovy'
path.write_text(init)
lab.render_init_groovy = lambda: path
factory = ns['FACTORIES'][:ns['FACTORIES'].index('(0..1).each{index ->')]
factory += r'''
(0..5).each{index ->
 def old=j.getItemByFullName('source-'+index).getProperty(ParametersDefinitionProperty).getParameterDefinition('NODE')
 def target=j.getItemByFullName('target-'+index).getProperty(ParametersDefinitionProperty).getParameterDefinition('NODE')
 cases.each{c->def a=invoke(old,c.channel,c.input);def b=invoke(target,c.channel,c.input);rows.add([fixture:index,channel:c.channel,input:c.input,source:a,target:b,equivalent:a.exists==b.exists&&a.literal==b.literal&&a.error==b.error&&a.valueType==b.valueType])}
}
assert rows.every{it.equivalent}
println JsonOutput.toJson([sourceVersion:j.pluginManager.getPlugin('uno-choice').version,targetVersion:j.pluginManager.getPlugin('parameter-foundry').version,rows:rows,businessJobsExecuted:false])
'''

async def main():
    lab.prepare_plugins()
    hpi = lab.PLUGINS_DIR / 'parameter-foundry.jpi'
    digest = hashlib.sha256(hpi.read_bytes()).hexdigest()
    lab.start()
    try:
        deadline = time.monotonic() + 120
        while time.monotonic() < deadline:
            if (lab.HOME_DIR / 'empty-option-ready').exists():
                break
            await asyncio.sleep(1)
        else:
            raise RuntimeError('Neutral fixtures did not initialize')
        jar = ROOT / 'cli.jar'
        with urllib.request.urlopen(lab.BASE_URL + '/jnlpJars/jenkins-cli.jar') as response:
            jar.write_bytes(response.read())
        def call(script):
            p = subprocess.run(['docker','run','--rm','-i','--network','host','-v',str(jar)+':/work/cli.jar:ro',lab.MAVEN_IMAGE,'java','-jar','/work/cli.jar','-s',lab.BASE_URL,'-webSocket','groovy','='],input=script.encode(),capture_output=True,timeout=90)
            (ROOT/'last-cli.stderr').write_bytes(p.stderr)
            assert p.returncode == 0, 'Neutral CLI verification failed; inspect cache stderr'
            return json.loads(p.stdout)
        result = call(factory)
        expected = [['','flags'],['flags','','other'],['flags','']] * 2
        ui = []
        async with async_playwright() as pw:
            browser = await pw.chromium.launch()
            page = await browser.new_page()
            for index in range(6):
                pair = {}
                for kind in ['source','target']:
                    await page.goto(lab.BASE_URL + '/job/'+kind+'-'+str(index)+'/build?delay=0sec')
                    if kind == 'target':
                        await page.wait_for_function("document.querySelector('[data-parameter-name=NODE]')?.dataset.upState==='ready'")
                    if index < 3:
                        selector = '[data-parameter-name=NODE] [data-up-enhanced] select' if kind == 'target' else 'select[name=value]'
                        control = page.locator(selector).first
                        await control.wait_for()
                        options = await control.locator('option').evaluate_all('(nodes)=>nodes.map(n=>({value:n.value,selected:!!n.selected}))')
                        assert [o['value'] for o in options if o['selected']] == [expected[index][0]], options
                        await control.select_option(value='')
                        assert await control.input_value() == ''
                    else:
                        selector = '[data-parameter-name=NODE] input[type=radio][data-up-native-value]' if kind == 'target' else 'input[type=radio][name=NODE]'
                        await page.locator(selector).first.wait_for()
                        options = await page.locator(selector).evaluate_all('(nodes)=>nodes.map(n=>({value:n.value,selected:!!n.checked}))')
                        assert not any(o['selected'] for o in options), options
                        radio = page.locator(selector+'[value=""]')
                        await radio.focus()
                        await page.keyboard.press('Space')
                        assert await radio.is_checked()
                    assert [o['value'] for o in options] == expected[index], options
                    pair[kind] = options
                    await page.get_by_role('button',name='Build',exact=True).click()
                    deadline = time.monotonic() + 60
                    while time.monotonic() < deadline:
                        response = await page.request.get(lab.BASE_URL+'/job/'+kind+'-'+str(index)+'/api/json?tree=lastBuild[number,building,result]')
                        build = (await response.json()).get('lastBuild')
                        if build and not build['building']:
                            assert build['result'] == 'SUCCESS', build
                            break
                        await asyncio.sleep(.5)
                    else:
                        raise RuntimeError('Neutral empty-value form build did not finish')
                assert pair['source'] == pair['target']
                ui.append({'fixture':index,'sourceAndTargetOptionsAndDefaultsMatch':True,'values':pair})
            await page.goto(lab.BASE_URL+'/job/target-0/1/rebuild/parameterized')
            history = page.locator('input[name=value][type=text]').first
            await history.wait_for()
            assert await history.input_value() == ''
            await browser.close()
        builds = call(r'''
import jenkins.model.Jenkins
import hudson.model.ParametersAction
import hudson.EnvVars
import groovy.json.JsonOutput
 def j=Jenkins.get();def checks=[];(0..5).each{index->['source','target'].each{kind->def job=j.getItemByFullName(kind+'-'+index);assert job.builds.size()==1;def b=job.lastBuild;def p=b.getAction(ParametersAction).getParameter('NODE');def env=new EnvVars();p.buildEnvironment(b,env);assert p.class.name=='hudson.model.StringParameterValue'&&p.value==''&&env.containsKey('NODE')&&env.NODE=='';checks.add([fixture:index,kind:kind,value:'',environmentPresent:true,buildResult:b.result.toString()])}};println JsonOutput.toJson(checks)
''')
        result.update(exactHpiSha256=digest,browser=browser.version,ui=ui,neutralFormBuilds=builds,emptyCoreHistoryEditable=True,productionMutations=False)
        RESULT.write_text(json.dumps(result,indent=2)+'\n')
        print(json.dumps({'factoryCasesPassed':len(result['rows']),'browserPairs':len(ui),'neutralEmptyFormBuilds':len(builds),'emptyCoreHistoryEditable':True,'hpiSha256':digest,'businessJobsExecuted':False}))
    finally:
        logs=subprocess.run(['docker','logs',lab.CONTAINER],capture_output=True)
        (ROOT/'controller.log').write_bytes(logs.stdout+logs.stderr)
        lab.stop()

asyncio.run(main())
