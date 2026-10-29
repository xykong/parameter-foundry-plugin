#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = ["playwright>=1.55,<2"]
# ///
"""Submit historical scalar Rebuild forms in an isolated, synthetic UI Lab."""
import asyncio
import hashlib
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import time
import os
import uuid
import zipfile
import re
import urllib.request

from playwright.async_api import async_playwright

PUBLIC = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location("foundry_e2e", PUBLIC / "tools/e2e/run-e2e.py")
lab = importlib.util.module_from_spec(spec)
spec.loader.exec_module(lab)
lab.JENKINS_IMAGE = "jenkins/jenkins:2.568.3-lts-jdk21"
ROOT = PUBLIC / ("tools/ui-lab/.cache/delimited-rebuild-" + uuid.uuid4().hex)
ROOT.mkdir(parents=True, exist_ok=True)
lab.HOME_DIR = ROOT / "home"
lab.PLUGINS_DIR = ROOT / "plugins"
RESULT = PUBLIC / "docs/testing/results/delimited-rebuild.json"
INIT = r'''
import jenkins.model.Jenkins
import hudson.model.*
import hudson.security.*
import io.jenkins.plugins.parameterfoundry.definition.*
import io.jenkins.plugins.parameterfoundry.model.Option

def j=Jenkins.get()
j.securityRealm=SecurityRealm.NO_AUTHENTICATION
j.authorizationStrategy=AuthorizationStrategy.UNSECURED
j.numExecutors=1
j.noUsageStatistics=true
j.save()
Thread.start {
while(j.initLevel != hudson.init.InitMilestone.COMPLETED) { Thread.sleep(500) }
File locationFile=new File(j.rootDir,'owned-location.txt')
while(!locationFile.isFile()) { Thread.sleep(100) }
def location=jenkins.model.JenkinsLocationConfiguration.get()
location.url=locationFile.text
location.save()
[comma:',',custom:'|'].each { name,delimiter ->
 def d=new FoundryChoiceParameterDefinition('TARGETS')
 d.selectionMode='multiple'
 d.standardApiInputMode='delimited'
 d.delimiter=delimiter
 d.choices=['alpha','beta','gamma'].collect{new Option(it)}
 def job=j.createProject(FreeStyleProject,'rebuild-'+name)
 job.addProperty(new ParametersDefinitionProperty(d))
 job.save()
 def run=job.scheduleBuild2(0,new ParametersAction(new StringParameterValue('TARGETS',['alpha','beta','gamma'].join(delimiter)))).get()
 assert run.result==Result.SUCCESS
 def control=j.createProject(FreeStyleProject,'native-'+name)
 control.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition('TARGETS','')))
 assert control.scheduleBuild2(0,new ParametersAction(new StringParameterValue('TARGETS',['alpha','beta','gamma'].join(delimiter)))).get().result==Result.SUCCESS
}
new File(j.rootDir,'delimited-rebuild-ready').text=groovy.json.JsonOutput.toJson([core:Jenkins.VERSION,version:j.pluginManager.getPlugin('parameter-foundry').version,sha256:java.security.MessageDigest.getInstance('SHA-256').digest(j.pluginManager.getPlugin('parameter-foundry').archive.bytes).encodeHex().toString()])
}
'''

async def main():
    cached = Path(os.environ.get("PF_REBUILD_PLUGIN_CACHE", PUBLIC / "tools/e2e/.cache/plugins"))
    assert cached.is_dir(), "Prepare the normal E2E plugin cache first."
    shutil.copytree(cached, lab.PLUGINS_DIR, dirs_exist_ok=True)
    artifact = PUBLIC / "target/parameter-foundry.hpi"
    shutil.copy2(artifact, lab.PLUGINS_DIR / "parameter-foundry.jpi")
    candidate_sha = hashlib.sha256((lab.PLUGINS_DIR / "parameter-foundry.jpi").read_bytes()).hexdigest()
    rebuild = lab.PLUGINS_DIR / "rebuild.jpi"
    with urllib.request.urlopen(
        "https://updates.jenkins.io/download/plugins/rebuild/344.v805899551b_13/rebuild.hpi", timeout=60
    ) as response:
        rebuild.write_bytes(response.read())
    assert hashlib.sha256(rebuild.read_bytes()).hexdigest() == "a3cccdaba225f072af3521cbf28322f562584dd330201335802f2b65a43a757b"
    init = ROOT / "init.groovy"
    init.write_text(INIT)
    lab.HOME_DIR.mkdir(parents=True)
    plugin_manifest = {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in lab.PLUGINS_DIR.glob("*.jpi")}
    (ROOT / "plugin-manifest.json").write_text(json.dumps(plugin_manifest, indent=2))
    name = "pf-delimited-rebuild-" + uuid.uuid4().hex
    container_id = None
    try:
        created = subprocess.run([
            "docker", "create", "--name", name, "--cpus", "2", "--memory", "4g",
            "--publish", "127.0.0.1::8080", "--env", "JAVA_OPTS=-Djenkins.install.runSetupWizard=false",
            "--volume", str(lab.HOME_DIR)+":/var/jenkins_home",
            "--volume", str(lab.PLUGINS_DIR)+":/usr/share/jenkins/ref/plugins:ro",
            "--volume", str(init)+":/usr/share/jenkins/ref/init.groovy.d/01-rebuild.groovy:ro", lab.JENKINS_IMAGE,
        ], check=True, capture_output=True, text=True)
        container_id = created.stdout.strip()
        assert len(container_id) == 64
        subprocess.run(["docker", "start", container_id], check=True, capture_output=True)
        port = subprocess.check_output(["docker", "port", container_id, "8080/tcp"], text=True).strip().split(":")[-1]
        lab.BASE_URL = "http://127.0.0.1:" + port
        (lab.HOME_DIR / "owned-location.txt").write_text(lab.BASE_URL + "/")
        deadline = time.monotonic() + 180
        while not (lab.HOME_DIR / "delimited-rebuild-ready").exists():
            assert time.monotonic() < deadline, "Owned fixtures did not initialize."
            await asyncio.sleep(1)
        loaded = json.loads((lab.HOME_DIR / "delimited-rebuild-ready").read_text())
        assert loaded["core"] == "2.568.3"
        assert loaded["sha256"] == candidate_sha
        with zipfile.ZipFile(artifact) as archive:
            manifest = archive.read("META-INF/MANIFEST.MF").decode().replace("\r\n", "\n")
        assert loaded["version"] == re.search(r"^Plugin-Version: (.+)$", manifest, re.M).group(1)
        rows = []
        async with async_playwright() as playwright:
            browser = await playwright.chromium.launch()
            errors = []
            failed_requests = []
            for role in ("core", "foundry"):
                for width in (1440, 390):
                    for name, delimiter in (("comma", ","), ("custom", "|")):
                        job_name = ("native-" if role == "core" else "rebuild-") + name
                        case_errors = []
                        page = await browser.new_page(viewport={"width": width, "height": 1000})
                        async def bound_origin(route):
                            if route.request.url.startswith("http") and not route.request.url.startswith(lab.BASE_URL + "/"):
                                errors.append("Blocked unexpected origin: " + route.request.url.split("?", 1)[0])
                                await route.abort()
                            else:
                                await route.continue_()
                        await page.route("**/*", bound_origin)
                        page.on("request", lambda request: errors.append("Unexpected origin: " + request.url.split("?", 1)[0]) if request.url.startswith("http") and not request.url.startswith(lab.BASE_URL + "/") else None)
                        page.on("pageerror", lambda error: case_errors.append(error.stack or str(error)))
                        page.on("requestfailed", lambda request: failed_requests.append(request.url.split("?", 1)[0]))
                        await page.goto(f"{lab.BASE_URL}/job/{job_name}/1/rebuild/parameterized")
                        field = page.locator('input[name="value"]')
                        assert await field.input_value() == delimiter.join(("alpha", "beta", "gamma"))
                        submit = page.get_by_role("button", name="Rebuild", exact=True)
                        await submit.scroll_into_view_if_needed()
                        assert await submit.is_visible() and await submit.is_enabled()
                        await page.screenshot(path=str(ROOT / f"rebuild-{name}-{width}.png"), full_page=True)
                        async with page.expect_response(lambda r: "/rebuild/configSubmit" in r.url) as pending:
                            await submit.click()
                        response = await pending.value
                        if response.status not in (200, 302, 303):
                            (ROOT / "failed-response.html").write_text(await response.text())
                        assert response.status in (200, 302, 303), f"Rebuild failed: {response.status}"
                        expected_number = 2 if width == 1440 else 3
                        url = f"{lab.BASE_URL}/job/{job_name}/{expected_number}/api/json?tree=building,result,actions[parameters[name,value]]"
                        deadline = time.monotonic() + 60
                        while True:
                            result = await page.request.get(url)
                            if result.ok:
                                data = await result.json()
                                if not data["building"]:
                                    break
                            assert time.monotonic() < deadline, "Neutral rebuild did not complete."
                            await asyncio.sleep(1)
                        assert data["result"] == "SUCCESS"
                        values = [p for a in data["actions"] for p in a.get("parameters", []) if p["name"] == "TARGETS"]
                        assert len(values) == 1 and values[0]["value"] == (delimiter.join(("alpha", "beta", "gamma")) if role == "core" else ["alpha", "beta", "gamma"])
                        historical = await page.request.get(f"{lab.BASE_URL}/job/{job_name}/1/api/json?tree=actions[parameters[name,value]]")
                        assert any(p["value"] == delimiter.join(("alpha", "beta", "gamma")) for a in (await historical.json())["actions"] for p in a.get("parameters", []) if p["name"] == "TARGETS")
                        rows.append({"role": role, "pageErrors": case_errors, "viewport": width, "delimiter": delimiter, "status": response.status, "savedValues": values[0]["value"], "historyUnchanged": True})
                        await page.close()
            for width in (1440, 390):
                for delimiter in (",", "|"):
                    baseline = next(r for r in rows if r["role"] == "core" and r["viewport"] == width and r["delimiter"] == delimiter)
                    actual = next(r for r in rows if r["role"] == "foundry" and r["viewport"] == width and r["delimiter"] == delimiter)
                    assert actual["pageErrors"] == baseline["pageErrors"], (baseline["pageErrors"], actual["pageErrors"])
            assert not errors and not failed_requests, (errors, failed_requests)
            await browser.close()
        RESULT.parent.mkdir(parents=True, exist_ok=True)
        report = {"state": "DELIMITED_REBUILD_BROWSER_PASS", "artifactSha256": candidate_sha, "loadedArtifact": loaded, "pluginManifest": plugin_manifest, "pageErrors": errors, "failedRequests": failed_requests, "rebuildVersion": "344.v805899551b_13", "cases": rows, "syntheticBuilds": 12, "productionCalls": 0}

    finally:
        if container_id is not None:
            logs = subprocess.run(["docker", "logs", container_id], capture_output=True)
            (ROOT / "server.log").write_bytes(logs.stdout + logs.stderr)
            removed = subprocess.run(["docker", "rm", "--force", container_id], capture_output=True, text=True)
            cleanup = {"containerId": container_id, "returncode": removed.returncode}
            (ROOT / "cleanup.json").write_text(json.dumps(cleanup))
            assert removed.returncode == 0, "Owned container cleanup failed"
    report["cleanup"] = cleanup
    assert report["cleanup"]["returncode"] == 0
    RESULT.write_text(json.dumps(report, indent=2) + "\n")
    print(RESULT.read_text())

if __name__ == "__main__":
    asyncio.run(main())
