#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = ["playwright>=1.55,<2"]
# ///
"""Disposable real-plugin browser gate for the first-party file companion."""
from __future__ import annotations

import argparse
import asyncio
import importlib.util
import json
import platform
import time
import urllib.request
from pathlib import Path

from playwright.async_api import async_playwright

ROOT = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location("foundry_e2e", ROOT / "tools/e2e/run-e2e.py")
lab = importlib.util.module_from_spec(spec)
spec.loader.exec_module(lab)
lab.CONTAINER = "parameter-foundry-files-ui-lab"
lab.HOME_DIR = ROOT / "tools/ui-lab/.cache/foundry-files-home"
lab.render_init_groovy = lambda: Path(__file__).with_name("foundry-files-init.groovy")
GALLERY = ROOT / "docs/ui-gallery/images/parameter-foundry"

def wait_ready() -> None:
    deadline = time.monotonic() + 120
    while time.monotonic() < deadline:
        try:
            with urllib.request.urlopen(lab.BASE_URL + "/job/foundry-files/api/json", timeout=3) as response:
                if response.status == 200 and (lab.HOME_DIR / "parameter-foundry-files-ready").is_file():
                    return
        except OSError:
            pass
        time.sleep(1)
    lab.run(["docker", "logs", lab.CONTAINER], check=False)
    raise RuntimeError("Foundry file UI Lab did not become ready")

lab.wait_ready = wait_ready

async def wait_build(page, job: str, number: int) -> None:
    for _ in range(120):
        response = await page.request.get(f"{lab.BASE_URL}/job/{job}/{number}/api/json?tree=building,result")
        if response.ok:
            body = await response.json()
            if not body["building"]:
                assert body["result"] == "SUCCESS", body
                return
        await asyncio.sleep(0.5)
    raise AssertionError(f"build timed out: {job}/{number}")

async def check() -> None:
    async with async_playwright() as pw:
        browser = await pw.chromium.launch()
        page = await browser.new_page(viewport={"width": 1440, "height": 1000})
        inventory = await page.request.get(lab.BASE_URL + "/pluginManager/api/json?tree=plugins[shortName]")
        assert inventory.ok
        assert not any(p["shortName"] == "file-parameters" for p in (await inventory.json())["plugins"])
        await page.goto(f"{lab.BASE_URL}/job/foundry-files/build?delay=0sec")
        files = page.locator('input[type="file"]')
        assert await files.count() == 2
        await page.get_by_label("UPLOAD", exact=True).set_input_files({"name": "sample.txt", "mimeType": "text/plain", "buffer": b"synthetic upload\n"})
        await page.get_by_label("SMALL", exact=True).set_input_files({"name": "small.txt", "mimeType": "text/plain", "buffer": b"small upload\n"})
        await page.screenshot(path=GALLERY / "file-upload.png", full_page=True)
        await page.get_by_role("button", name="Build", exact=True).click()
        await wait_build(page, "foundry-files", 1)
        download = await page.request.get(f"{lab.BASE_URL}/job/foundry-files/1/parameters/parameter/UPLOAD/download")
        assert download.ok, download.status
        assert await download.body() == b"synthetic upload\n"
        lab.run(["docker", "restart", lab.CONTAINER])
        lab.wait_ready()
        await page.goto(f"{lab.BASE_URL}/job/foundry-files/1/rebuild/parameterized")
        await page.get_by_role("button", name="Rebuild", exact=True).click()
        await wait_build(page, "foundry-files", 2)
        await page.goto(f"{lab.BASE_URL}/job/foundry-files-freestyle/build?delay=0sec")
        await page.locator('input[type="file"]').set_input_files({"name": "sample.txt", "mimeType": "text/plain", "buffer": b"synthetic upload\n"})
        await page.get_by_role("button", name="Build", exact=True).click()
        await wait_build(page, "foundry-files-freestyle", 1)
        await page.set_viewport_size({"width": 412, "height": 915})
        await page.goto(f"{lab.BASE_URL}/job/foundry-files/build?delay=0sec")
        assert await page.locator('input[type="file"]').count() == 2
        assert await page.evaluate("document.documentElement.scrollWidth") == 412
        await page.get_by_label("UPLOAD", exact=True).set_input_files({"name": "sample.txt", "mimeType": "text/plain", "buffer": b"synthetic upload\n"})
        await page.get_by_label("SMALL", exact=True).set_input_files({"name": "small.txt", "mimeType": "text/plain", "buffer": b"small upload\n"})
        await page.evaluate("window.scrollTo(0, document.documentElement.scrollHeight)")
        small_box = await page.get_by_label("SMALL", exact=True).bounding_box()
        build_box = await page.get_by_role("button", name="Build", exact=True).bounding_box()
        assert small_box["y"] + small_box["height"] <= build_box["y"]
        await page.screenshot(path=GALLERY / "file-upload-mobile.png", full_page=False)
        await page.get_by_role("button", name="Build", exact=True).click()
        await wait_build(page, "foundry-files", 3)
        schema = await page.request.get(f"{lab.BASE_URL}/job/foundry-files/parameter-foundry/api/v1/schema")
        assert schema.ok
        assert all(not p["supported"] for p in (await schema.json())["parameters"])
        assert all(p["owner"] == "parameter-foundry" for p in (await schema.json())["parameters"])
        typed = await page.request.post(f"{lab.BASE_URL}/job/foundry-files/parameter-foundry/api/v1/build", data=json.dumps({"values": {}}), headers={"Content-Type": "application/json"})
        assert typed.status == 409, (typed.status, await typed.text())
        report = {"browser": "Chromium " + browser.version, "os": platform.system(), "desktop": "1440x1000", "mobileViewport": "412x915", "checks": ["UI upload", "Pipeline stash", "temporary wrapper", "download bytes", "controller restart and Rebuild retained upload", "Freestyle wrapper", "mobile overflow and upload submission", "source File Parameters absent", "binary schema ownership", "typed JSON 409"], "passed": True}
        (ROOT / "docs/testing/results/foundry-files-ui-lab.json").write_text(json.dumps(report, indent=2) + "\n")
        print(json.dumps(report))
        await browser.close()

async def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--skip-build", action="store_true", help="Use the HPI already produced by mvn verify")
    if parser.parse_args().skip_build:
        lab.build_artifacts = lambda: None
    lab.prepare_plugins()
    lab.start()
    try:
        lab.wait_ready()
        await check()
    finally:
        lab.stop()

if __name__ == "__main__":
    asyncio.run(main())
