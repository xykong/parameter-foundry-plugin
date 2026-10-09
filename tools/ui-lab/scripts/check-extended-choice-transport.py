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
            report={'passed':True,'browser':browser.version,'checks':['compatibility configuration save','browser array submission','legacy delimited form JSON with repeated export','standard combined field remains unexpanded','literal default configuration preserves whitespace','UI selects supported defaults only','native default submission exports selected options','omitted standard input exports exact literal']}
            (ROOT/'docs/testing/results/extended-choice-transport-ui-lab.json').write_text(json.dumps(report,indent=2)+'\n')
            print(json.dumps(report))
            await browser.close()
    finally:lab.stop()

if __name__=='__main__':asyncio.run(main())
