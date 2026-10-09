#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = ["playwright>=1.55,<2"]
# ///
"""Disposable browser gate for legacy static choice transport compatibility."""
import argparse
import asyncio
import importlib.util
import json
import platform
import re
import time
import urllib.request
from pathlib import Path
from playwright.async_api import async_playwright

ROOT=Path(__file__).resolve().parents[3]
spec=importlib.util.spec_from_file_location('foundry_e2e',ROOT/'tools/e2e/run-e2e.py')
lab=importlib.util.module_from_spec(spec)
spec.loader.exec_module(lab)
lab.CONTAINER='parameter-foundry-extended-choice-transport-lab'
lab.HOME_DIR=ROOT/'tools/ui-lab/.cache/extended-choice-transport-home'
lab.render_init_groovy=lambda: Path(__file__).with_name('extended-choice-transport-init.groovy')

def wait_ready():
    deadline=time.monotonic()+120
    while time.monotonic()<deadline:
        try:
            with urllib.request.urlopen(lab.BASE_URL+'/job/extended-choice-transport/api/json',timeout=3) as response:
                if response.status==200 and (lab.HOME_DIR/'extended-choice-transport-ready').exists():return
        except OSError:pass
        time.sleep(1)
    raise RuntimeError('Transport browser lab did not become ready')

lab.wait_ready=wait_ready

async def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--skip-build',action='store_true')
    if parser.parse_args().skip_build:lab.build_artifacts=lambda:None
    lab.prepare_plugins()
    lab.start()
    try:
        lab.wait_ready()
        async with async_playwright() as pw:
            browser=await pw.chromium.launch()
            page=await browser.new_page()
            base=lab.BASE_URL+'/job/extended-choice-transport'
            async def result(number,expected,job_base=None):
                result_base=job_base or base
                for _ in range(120):
                    response=await page.request.get(result_base+f'/{number}/api/json?tree=building,result')
                    if response.ok:
                        body=await response.json()
                        if not body['building']:
                            assert body['result']=='SUCCESS',body
                            break
                    await asyncio.sleep(.5)
                else:raise AssertionError('Contract build timed out')
                response=await page.request.get(result_base+f'/{number}/artifact/result.txt')
                assert response.ok and await response.text()==expected
            await page.goto(base+'/configure')
            mode=page.locator('select[name="parameter.standardApiInputMode"]')
            assert await mode.count()==1
            await mode.select_option('extended-choice')
            await page.get_by_role('button',name='Save',exact=True).click()
            await page.goto(base+'/build?delay=0sec')
            field=page.locator('[data-parameter-name="TARGETS"]')
            await field.locator('[data-up-enhanced]').wait_for(state='visible')
            await field.locator('[data-up-enhanced] label').filter(has_text=re.compile(r'^\s*alpha\s*$')).first.click()
            await field.locator('[data-up-enhanced] label').filter(has_text=re.compile(r'^\s*beta\s*$')).first.click()
            await page.get_by_role('button',name='Build',exact=True).click()
            await result(1,'alpha,beta')
            body={'parameter':[{'name':'TARGETS','value':'beta,alpha,alpha'}]}
            response=await page.request.post(base+'/build?delay=0sec',form={'json':json.dumps(body)})
            assert response.status in [200,201,302], (response.status, (await response.text())[:1000])
            await result(2,'beta,alpha,alpha')
            response=await page.request.post(base+'/buildWithParameters?delay=0sec',form={'TARGETS':'beta,alpha'})
            assert response.status in [200,201,302], (response.status, (await response.text())[:1000])
            await result(3,'')
            literal_form=' beta ,unknown,alpha,alpha '
            response=await page.request.post(base+'/build?delay=0sec',form={'json':json.dumps({'parameter':[{'name':'TARGETS','value':literal_form}]})})
            assert response.status in [200,201,302],response.status
            await result(4,literal_form)
            mixed=[42,True,None,'beta','','unknown']
            response=await page.request.post(base+'/build?delay=0sec',form={'json':json.dumps({'parameter':[{'name':'TARGETS','value':mixed}]})})
            assert response.status in [200,201,302],response.status
            await result(5,'42,true,null,beta,,unknown')
            await page.goto(base+'/4/rebuild/parameterized')
            historical=page.locator('input[name="value"]')
            assert await historical.input_value()==literal_form
            await historical.fill('gamma,unknown,gamma')
            await page.get_by_role('button',name='Rebuild',exact=True).click()
            await result(6,'gamma,unknown,gamma')
            default_base=lab.BASE_URL+'/job/extended-choice-default'
            literal=' gamma,unknown,alpha,gamma '
            await page.goto(default_base+'/configure')
            literal_field=page.locator('input[name="parameter.legacyDefaultLiteral"]')
            assert await literal_field.input_value()==literal
            await page.get_by_role('button',name='Save',exact=True).click()
            await page.goto(default_base+'/configure')
            assert await literal_field.input_value()==literal
            await page.goto(default_base+'/build?delay=0sec')
            default_field=page.locator('[data-parameter-name="TARGETS"]')
            await default_field.locator('[data-up-enhanced]').wait_for(state='visible')
            selected=await default_field.locator('[data-up-enhanced] input[type="checkbox"]:checked').evaluate_all('(inputs) => inputs.map(input => input.value).sort()')
            assert selected==['alpha','gamma'],selected
            assert await default_field.locator('label').filter(has_text='unknown').count()==0
            await page.get_by_role('button',name='Build',exact=True).click()
            await result(1,'alpha,gamma',default_base)
            response=await page.request.post(default_base+'/buildWithParameters?delay=0sec')
            assert response.status in [200,201,302],response.status
            await result(2,literal,default_base)
            single_checks=[]
            async def single_field(name):
                job_base=lab.BASE_URL+'/job/'+name
                await page.goto(job_base+'/build?delay=0sec')
                field=page.locator('[data-parameter-name="SINGLE"]')
                try:
                    await page.wait_for_function("document.querySelector('[data-parameter-name=SINGLE]').dataset.upState === 'ready'")
                    if await field.get_attribute('data-renderer')!='radio':
                        await field.locator('[data-up-enhanced]').wait_for(state='visible')
                except Exception:
                    diagnostics=ROOT/'tools/ui-lab/.cache'
                    diagnostics.mkdir(parents=True,exist_ok=True)
                    (diagnostics/'extended-single-failure.html').write_text(await page.content())
                    await page.screenshot(path=str(diagnostics/'extended-single-failure.png'),full_page=True)
                    raise
                return job_base,field
            single_base,single=await single_field('extended-single-unknown')
            assert await single.locator('[data-up-enhanced] select').input_value()=='alpha'
            assert await single.locator('[data-up-enhanced] option[value=""]').count()==0
            await page.get_by_role('button',name='Build',exact=True).click()
            await result(1,'alpha',single_base)
            response=await page.request.post(single_base+'/buildWithParameters?delay=0sec')
            assert response.status in [200,201,302],response.status
            await result(2,'unlisted-default',single_base)
            single_checks.append('single UI first option and omitted literal default are independent')
            quoted='<literal "quoted">,unknown'
            response=await page.request.post(single_base+'/build?delay=0sec',form={'json':json.dumps({'parameter':[{'name':'SINGLE','value':quoted}]})})
            assert response.status in [200,201,302],response.status
            await result(3,quoted,single_base)
            await page.goto(single_base+'/3/rebuild/parameterized')
            assert await page.locator('input[name="value"]').input_value()==quoted
            await page.get_by_role('button',name='Rebuild',exact=True).click()
            await result(4,quoted,single_base)
            single_checks.append('single Rebuild preserves quoted HTML literal as text')
            _,single=await single_field('extended-single-last')
            assert await single.locator('[data-up-enhanced] select').input_value()=='beta'
            single_checks.append('single last matching DOM option is selected')
            _,radio=await single_field('extended-radio-empty')
            assert await radio.locator('input[type="radio"]:checked').count()==0
            assert await radio.locator('input[type="radio"][value=""]').count()==0
            single_checks.append('radio without default stays unselected without added None')
            _,radio=await single_field('extended-radio-last')
            assert await radio.locator('input[type="radio"]:checked').input_value()=='beta'
            single_checks.append('radio last matching DOM option is selected')
            _,single=await single_field('extended-single-long')
            assert await single.locator('[data-up-enhanced] option').count()==240
            assert 'showing 200' not in await single.inner_text()
            single_checks.append('static single retains all 240 options without truncation notice')
            empty_base=lab.BASE_URL+'/job/extended-choice-empty-transport'
            await page.goto(empty_base+'/configure')
            empty_flag=page.locator('input[name="parameter.legacyEmptyChoiceAllowed"]')
            assert await empty_flag.is_checked()
            await page.get_by_role('button',name='Save',exact=True).click()
            await page.goto(empty_base+'/configure')
            assert await empty_flag.is_checked()
            await page.goto(empty_base+'/build?delay=0sec')
            empty_field=page.locator('[data-parameter-name="TARGETS"]')
            await empty_field.locator('[data-up-enhanced]').wait_for(state='visible')
            visible_values=await empty_field.locator('[data-up-enhanced] input[type="checkbox"]').evaluate_all('(inputs) => inputs.map(input => input.value)')
            assert visible_values==['alpha','beta'],visible_values
            response=await page.request.post(empty_base+'/buildWithParameters?delay=0sec',data='TARGETS=&TARGETS=alpha&TARGETS=',headers={'Content-Type':'application/x-www-form-urlencoded'})
            assert response.status in [200,201,302],response.status
            await result(1,',alpha,',empty_base)
            await page.goto(empty_base+'/1/rebuild/parameterized')
            assert await page.locator('input[name="value"]').input_value()==',alpha,'
            await page.get_by_role('button',name='Rebuild',exact=True).click()
            await result(2,',alpha,',empty_base)
            no_js=await browser.new_context(java_script_enabled=False)
            fallback_page=await no_js.new_page()
            await fallback_page.goto(empty_base+'/build?delay=0sec')
            native=fallback_page.locator('[data-parameter-name="TARGETS"] [data-up-native-value]')
            assert await native.evaluate_all('(inputs) => inputs.map(input => input.value)')==['alpha','beta']
            await native.locator('xpath=self::*[@value="alpha"]').check()
            await fallback_page.get_by_role('button',name='Build without JavaScript',exact=True).click()
            await result(3,'alpha',empty_base)
            await no_js.close()
            single_checks.append('no-JavaScript fallback omits blank choice and submits supported value')
            single_checks.extend(['empty transport flag survives configuration save','empty transport adds no visible blank option','repeated HTTP empty fields retain delimiter positions','Rebuild preserves empty token positions'])
            report={'passed':True,'browser':browser.version,'hostPlatform':platform.system(),'viewport':page.viewport_size,'jenkinsImage':lab.JENKINS_IMAGE,'checks':['compatibility configuration save','browser array submission','legacy delimited form JSON with repeated export','standard combined field remains unexpanded','literal default configuration preserves whitespace','UI selects supported defaults only','native default submission exports selected options','omitted standard input exports exact literal','unlisted form string retains whitespace and repetitions','mixed form array retains scalar and empty elements','Rebuild retains and edits historical literal']+single_checks}
            (ROOT/'docs/testing/results/extended-choice-transport-ui-lab.json').write_text(json.dumps(report,indent=2)+'\n')
            print(json.dumps(report))
            await browser.close()
    finally:lab.stop()

if __name__=='__main__':asyncio.run(main())
