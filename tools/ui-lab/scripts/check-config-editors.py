#!/usr/bin/env -S uv run --script
# /// script
# dependencies = ["playwright>=1.55,<2"]
# ///
"""Exercise configuration repeatables and credential selection on a disposable controller."""

import asyncio
import json
from pathlib import Path
import shutil
import socket
import subprocess
import time
import urllib.request
from playwright.async_api import async_playwright, expect

ROOT = Path(__file__).resolve().parents[3]
UNIT = ROOT / "tools/ui-lab/.cache" / ("config-editors-" + str(time.time_ns()))
NAME = "pf-config-editors-" + str(time.time_ns())
SEED = """
import jenkins.model.Jenkins
import io.jenkins.plugins.parameterfoundry.provider.*
import io.jenkins.plugins.parameterfoundry.model.Option
import com.cloudbees.plugins.credentials.*
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl

def j=Jenkins.get()
j.setNumExecutors(0)
def config=ParameterFoundryGlobalConfiguration.get()
def provider=new StaticProviderDefinition('synthetic-options')
provider.setOptions([new Option('first'),new Option('second')])
def profile=new ConnectionProfile('synthetic-profile','https://example.invalid/api/')
def header=new ProfileHeader('X-Example','synthetic')
profile.setHeaders([header])
config.setProviders([provider]); config.setConnectionProfiles([profile]); config.save()
def catalog=io.jenkins.plugins.parameterfoundry.companion.script.ScriptProviderCatalog.get()
catalog.setEntries([new io.jenkins.plugins.parameterfoundry.companion.script.ScriptCatalogEntry('synthetic-script',new org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.SecureGroovyScript('return []',true,null))]);catalog.save()
SystemCredentialsProvider.getInstance().getCredentials().add(new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL,'synthetic-basic','Synthetic browser test','test-user','test-value'))
SystemCredentialsProvider.getInstance().save()
def folder=j.createProject(com.cloudbees.hudson.plugins.folder.Folder,'synthetic-folder')
def property=new ParameterFoundryFolderProperty()
property.setConnectionProfiles([new ConnectionProfile('folder-profile','https://example.invalid/api/')])
folder.addProperty(property);folder.save()
new File(j.rootDir,'config-editor-ready').text='ready' 
"""


def command(args):
    return subprocess.check_output(args, text=True).strip()


async def inspect(base):
    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=True)
        page = await browser.new_page(viewport={"width": 1440, "height": 1100})
        await page.goto(base + "/configure", wait_until="networkidle")
        # Capture the actual controls before operating them. Use the rendered
        # structure to keep each deletion scoped to its own repeated item.
        await page.locator('select[name="_.credentialId"]').select_option(
            "synthetic-basic"
        )
        assert (
            await page.locator('select[name="_.credentialId"]').input_value()
            == "synthetic-basic"
        )

        def chunk(selector):
            return page.locator(selector).locator(
                'xpath=ancestor::div[contains(concat(" ", normalize-space(@class), " "), " repeated-chunk ")][1]'
            )

        header = chunk('input[value="X-Example"]')
        assert await header.locator("button.repeatable-delete").count() == 1
        await header.locator("button.repeatable-delete").click()
        await page.locator('input[value="X-Example"]').wait_for(state="detached")
        assert (
            await page.locator('input[name="_.id"][value="synthetic-profile"]').count()
            == 1
        )
        option = chunk('input[name="_.value"][value="second"]')
        assert await option.locator("button.repeatable-delete").count() == 1
        await option.locator("button.repeatable-delete").click()
        await page.locator('input[name="_.value"][value="second"]').wait_for(
            state="detached"
        )
        assert await page.locator('input[name="_.value"][value="first"]').count() == 1
        await page.get_by_role("button", name="Save", exact=True).click()
        await page.wait_for_url(base + "/")
        await page.goto(base + "/configure", wait_until="networkidle")
        await expect(page.locator('select[name="_.credentialId"]')).to_have_value(
            "synthetic-basic"
        )
        assert await page.locator('input[name="_.value"][value="first"]').count() == 1
        assert await page.locator('input[name="_.value"][value="second"]').count() == 0
        assert await page.locator('input[value="X-Example"]').count() == 0
        profile = chunk('input[name="_.id"][value="synthetic-profile"]')
        assert await profile.locator("button.repeatable-delete").count() == 1
        await page.locator('select[name="_.credentialId"]').scroll_into_view_if_needed()
        await page.screenshot(path=UNIT / "saved-configuration.png")
        await profile.locator("button.repeatable-delete").click()
        await page.locator('input[name="_.id"][value="synthetic-profile"]').wait_for(
            state="detached"
        )
        await page.get_by_role("button", name="Save", exact=True).click()
        await page.wait_for_url(base + "/")
        await page.goto(base + "/configure", wait_until="networkidle")
        assert (
            await page.locator('input[name="_.id"][value="synthetic-profile"]').count()
            == 0
        )
        assert await page.locator('input[name="_.value"][value="first"]').count() == 1
        provider = chunk('input[name="_.id"][value="synthetic-options"]')
        await provider.locator("button.repeatable-delete").first.click()
        await page.locator('input[name="_.id"][value="synthetic-options"]').wait_for(
            state="detached"
        )
        catalog = chunk('input[name="_.id"][value="synthetic-script"]')
        assert await catalog.locator("button.repeatable-delete").count() == 1
        await catalog.locator("button.repeatable-delete").click()
        await page.locator('input[name="_.id"][value="synthetic-script"]').wait_for(
            state="detached"
        )
        await page.get_by_role("button", name="Save", exact=True).click()
        await page.wait_for_url(base + "/")
        await page.goto(base + "/configure", wait_until="networkidle")
        assert (
            await page.locator('input[name="_.id"][value="synthetic-options"]').count()
            == 0
        )
        assert (
            await page.locator('input[name="_.id"][value="synthetic-script"]').count()
            == 0
        )
        folder_url = base + "/job/synthetic-folder/"
        await page.goto(folder_url + "configure", wait_until="networkidle")
        await page.locator('select[name="_.credentialId"]').select_option(
            "synthetic-basic"
        )
        await page.get_by_role("button", name="Save", exact=True).click()
        await page.wait_for_url(folder_url)
        await page.goto(folder_url + "configure", wait_until="networkidle")
        assert (
            await page.locator('select[name="_.credentialId"]').input_value()
            == "synthetic-basic"
        )
        folder_profile = chunk('input[name="_.id"][value="folder-profile"]')
        await folder_profile.locator("button.repeatable-delete").click()
        await page.locator('input[name="_.id"][value="folder-profile"]').wait_for(
            state="detached"
        )
        await page.get_by_role("button", name="Save", exact=True).click()
        await page.wait_for_url(folder_url)
        await page.goto(folder_url + "configure", wait_until="networkidle")
        assert (
            await page.locator('input[name="_.id"][value="folder-profile"]').count()
            == 0
        )
        import hashlib

        result = {
            "state": "CONFIG_EDITORS_PASS",
            "jenkins": "2.580.1",
            "browser": browser.version,
            "viewport": {"width": 1440, "height": 1100},
            "hpi_sha256": hashlib.sha256(
                (ROOT / "target/parameter-foundry.hpi").read_bytes()
            ).hexdigest(),
            "credential_selection_saved": True,
            "nested_header_deleted_parent_preserved": True,
            "option_deletion_saved": True,
            "profile_deletion_saved": True,
            "empty_global_providers_saved": True,
            "empty_script_catalog_saved": True,
            "folder_credential_selection_saved": True,
            "folder_profile_deletion_saved": True,
            "business_builds": 0,
        }
        (UNIT / "result.json").write_text(json.dumps(result, indent=2) + "\n")
        await browser.close()


def main():
    UNIT.mkdir(parents=True)
    plugins = UNIT / "plugins"
    plugins.mkdir()
    for source in (ROOT / "target/test-classes/test-dependencies").glob("*.hpi"):
        shutil.copy2(source, plugins / (source.stem + ".jpi"))
    shutil.copy2(
        ROOT / "target/parameter-foundry.hpi", plugins / "parameter-foundry.jpi"
    )
    (UNIT / "init.groovy").write_text(SEED)
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        port = sock.getsockname()[1]
    base = f"http://127.0.0.1:{port}"
    command(
        [
            "docker",
            "run",
            "-d",
            "--rm",
            "--name",
            NAME,
            "--cpus",
            "2",
            "--memory",
            "4g",
            "-p",
            f"127.0.0.1:{port}:8080",
            "-e",
            "JAVA_OPTS=-Djenkins.install.runSetupWizard=false",
            "-v",
            str(plugins) + ":/usr/share/jenkins/ref/plugins:ro",
            "-v",
            str(UNIT / "init.groovy")
            + ":/usr/share/jenkins/ref/init.groovy.d/01-doc.groovy:ro",
            "jenkins/jenkins:2.580.1-jdk21",
        ]
    )
    try:
        deadline = time.monotonic() + 180
        while time.monotonic() < deadline:
            try:
                with urllib.request.urlopen(base + "/api/json", timeout=2) as response:
                    if (
                        response.status == 200
                        and command(
                            [
                                "docker",
                                "exec",
                                NAME,
                                "test",
                                "-f",
                                "/var/jenkins_home/config-editor-ready",
                            ]
                        )
                        == ""
                    ):
                        break
            except (OSError, subprocess.CalledProcessError):
                time.sleep(1)
        else:
            raise RuntimeError("Controller did not become ready")
        asyncio.run(inspect(base))
        print(UNIT)
    finally:
        command(["docker", "stop", NAME])


if __name__ == "__main__":
    main()
