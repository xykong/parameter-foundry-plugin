#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = ["playwright==1.63.0"]
# ///
"""Owned internal-network Jenkins/browser gate; never loads production data or submits builds."""
import argparse
import asyncio
import hashlib
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import time
import urllib.parse
import urllib.request
import zipfile
import re
import sys
from playwright.async_api import async_playwright

ROOT = Path(__file__).resolve().parents[3]

async def main():
    args = argparse.ArgumentParser()
    args.add_argument('--hpi', type=Path, required=True)
    args.add_argument('--dependencies', type=Path, default=ROOT/'plugin/target/test-classes/test-dependencies')
    options = args.parse_args()
    artifact = options.hpi.resolve(strict=True)
    digest = hashlib.sha256(artifact.read_bytes()).hexdigest()
    unit = ROOT / 'tools/ui-lab/.cache' / ('raw-rest-' + str(time.time_ns()))
    unit.mkdir(mode=0o700, parents=True)
    plugins = unit / 'plugins'; plugins.mkdir()
    home = unit / 'home'; home.mkdir()
    name = 'pf-raw-rest-ui-' + str(os.getpid())
    network = name + '-net'
    relay = name + '-relay'
    image = 'jenkins/jenkins:2.568.3-lts-jdk21'
    created_network = created_container = created_relay = False
    owned_ids = {}
    def run(command, label):
        result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True)
        (unit / (label + '.stdout')).write_text(result.stdout)
        (unit / (label + '.stderr')).write_text(result.stderr)
        if result.returncode: raise RuntimeError(label + ' failed; inspect ' + str(unit))
        return result.stdout.strip()
    def peer(path, **values):
        url = 'http://127.0.0.1:18182/' + path
        if values: url += '?' + urllib.parse.urlencode(values)
        command = ['docker', 'exec', name, 'curl', '-fsS']
        if path == 'control': command += ['-X', 'POST']
        return json.loads(run(command + [url], 'peer-' + str(time.time_ns())))
    try:
        dependencies = options.dependencies.resolve(strict=True)
        dependency_receipt = []
        for plugin_id in (dependencies/'index').read_text().splitlines():
            assert re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]*',plugin_id) and '..' not in plugin_id
            source=dependencies/(plugin_id+'.hpi')
            if not source.is_file(): source=dependencies/(plugin_id+'.jpi')
            assert source.is_file(),source
            shutil.copy2(source,plugins/(plugin_id+'.jpi'))
            dependency_receipt.append({'id':plugin_id,'sha256':hashlib.sha256(source.read_bytes()).hexdigest()})
        (unit/'dependency-receipt.json').write_text(json.dumps(dependency_receipt,indent=2))
        shutil.copy2(artifact, plugins/'parameter-foundry.jpi')
        with socket.socket() as sock:
            sock.bind(('127.0.0.1', 0)); port = sock.getsockname()[1]
        base = 'http://127.0.0.1:' + str(port)
        owned_ids[network] = run(['docker','network','create','--internal','--label','task=pf-raw-rest-ui',network], 'create-network'); created_network = True
        owned_ids[name] = run(['docker','run','-d','--name',name,'--network',network,'--user','root','--cpus','2','--memory','4g','-e','JAVA_OPTS=-Djenkins.install.runSetupWizard=false','-e','PF_LAB_URL='+base+'/','-e','PF_LAB_HPI_SHA='+digest,'-v',str(home)+':/var/jenkins_home','-v',str(plugins)+':/usr/share/jenkins/ref/plugins:ro','-v',str(Path(__file__).with_name('raw-rest-provider-init.groovy'))+':/usr/share/jenkins/ref/init.groovy.d/01-raw.groovy:ro',image], 'start-controller'); created_container = True
        # Internal Docker networks do not publish host ports on every engine.
        # Only the fixed reverse proxy has a bridge route; Jenkins stays internal.
        relay_config = unit/'relay.conf'
        relay_config.write_text('events {}\nhttp { server { listen 8080; location / { proxy_pass http://'+name+':8080; proxy_set_header Host $http_host; proxy_set_header X-Forwarded-Proto http; } } }\n')
        owned_ids[relay] = run(['docker','create','--pull','never','--name',relay,'--network','bridge','--cpus','0.5','--memory','128m','-p','127.0.0.1:'+str(port)+':8080','-v',str(relay_config)+':/etc/nginx/nginx.conf:ro','nginx:1.30.5-alpine'], 'create-relay'); created_relay = True
        run(['docker','network','connect',network,relay], 'connect-relay')
        run(['docker','start',relay], 'start-relay')
        deadline = time.monotonic()+180
        while time.monotonic()<deadline:
            state = run(['docker','inspect','--format','{{.State.Status}}',name], 'startup-state')
            if state in {'exited', 'dead'}:
                raise RuntimeError('Owned raw REST controller exited during startup; inspect ' + str(unit))
            try:
                with urllib.request.urlopen(base+'/api/json',timeout=3) as response: ready=response.status==200
                if ready and (home/'raw-rest-ready.json').is_file(): break
            except OSError: pass
            await asyncio.sleep(1)
        else: raise RuntimeError('Owned raw REST controller startup failed')
        metadata = json.loads((home/'raw-rest-ready.json').read_text()); assert metadata['sha256']==digest
        blocked=[]; errors=[]; results=[]
        async with async_playwright() as pw:
            browser = await pw.chromium.launch()
            context = await browser.new_context(viewport={'width':1440,'height':1000})
            async def protect(route):
                if route.request.method=='POST' and any(x in urllib.parse.urlsplit(route.request.url).path for x in ['/build','/rebuild']):
                    blocked.append(route.request.url); await route.abort()
                else: await route.continue_()
            await context.route('**/*', protect)
            page = await context.new_page(); page.on('pageerror', lambda error: errors.append(str(error)))
            await page.goto(base+'/configure',wait_until='networkidle')
            modes=page.locator('select[name="_.responseMode"]:visible'); assert await modes.count()==2
            assert await modes.evaluate_all('(xs)=>xs.map(x=>x.value)')==['active-choices-raw-scalar']*2
            values=await page.locator('textarea[name="_.value"]:visible').evaluate_all('(xs)=>xs.map(x=>x.value)')
            assert 'null' in values and 'topic/stable' in values and 'HTTP {status}: {uri}' in values
            await page.screenshot(path=str(unit/'configuration-desktop.png'),full_page=True)
            await page.get_by_role('button',name='Save',exact=True).click(); await page.wait_for_load_state('networkidle')
            async def read_form(job, expected, selected, state="ready"):
                await page.goto(base+'/job/'+job+'/build?delay=0sec',wait_until='networkidle')
                field=page.locator('[data-parameter-name="REF"]')
                await page.wait_for_function('(state)=>document.querySelector("[data-parameter-name=REF]").dataset.upState===state',arg=state)
                select=field.locator('[data-up-enhanced] select'); await select.wait_for(state='visible')
                assert await field.get_attribute('data-active-choices-filter-mode')=='none'
                assert await field.locator('[data-up-enhanced] input[type=search]:visible').count()==0
                actual=await select.locator('option').evaluate_all('(xs)=>xs.map(x=>x.value)')
                assert actual==expected,(job,actual,expected)
                assert await select.input_value()==selected
                return select
            for width in [1440,390]:
                await page.set_viewport_size({'width':width,'height':1000 if width==1440 else 844})
                for index,(body,expected,selected,api) in enumerate([
                    ('topic/test\n',['topic/test\n','topic/stable'],'topic/test\n','topic/test\n'),
                    ('{"data":"literal"}',['{"data":"literal"}','topic/stable'],'{"data":"literal"}','{"data":"literal"}'),
                    ('null',['topic/stable'],'topic/stable','topic/stable'),
                    ('',['','topic/stable'],'',''),
                    ('   ',['','topic/stable'],'','   '),
                    ('topic/stable',['topic/stable'],'topic/stable','topic/stable'),
                ]):
                    peer('control',status=200,body=body); select=await read_form('raw-rest-values',expected,selected)
                    native=peer('native-default',job='raw-rest-values'); assert native['value']==api and native['type']=='hudson.model.StringParameterValue'
                    results.append({'width':width,'case':index,'matched':True})
                await page.screenshot(path=str(unit/('choices-'+str(width)+'.png')),full_page=True)
            await page.set_viewport_size({'width':1440,'height':1000})
            peer('control',status=503)
            error='HTTP 503: '+metadata['peer']+'value'
            await read_form('raw-rest-values',[error,'topic/stable'],error)
            await read_form('raw-rest-fallback',['topic/fallback'],'topic/fallback',state='fallback')
            peer('control',status=200,body='topic/recovered\n')
            select=await read_form('raw-rest-fallback',['topic/recovered\n','topic/stable'],'topic/recovered\n')
            await select.focus()
            keyboard_values = [{'step':'focus','value':await select.input_value()}]
            # Native popup arrows are not implemented by macOS headless Chromium;
            # typeahead is a native keyboard selection path without form submission.
            await select.press_sequentially('topic/stable',delay=30)
            keyboard_values.append({'step':'typeahead topic/stable','value':await select.input_value()})
            (unit/'keyboard.json').write_text(json.dumps(keyboard_values,indent=2))
            await page.screenshot(path=str(unit/'recovery-keyboard.png'),full_page=True)
            assert await select.input_value()=='topic/stable',keyboard_values
            assert not blocked and not errors,(blocked,errors)
            stats=peer('stats'); assert stats['jobs']==2 and stats['builds']==0 and stats['queue']==0
            result={'state':'RAW_REST_BROWSER_GATE_PASS','artifact':metadata,'browser':browser.version,'cases':results,'configurationSaved':True,'mappedErrorAndFallbackRecoveryPassed':True,'keyboardSelectionPassed':True,'formsSubmitted':0,'businessBuildsScheduled':0,'stats':stats,'dependencyReceipt':'dependency-receipt.json'}
            (unit/'result.json').write_text(json.dumps(result,indent=2)); print(json.dumps({'state':result['state'],'result':str(unit/'result.json'),'cases':len(results),'sha256':digest}))
            await browser.close()
    finally:
        primary_error = sys.exc_info()[1]
        cleanup_errors = []
        for resource, created, is_network in [(relay, created_relay, False), (name, created_container, False), (network, created_network, True)]:
            if not created:
                continue
            prefix = ['docker', 'network'] if is_network else ['docker']
            inspection = subprocess.run(prefix + ['inspect', '--format', '{{.Id}}', resource], capture_output=True, text=True)
            if inspection.returncode or inspection.stdout.strip() != owned_ids[resource]:
                cleanup_errors.append(resource + ': ownership inspection failed; no removal attempted')
                continue
            if not is_network:
                logs = subprocess.run(['docker', 'logs', owned_ids[resource]], capture_output=True, text=True)
                (unit/(resource+'-cleanup.stdout')).write_text(logs.stdout)
                (unit/(resource+'-cleanup.stderr')).write_text(logs.stderr)
                if logs.returncode:
                    cleanup_errors.append(resource + ': log collection failed')
            command = prefix + ['rm'] + ([] if is_network else ['-f']) + [owned_ids[resource]]
            removed = subprocess.run(command, capture_output=True, text=True)
            (unit/(resource+'-remove.stdout')).write_text(removed.stdout)
            (unit/(resource+'-remove.stderr')).write_text(removed.stderr)
            if removed.returncode:
                cleanup_errors.append(resource + ': removal failed')
        (unit/'cleanup.json').write_text(json.dumps({'errors':cleanup_errors,'ownedIds':owned_ids},indent=2))
        if cleanup_errors:
            details = '; '.join(cleanup_errors)
            if primary_error is not None:
                primary_error.add_note('Cleanup errors: ' + details)
            else:
                raise RuntimeError('Cleanup errors: ' + details)

asyncio.run(main())
