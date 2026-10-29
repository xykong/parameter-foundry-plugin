#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = ["playwright>=1.55,<2"]
# ///
"""Capture genuine matrix interactions from the isolated documentation controller."""
import asyncio
import hashlib
import json
from pathlib import Path
from playwright.async_api import async_playwright

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "docs/media"
RAW = ROOT / "tools/e2e/.cache/docs-media-capture"

async def main():
    OUTPUT.mkdir(exist_ok=True)
    RAW.mkdir(exist_ok=True)
    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=True)
        page = await browser.new_page(viewport={"width": 1160, "height": 780}, device_scale_factor=1)
        await page.goto('http://127.0.0.1:18081/job/parameter-foundry-provider-e2e/build?delay=0sec', wait_until='networkidle')
        matrix = page.locator('[data-parameter-name="DOCKER_LOGINS"]')
        await page.wait_for_selector('[data-parameter-name="DOCKER_LOGINS"][data-up-interactive="true"]')
        await matrix.scroll_into_view_if_needed()
        frames = []
        async def frame(name, expected):
            assert await matrix.locator('input[data-up-generated-value]').count() == expected
            await matrix.screenshot(path=RAW / f'{name}.png')
            frames.append({'file':f'{name}.png','selected':expected,'hold_ms':1800})
        await matrix.get_by_role('button',name='Clear all',exact=True).click()
        await frame('01-clear',0)
        await matrix.locator('thead th button',has_text='staging').first.click()
        await frame('02-column',5)
        await matrix.locator('tbody th button').first.click()
        await frame('03-row',7)
        await matrix.get_by_role('button',name='Select all',exact=True).click()
        await frame('04-all',15)
        await matrix.get_by_role('button',name='Invert',exact=True).click()
        await frame('05-invert',0)
        await matrix.locator('thead th button',has_text='staging').first.click()
        await matrix.screenshot(path=OUTPUT/'matrix.png')
        metadata={'source':'isolated synthetic Jenkins UI Lab','job':'parameter-foundry-provider-e2e', 'frames':frames, 'hpi_sha256':hashlib.sha256((ROOT/'plugin/target/parameter-foundry.hpi').read_bytes()).hexdigest()}
        (RAW/'capture.json').write_text(json.dumps(metadata,indent=2)+'\n')
        await page.goto('http://127.0.0.1:18081/job/parameter-foundry-form-e2e/build?delay=0sec', wait_until='networkidle')
        form = page.locator('[data-parameter-name="DEPLOYMENT"]')
        await page.wait_for_selector('[data-parameter-name="DEPLOYMENT"][data-up-form-interactive="true"]')
        await form.get_by_label('Service name',exact=True).fill('catalog')
        await form.get_by_label('replicas',exact=True).fill('4')
        await form.get_by_label('Enable advanced settings',exact=True).check()
        await form.get_by_label('features',exact=True).select_option(['audit','canary'])
        assert json.loads(await form.locator('[data-up-form-value]').input_value())['replicas'] == 4
        await form.screenshot(path=OUTPUT/'typed-form.png')
        await page.goto('http://127.0.0.1:18081/job/parameter-foundry-migration-e2e/parameter-foundry/',wait_until='networkidle')
        await page.screenshot(path=RAW/'migration-full.png',full_page=True)
        await page.goto('http://127.0.0.1:18081/parameter-foundry-retirement/',wait_until='networkidle')
        await page.locator('.up-retirement').screenshot(path=OUTPUT/'retirement.png')
        await browser.close()

if __name__=='__main__':asyncio.run(main())
