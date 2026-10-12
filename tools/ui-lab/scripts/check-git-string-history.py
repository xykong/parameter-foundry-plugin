#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = ["playwright>=1.55,<2"]
# ///
"""Rebuild source Git string values in a disposable, loopback-only Jenkins lab."""
import argparse
import asyncio
import hashlib
import importlib.util
import json
import shutil
import time
import urllib.request
import zipfile
from datetime import datetime, timezone
from pathlib import Path

from playwright.async_api import async_playwright

ROOT = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location("foundry_e2e", ROOT / "tools/e2e/run-e2e.py")
lab = importlib.util.module_from_spec(spec)
spec.loader.exec_module(lab)
lab.CONTAINER = "parameter-foundry-git-string-history-lab"
lab.HOME_DIR = ROOT / "tools/ui-lab/.cache/git-string-history-home"
lab.PLUGINS_DIR = ROOT / "tools/ui-lab/.cache/git-string-history-plugins"
lab.JENKINS_IMAGE = "jenkins/jenkins:2.568.3-jdk21"
assert lab.BASE_URL == "http://127.0.0.1:18081"


def script(text):
    from urllib.parse import urlencode

    request = urllib.request.Request(
        lab.BASE_URL + "/scriptText",
        data=urlencode({"script": text}).encode(),
    )
    with urllib.request.urlopen(request, timeout=90) as response:
        return response.read().decode()


def wait_ready():
    deadline = time.monotonic() + 180
    while time.monotonic() < deadline:
        try:
            with urllib.request.urlopen(lab.BASE_URL + "/api/json", timeout=3) as response:
                if response.status == 200 and (lab.HOME_DIR / "history-ready").exists():
                    return
        except OSError:
            pass
        time.sleep(1)
    raise RuntimeError("History browser lab did not become ready")


async def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--legacy-hpi", required=True, type=Path)
    parser.add_argument("--dependency-dir", type=Path)
    parser.add_argument("--skip-build", action="store_true")
    args = parser.parse_args()
    # Unique bind-mount paths also avoid stale directory identities after a Mac rename.
    run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%f")
    cache = ROOT / "tools/ui-lab/.cache/git-string-history" / run_id
    lab.HOME_DIR = cache / "home"
    lab.PLUGINS_DIR = cache / "plugins"
    with zipfile.ZipFile(args.legacy_hpi) as archive:
        manifest = archive.read("META-INF/MANIFEST.MF").decode()
    assert "Short-Name: list-git-branches-parameter" in manifest
    if args.skip_build:
        lab.build_artifacts = lambda: None
    if args.dependency_dir:
        lab.build_artifacts()
        lab.PLUGINS_DIR.mkdir(parents=True, exist_ok=True)
        # Use a fresh, owned directory; no dependencies are modified in place.
        assert not list(lab.PLUGINS_DIR.glob("*.jpi")), "Use a fresh history plugin directory"
        for source in args.dependency_dir.glob("*.jpi"):
            shutil.copy2(source, lab.PLUGINS_DIR / source.name)
        shutil.copy2(ROOT / "plugin/target/parameter-foundry.hpi", lab.PLUGINS_DIR / "parameter-foundry.jpi")
    else:
        lab.prepare_plugins()
    shutil.copy2(args.legacy_hpi, lab.PLUGINS_DIR / "list-git-branches-parameter.jpi")
    assert (lab.PLUGINS_DIR / "rebuild.jpi").is_file()
    init = cache / "init.groovy"
    init.parent.mkdir(parents=True, exist_ok=True)
    init.write_text("""import jenkins.model.Jenkins
def j=Jenkins.get()
j.securityRealm=hudson.security.SecurityRealm.NO_AUTHENTICATION
j.authorizationStrategy=hudson.security.AuthorizationStrategy.UNSECURED
j.setCrumbIssuer(null)
j.setNumExecutors(2)
new File(j.rootDir,'history-ready').text='ready'
""")
    lab.render_init_groovy = lambda: init
    lab.start()
    try:
        wait_ready()
        literals = ["old-unlisted-ref", " padded,ref ", ""]
        rows = json.loads(script("""import hudson.model.*
import groovy.json.*
import jenkins.model.Jenkins
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition
import io.jenkins.plugins.parameterfoundry.provider.*
import io.jenkins.plugins.parameterfoundry.model.Option
def j=Jenkins.get()
assert Jenkins.VERSION=='2.568.3'
assert j.pluginManager.getPlugin('parameter-foundry').active
def source=j.pluginManager.getPlugin('list-git-branches-parameter')
assert source.active && source.enabled
assert j.pluginManager.getPlugin('rebuild').active
def provider=new StaticProviderDefinition('history-static')
provider.options=[new Option('release/default')]
ParameterFoundryGlobalConfiguration.get().providers=[provider]
def literals=new JsonSlurper().parseText('@@literals@@')
def rows=[]
(0..<6).each { index ->
 def literal=literals[index%3]
 def job=j.createProject(FreeStyleProject,'legacy-git-history-'+index)
 job.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition('REF','')))
 def value=new com.syhuang.hudson.plugins.listgitbranchesparameter.ListGitBranchesParameterValue('REF',literal)
 assert value.class.classLoader==source.classLoader
 value.description='Historical description'
 def run=job.scheduleBuild2(0,new ParametersAction(value)).get()
 assert run.result==Result.SUCCESS
 job.removeProperty(ParametersDefinitionProperty)
 def target=new FoundryChoiceParameterDefinition('REF')
 target.providerId='history-static'
 target.standardApiInputMode='list-git-branches'
 target.defaultMode='explicit'
 target.defaultValue='release/default'
 job.addProperty(new ParametersDefinitionProperty(target))
 rows << [job:job.name,literal:literal,original:run.number]
}
println JsonOutput.toJson(rows)
""".replace("@@literals@@", json.dumps(literals))))
        output = cache / "evidence"
        output.mkdir(parents=True, exist_ok=True)
        checks = []
        async with async_playwright() as pw:
            browser = await pw.chromium.launch()
            for index, row in enumerate(rows):
                width, height = (1440, 1000) if index < 3 else (390, 844)
                page = await browser.new_page(viewport={"width": width, "height": height})
                base = lab.BASE_URL + "/job/" + row["job"]
                resolves = []
                page.on("request", lambda request: resolves.append(request.url)
                        if "/parameter-foundry/resolve" in request.url else None)
                response = await page.goto(base + "/1/parameters/")
                assert response.status == 200
                (output / f"history-{index}.html").write_text(await page.content())
                if row["literal"]:
                    assert await page.locator("[name=parameter] pre.jenkins-readonly").text_content() == row["literal"]
                else:
                    assert await page.locator("[name=parameter] .jenkins-not-applicable").inner_text() == "N/A"
                assert "Historical description" in await page.locator("body").inner_text()
                await page.screenshot(path=str(output / f"history-{index}.png"), full_page=True)
                await page.goto(base + "/1/rebuild/parameterized")
                field = page.locator("input[name=value]")
                assert await field.input_value() == row["literal"]
                assert await field.is_editable()
                assert await page.locator("[data-provider-backed=true]").count() == 0
                await page.screenshot(path=str(output / f"rebuild-{index}.png"), full_page=True)
                button = page.get_by_role("button", name="Rebuild", exact=True)
                await button.scroll_into_view_if_needed()
                await button.click()
                for _ in range(120):
                    response = await page.request.get(base + "/2/api/json?tree=building,result,actions[parameters[name,value]]")
                    if response.ok:
                        result = await response.json()
                        if not result["building"]:
                            assert result["result"] == "SUCCESS"
                            values = [p for a in result["actions"] for p in a.get("parameters", []) if p["name"] == "REF"]
                            assert len(values) == 1
                            assert values[0]["value"] == (row["literal"] or "release/default")
                            break
                    await asyncio.sleep(.5)
                else:
                    raise AssertionError("Synthetic Rebuild did not complete")
                assert not resolves, resolves
                checks.append({"viewport": [width, height], **row, "rebuilt": result,
                               "providerResolveRequests": len(resolves)})
                await page.close()
            version = browser.version
            await browser.close()
        result = {
            "core": "2.568.3", "browser": version, "checks": checks, "runId": run_id,
            "candidateSha256": hashlib.sha256((lab.PLUGINS_DIR / "parameter-foundry.jpi").read_bytes()).hexdigest(),
            "legacySha256": hashlib.sha256(args.legacy_hpi.read_bytes()).hexdigest(),
            "scope": "6 synthetic source histories and 6 actual Rebuilds; source plugin remains installed",
        }
        (output / "result.json").write_text(json.dumps(result, indent=2) + "\n")
        print(json.dumps(result, indent=2))
    finally:
        lab.stop()


if __name__ == "__main__":
    asyncio.run(main())
