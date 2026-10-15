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
    def run(command, label, input_text=None):
        result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True, input=input_text)
        (unit / (label + '.stdout')).write_text(result.stdout)
        (unit / (label + '.stderr')).write_text(result.stderr)
        if result.returncode: raise RuntimeError(label + ' failed; inspect ' + str(unit))
        return result.stdout.strip()
    def peer(path, **values):
        url = 'http://127.0.0.1:18182/' + path
        if values: url += '?' + urllib.parse.urlencode(values)
        command = ['docker', 'exec', owned_ids[name], 'curl', '-fsS']
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
        keytool = shutil.which('keytool')
        assert keytool, 'A JDK keytool is required for owned TLS fixtures.'
        run([keytool,'-genkeypair','-alias','peer','-keyalg','RSA','-keysize','2048','-dname','CN=owned-tls-peer.invalid','-ext','SAN=dns:owned-tls-peer.invalid','-validity','2','-keystore',str(unit/'peer.p12'),'-storetype','PKCS12','-storepass','owned-fixture-pass','-keypass','owned-fixture-pass','-noprompt'], 'owned-keypair')
        run([keytool,'-exportcert','-alias','peer','-keystore',str(unit/'peer.p12'),'-storepass','owned-fixture-pass','-file',str(unit/'peer.cer')], 'owned-certificate')
        run([keytool,'-importcert','-alias','peer','-file',str(unit/'peer.cer'),'-keystore',str(unit/'trust.p12'),'-storetype','PKCS12','-storepass','owned-fixture-pass','-noprompt'], 'owned-truststore')
        with socket.socket() as sock:
            sock.bind(('127.0.0.1', 0)); port = sock.getsockname()[1]
        base = 'http://127.0.0.1:' + str(port)
        owned_ids[network] = run(['docker','network','create','--internal','--label','task=pf-raw-rest-ui',network], 'create-network'); created_network = True
        import ipaddress
        network_info=json.loads(run(['docker','network','inspect',owned_ids[network]],'inspect-owned-network'))[0]
        subnet=next(v['Subnet'] for v in network_info['IPAM']['Config'] if ':' not in v['Subnet'])
        peer_ip=str(ipaddress.IPv4Network(subnet).network_address+2)
        owned_ids[name] = run(['docker','run','-d','--name',name,'--network',network,'--ip',peer_ip,'--add-host','raw-rest-tls.invalid:'+peer_ip,'--add-host','owned-tls-peer.invalid:'+peer_ip,'--user','root','--cpus','2','--memory','4g','-e','JAVA_OPTS=-Djenkins.install.runSetupWizard=false -Djava.security.properties=/opt/pf-lab/dns.security -Djavax.net.ssl.trustStore=/opt/pf-lab/trust.p12 -Djavax.net.ssl.trustStorePassword=owned-fixture-pass','-e','PF_LAB_URL='+base+'/','-e','PF_LAB_HPI_SHA='+digest,'-v',str(unit/'peer.p12')+':/opt/pf-lab/peer.p12:ro','-v',str(unit/'trust.p12')+':/opt/pf-lab/trust.p12:ro','-v',str(home)+':/var/jenkins_home','-v',str(plugins)+':/usr/share/jenkins/ref/plugins:ro','-v',str(Path(__file__).with_name('raw-rest-dns.security'))+':/opt/pf-lab/dns.security:ro','-v',str(Path(__file__).with_name('raw-rest-provider-init.groovy'))+':/usr/share/jenkins/ref/init.groovy.d/01-raw.groovy:ro',image], 'start-controller'); created_container = True
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
        blocked=[]; errors=[]; results=[]; core_errors=[]; core_probe=False
        async with async_playwright() as pw:
            browser = await pw.chromium.launch()
            context = await browser.new_context(viewport={'width':1440,'height':1000})
            async def protect(route):
                if route.request.method=='POST' and any(x in urllib.parse.urlsplit(route.request.url).path for x in ['/build','/rebuild']):
                    blocked.append(route.request.url); await route.abort()
                else: await route.continue_()
            await context.route('**/*', protect)
            page = await context.new_page(); page.on('pageerror', lambda error: (core_errors if core_probe else errors).append({'url':page.url,'viewport':page.viewport_size,'message':str(error),'stack':error.stack}))
            async def set_viewport(size):
                # Each platform case starts at its viewport; resizing a Core configuration page
                # can hit the separately reproduced Core breadcrumb-overflow defect.
                await page.goto('about:blank',wait_until='load')
                await page.set_viewport_size(size)
            async def save_configuration():
                await page.get_by_role('button',name='Save',exact=True).click()
                await page.wait_for_url(lambda url: urllib.parse.urlsplit(str(url)).path.rstrip('/')!='/configure',wait_until='networkidle')
            await page.goto(base+'/configure',wait_until='networkidle')
            modes=page.locator('select[name="_.responseMode"]:visible'); assert await modes.count()==8
            assert await modes.evaluate_all('(xs)=>xs.map(x=>x.value)')==['active-choices-raw-scalar']*8
            policies=page.locator('select[name="_.rawStatusPolicy"]:visible')
            assert await policies.evaluate_all('(xs)=>xs.map(x=>x.value)')==['http-status']*8
            await policies.nth(6).select_option('native-nonredirect-body')
            await policies.nth(6).scroll_into_view_if_needed()
            await page.screenshot(path=str(unit/'raw-status-policy-configuration-desktop.png'))
            values=await page.locator('textarea[name="_.value"]:visible').evaluate_all('(xs)=>xs.map(x=>x.value)')
            assert 'null' in values and 'topic/stable' in values and 'HTTP {status}: {uri}' in values and 'DNS unavailable' in values
            assert await page.locator('select[name="_.kind"]:visible').evaluate_all('(xs)=>xs.map(x=>x.value)')==['HTTP_STATUS','DNS_UNRESOLVED','TLS_REJECTED','TLS_REJECTED','HTTP_STATUS','HTTP_STATUS','HTTP_STATUS','HTTP_STATUS','REDIRECT_LIMIT']
            fingerprints=await page.locator('input[name="_.fingerprint"]:visible').evaluate_all('(xs)=>xs.map(x=>x.value)')
            assert metadata['tlsFingerprint'] in fingerprints and '0'*64 in fingerprints
            # Oversized full-page captures temporarily switch Chromium to a 1x1 viewport,
            # triggering Core's independent breadcrumb defect. Capture the real field viewport.
            await page.screenshot(path=str(unit/'configuration-desktop.png'))
            await page.locator('input[name="_.fingerprint"]:visible').nth(2).scroll_into_view_if_needed()
            await page.screenshot(path=str(unit/'tls-fingerprint-configuration-desktop.png'))
            redirect_field=None
            for field in await page.locator('textarea[name="_.value"]:visible').all():
                if await field.input_value()==metadata['peer']+'staged':
                    assert redirect_field is None
                    redirect_field=field
            assert redirect_field is not None
            await redirect_field.fill(metadata['peer']+'final')
            await redirect_field.scroll_into_view_if_needed()
            await page.screenshot(path=str(unit/'raw-redirect-permission-configuration-desktop.png'))
            await save_configuration()
            await page.goto(base+'/configure',wait_until='networkidle')
            assert metadata['peer']+'final' in await page.locator('textarea[name="_.value"]:visible').evaluate_all('(xs)=>xs.map(x=>x.value)')
            assert metadata['peer']+'final' in await page.locator('input[name="_.matchUri"]:visible').evaluate_all('(xs)=>xs.map(x=>x.value)')
            rate_waits=[]
            async def respect_rate_budget():
                budget=peer('rate-budget')
                if budget['maximumPrincipalCount'] > budget['principalLimit']-8:
                    rate_waits.append(budget)
                    await asyncio.sleep(min(60,budget['waitMillis']/1000))
            async def read_form(job, expected, selected, state="ready"):
                await respect_rate_budget()
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
                await set_viewport({'width':width,'height':1000 if width==1440 else 844})
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
                for code in [302,304,306,308]:
                    peer('control',status=code,body='topic/native-body\n')
                    await read_form('status-old',['reviewed-http-error','topic/stable'],'reviewed-http-error')
                    old=peer('native-default',job='status-old')
                    assert old['value']=='reviewed-http-error' and old['rawStatusPolicy']=='http-status'
                    expected='' if code==304 else 'topic/native-body\n'
                    await read_form('status-body',[expected,'topic/stable'],expected)
                    native=peer('native-default',job='status-body')
                    assert native['value']==expected and native['type']=='hudson.model.StringParameterValue' and native['rawStatusPolicy']=='native-nonredirect-body'
                    results.append({'width':width,'case':'status-'+str(code),'matched':True})
                    await page.screenshot(path=str(unit/('raw-status-'+str(code)+'-'+str(width)+'.png')),full_page=True)
            for width in [1440,390]:
                await set_viewport({'width':width,'height':1000 if width==1440 else 844})
                for code in [300,301,302,303,307]:
                    peer('control',status=200,body='topic/redirected',redirectCode=code,redirectLocation='/api/final')
                    before=peer('stats')
                    assert peer('native-source')['value']=='topic/redirected'
                    await read_form('raw-redirect',['topic/redirected','topic/stable'],'topic/redirected')
                    native=peer('native-default',job='raw-redirect')
                    assert native['value']=='topic/redirected' and native['type']=='hudson.model.StringParameterValue'
                    after=peer('stats')
                    assert after['redirectTargets']>before['redirectTargets']
                    assert after['redirectRequests']-before['redirectRequests']==after['redirectTargets']-before['redirectTargets']
                    results.append({'width':width,'case':'redirect-'+str(code),'matched':True})
                peer('control',status=503,redirectCode=302)
                final_error='Server returned HTTP response code: 503 for URL: '+metadata['peer']+'final'
                assert peer('native-source')['value']==final_error
                await read_form('raw-redirect',[final_error,'topic/stable'],final_error)
                assert peer('native-default',job='raw-redirect')['value']==final_error
                results.append({'width':width,'case':'redirect-final-http-failure','matched':True})
                await page.screenshot(path=str(unit/('raw-redirect-final-failure-'+str(width)+'.png')),full_page=True)
                peer('control',status=200,redirectLocation='/api/not-declared')
                before=peer('stats')
                await respect_rate_budget()
                await page.goto(base+'/job/raw-redirect/build?delay=0sec',wait_until='networkidle')
                await page.wait_for_function('document.querySelector("[data-parameter-name=REF]").dataset.upState==="error"')
                assert peer('native-default',job='raw-redirect')['value'] is None
                after=peer('stats')
                assert after['redirectTargets']==before['redirectTargets']
                assert before['deniedRedirectRequests']==after['deniedRedirectRequests']==0
                results.append({'width':width,'case':'redirect-undeclared-denied','matched':True})
                await page.screenshot(path=str(unit/('raw-redirect-denied-'+str(width)+'.png')),full_page=True)
                peer('control',status=200,body='topic/redirect-recovered',redirectLocation='/api/final')
                await read_form('raw-redirect',['topic/redirect-recovered','topic/stable'],'topic/redirect-recovered')
                assert peer('native-default',job='raw-redirect')['value']=='topic/redirect-recovered'
                results.append({'width':width,'case':'redirect-recovery','matched':True})
            await set_viewport({'width':1440,'height':1000})
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
            await read_form('raw-rest-dns',['DNS unavailable','topic/stable'],'DNS unavailable')
            assert peer('native-default',job='raw-rest-dns')['value']=='DNS unavailable'
            await page.screenshot(path=str(unit/'dns-unresolved-desktop.png'),full_page=True)
            assert run(['docker','inspect','--format','{{.Id}}',name], 'dns-recovery-owner')==owned_ids[name]
            peer_host=urllib.parse.urlsplit(metadata['peer']).hostname
            run(['docker','exec','-i',owned_ids[name],'tee','-a','/etc/hosts'], 'restore-owned-dns',peer_host+' raw-rest-dns.invalid\n')
            await read_form('raw-rest-dns',['topic/recovered\n','topic/stable'],'topic/recovered\n')
            recovered=peer('native-default',job='raw-rest-dns');assert recovered['value']=='topic/recovered\n' and recovered['type']=='hudson.model.StringParameterValue'
            await page.screenshot(path=str(unit/'dns-recovered-desktop.png'),full_page=True)
            for width in [1440,390]:
                await set_viewport({'width':width,'height':1000 if width==1440 else 844})
                await read_form('raw-rest-tls',['TLS unavailable','topic/stable'],'TLS unavailable')
                assert peer('native-default',job='raw-rest-tls')['value']=='TLS unavailable'
                await page.screenshot(path=str(unit/('tls-declared-'+str(width)+'.png')),full_page=True)
                await page.goto(base+'/job/raw-tls-denied/build?delay=0sec',wait_until='networkidle')
                await page.wait_for_function('document.querySelector("[data-parameter-name=REF]").dataset.upState==="error"')
                field=page.locator('[data-parameter-name="REF"]')
                text=await field.inner_text()
                status=field.locator('[data-up-refresh-status]')
                diagnostic=await field.evaluate('(node)=>({html:node.outerHTML,statuses:Array.from(node.querySelectorAll("[data-up-refresh-status]")).map(s=>({text:s.textContent,display:getComputedStyle(s).display,visibility:getComputedStyle(s).visibility,rect:s.getBoundingClientRect().toJSON()}))})')
                (unit/('tls-unknown-dom-'+str(width)+'.json')).write_text(json.dumps(diagnostic,indent=2))
                assert await status.count()==1 and await status.is_visible()
                assert 'Options could not be enhanced:' in await status.inner_text()
                await status.evaluate('(node)=>node.scrollIntoView({block:"center"})')
                assert await status.evaluate('(node)=>{const r=node.getBoundingClientRect();return node.contains(document.elementFromPoint(r.left+r.width/2,r.top+r.height/2))}')

                assert metadata['tlsFingerprint'] not in text and 'certificate_unknown' not in text and 'raw-rest-tls.invalid' not in text
                assert peer('native-default',job='raw-tls-denied')['value'] is None
                await page.screenshot(path=str(unit/('tls-unknown-'+str(width)+'.png')),full_page=True)
            await set_viewport({'width':1440,'height':1000})
            await page.goto(base+'/configure',wait_until='networkidle')
            tls_base=page.locator('input[name="_.baseUrl"]:visible')
            selected=None
            for field in await tls_base.all():
                if await field.input_value()==metadata['tlsPeer']:
                    assert selected is None
                    selected=field
            assert selected is not None
            await selected.fill(metadata['validTlsPeer'])
            await save_configuration()
            for job in ['raw-rest-tls','raw-tls-denied']:
                await read_form(job,['topic/recovered\n','topic/stable'],'topic/recovered\n')
                native=peer('native-default',job=job);assert native['value']=='topic/recovered\n' and native['type']=='hudson.model.StringParameterValue'
            await page.screenshot(path=str(unit/'tls-valid-reconfigured-desktop.png'),full_page=True)
            # Jenkins 2.568.3 breadcrumbs pass `label` to a Core template expecting `displayName`.
            # Reproduce separately on a parameter-free Core Job; never suppress product-page errors.
            core_probe=True
            await set_viewport({'width':390,'height':844})
            await page.goto(base+'/job/plain-core-breadcrumb-control/',wait_until='networkidle')
            assert await page.locator('[data-parameter-foundry]').count()==0
            await page.wait_for_timeout(300)
            await page.screenshot(path=str(unit/'core-breadcrumb-control-mobile.png'),full_page=True)
            core_probe=False
            assert core_errors and all(e['message']=="Cannot read properties of undefined (reading 'replace')" and '/jsbundles/header.js:' in e['stack'] and 'generateDropdownItems' in e['stack'] for e in core_errors),core_errors
            (unit/'core-breadcrumb-control.json').write_text(json.dumps({'state':'CORE_ONLY_BREADCRUMB_OVERFLOW_REPRODUCED','foundryFields':0,'errors':core_errors},indent=2))
            (unit/'page-errors.json').write_text(json.dumps({'blocked':blocked,'errors':errors},indent=2))
            assert not blocked and not errors,(blocked,errors)
            stats=peer('stats'); assert stats['jobs']==9 and stats['builds']==0 and stats['queue']==0
            result={'state':'RAW_REST_BROWSER_GATE_PASS','artifact':metadata,'browser':browser.version,'cases':results,'configurationSaved':True,'nonredirectStatusPolicyConfigurationSaved':True,'nonredirectStatusPolicyCases':8,'defaultHttpStatusMappingsPreserved':True,'mappedErrorAndFallbackRecoveryPassed':True,'keyboardSelectionPassed':True,'buildFormsSubmitted':0,'declaredTlsAndUnknownRejectionPassed':True,'tlsValidOriginReconfigurationPassed':True,'declaredDnsAndSameProfileRecoveryPassed':True,'dnsFixtureCachePolicy':'Owned JVM positive/negative DNS TTL0; production unchanged','coreBreadcrumbOverflowControl':'Separately reproduced on parameter-free Core Job; product fixtures use shorter names and retain strict pageerror checks','businessBuildsScheduled':0,'stats':stats,'dependencyReceipt':'dependency-receipt.json'}
            result['finiteRedirectConfigurationSaved']=True
            result['finiteRedirectCases']=16
            result['ownedNativeRedirectSourcePairs']=12
            result['rateBudgetWaits']=rate_waits
            result['rateBudgetPolicy']='Read-only owned runtime budget observation and natural window waits; no limit changes or resets'
            result['configurationScreenshotScope']='Actual1440x1000 field viewport; oversized full-page capture is excluded after independently recording its1x1 Core header trigger'
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
