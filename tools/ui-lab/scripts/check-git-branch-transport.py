#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = ["playwright>=1.55,<2"]
# ///
"""Disposable browser gate for legacy Git dropdown and literal transport compatibility."""
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
lab.CONTAINER='parameter-foundry-git-branch-transport-lab'
lab.HOME_DIR=ROOT/'tools/ui-lab/.cache/git-branch-transport-home'
lab.render_init_groovy=lambda: Path(__file__).with_name('git-branch-transport-init.groovy')

def wait_ready():
    deadline=time.monotonic()+120
    while time.monotonic()<deadline:
        try:
            with urllib.request.urlopen(lab.BASE_URL+'/job/git-transport-top/api/json',timeout=3) as response:
                if response.status==200 and (lab.HOME_DIR/'git-branch-transport-ready').exists():return
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
            checks=[]
            async def result(base,number,expected):
                for _ in range(120):
                    response=await page.request.get(base+f'/{number}/api/json?tree=building,result')
                    if response.ok:
                        body=await response.json()
                        if not body['building']:
                            assert body['result']=='SUCCESS',body
                            break
                    await asyncio.sleep(.5)
                else:raise AssertionError('Contract build timed out')
                response=await page.request.get(base+f'/{number}/artifact/result.txt')
                assert response.ok and await response.text()==expected
            for name,expected in [('exact','release/prod'),('approximate','release/prod-v2'),('missing','main'),('top','main'),('none','main')]:
                base=lab.BASE_URL+'/job/git-transport-'+name
                if name=='top':
                    await page.goto(base+'/configure')
                    assert await page.locator('select[name="parameter.standardApiInputMode"]').input_value()=='list-git-branches'
                    assert await page.locator('select[name="parameter.gitSelectionMode"]').input_value()=='top'
                    await page.get_by_role('button',name='Save',exact=True).click()
                    checks.append('compatibility configuration save')
                await page.goto(base+'/build?delay=0sec')
                field=page.locator('[data-parameter-name="REF"]')
                await page.wait_for_function("document.querySelector('[data-parameter-name=REF]').dataset.upInteractive === 'true'")
                select=field.locator('[data-up-enhanced] select')
                assert await field.locator('[data-up-snapshot-id]').input_value()==''
                assert await select.input_value()==expected,(name,await select.input_value(),expected)
                assert await select.locator('option').count()==3
                await page.get_by_role('button',name='Build',exact=True).click()
                await result(base,1,expected)
                checks.append(name+' dropdown and form output')
            base=lab.BASE_URL+'/job/git-transport-top'
            response=await page.request.post(base+'/buildWithParameters?delay=0sec')
            assert response.status in [200,201,302],response.status
            await result(base,2,'release/prod')
            checks.append('TOP dropdown independent of literal API default')
            response=await page.request.post(base+'/build?delay=0sec',form={'json':json.dumps({'parameter':[{'name':'REF','value':' unknown '} ]})})
            assert response.status in [200,201,302],response.status
            await result(base,3,' unknown ')
            checks.append('unlisted form literal retained')
            await page.goto(base+'/3/rebuild/parameterized')
            historical=page.locator('input[name="value"]')
            assert await historical.input_value()==' unknown '
            assert await page.locator('[data-provider-backed="true"]').count()==0
            await historical.fill(' retired-revision ')
            await page.get_by_role('button',name='Rebuild',exact=True).click()
            await result(base,4,' retired-revision ')
            checks.append('Rebuild editable historical literal without Provider')
            report={'passed':True,'browser':browser.version,'checks':checks}
            (ROOT/'docs/testing/results/git-branch-transport-ui-lab.json').write_text(json.dumps(report,indent=2)+'\n')
            print(json.dumps(report))
            await browser.close()
    finally:lab.stop()

if __name__=='__main__':asyncio.run(main())
