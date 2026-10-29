#!/usr/bin/env -S uv run --script
# /// script
# dependencies = ["playwright>=1.55,<2"]
# ///
"""Render the editorial title over the separately generated conceptual artwork."""
import asyncio
from pathlib import Path
from playwright.async_api import async_playwright
ROOT = Path(__file__).resolve().parents[2]
async def main():
    async with async_playwright() as p:
        browser = await p.chromium.launch()
        page = await browser.new_page(viewport={"width":1440,"height":600},device_scale_factor=1)
        await page.goto((ROOT/'tools/docs-media/hero.html').as_uri())
        await page.locator('.art').evaluate('(image) => image.decode()')
        await page.screenshot(path=ROOT/'docs/media/hero.png')
        await browser.close()
asyncio.run(main())
