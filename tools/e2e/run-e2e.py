#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = [
#   "playwright>=1.55,<2",
# ]
# ///

from __future__ import annotations

import asyncio
import json
import math
import platform
import shutil
import subprocess
import time
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

from playwright.async_api import Browser, Page, async_playwright

ROOT = Path(__file__).resolve().parents[2]
E2E_DIR = ROOT / "tools" / "e2e"
CACHE_DIR = E2E_DIR / ".cache"
PLUGINS_DIR = CACHE_DIR / "plugins"
HOME_DIR = CACHE_DIR / "home"
RESULTS_DIR = ROOT / "docs" / "testing" / "results"
GALLERY_DIR = ROOT / "docs" / "ui-gallery" / "images" / "unified-parameters"
CONTAINER = "unified-parameters-plugin-e2e"
JENKINS_IMAGE = "jenkins/jenkins:2.568.2-lts-jdk21"
MAVEN_IMAGE = "maven:3.9.11-eclipse-temurin-21"
BASE_URL = "http://127.0.0.1:18081"
BUILD_URL = BASE_URL + "/job/unified-parameters-e2e/build?delay=0sec"
PROVIDER_BUILD_URL = (
    BASE_URL + "/job/unified-parameters-provider-e2e/build?delay=0sec"
)
FORM_BUILD_URL = BASE_URL + "/job/unified-parameters-form-e2e/build?delay=0sec"
OPTION_COUNT = 10_000
MAX_RENDERED_OPTIONS = 200
EXTERNAL_SOURCE_ERRORS = (
    "jQuery is not defined",
    "Cannot read properties of undefined (reading 'extend')",
    "Cannot read properties of undefined (reading 'replace')",
)


def run(args: list[str], *, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(args, cwd=ROOT, check=check, text=True)


def stop() -> None:
    run(["docker", "rm", "--force", CONTAINER], check=False)


def build_artifacts() -> None:
    run(
        [
            "docker",
            "run",
            "--rm",
            "-v",
            f"{ROOT}:/workspace",
            "-v",
            f"{Path.home() / '.m2'}:/root/.m2",
            "-w",
            "/workspace",
            MAVEN_IMAGE,
            "mvn",
            "-B",
            "-ntp",
            "package",
            "-DskipTests",
            "-Dspotbugs.skip=true",
            "-Dspotless.check.skip=true",
        ]
    )


def prepare_plugins() -> None:
    build_artifacts()
    PLUGINS_DIR.mkdir(parents=True, exist_ok=True)
    run(
        [
            "docker",
            "run",
            "--rm",
            "-v",
            f"{E2E_DIR / 'plugins.txt'}:/plugins.txt:ro",
            "-v",
            f"{PLUGINS_DIR}:/plugins",
            JENKINS_IMAGE,
            "jenkins-plugin-cli",
            "--plugin-file",
            "/plugins.txt",
            "--plugin-download-directory",
            "/plugins",
            "--latest=false",
        ]
    )
    shutil.copy2(
        ROOT / "plugin" / "target" / "unified-parameters.hpi",
        PLUGINS_DIR / "unified-parameters.jpi",
    )
    shutil.copy2(
        ROOT
        / "migration-extended-choice"
        / "target"
        / "unified-parameters-migration-extended-choice.hpi",
        PLUGINS_DIR / "unified-parameters-migration-extended-choice.jpi",
    )
    shutil.copy2(
        ROOT
        / "migration-active-choices"
        / "target"
        / "unified-parameters-migration-active-choices.hpi",
        PLUGINS_DIR / "unified-parameters-migration-active-choices.jpi",
    )
    shutil.copy2(
        ROOT
        / "migration-list-git-branches"
        / "target"
        / "unified-parameters-migration-list-git-branches.hpi",
        PLUGINS_DIR / "unified-parameters-migration-list-git-branches.jpi",
    )
    shutil.copy2(
        ROOT
        / "migration-additional-sources"
        / "target"
        / "unified-parameters-migration-additional-sources.hpi",
        PLUGINS_DIR / "unified-parameters-migration-additional-sources.jpi",
    )


def start() -> None:
    stop()
    if HOME_DIR.exists():
        shutil.rmtree(HOME_DIR)
    HOME_DIR.mkdir(parents=True)
    run(
        [
            "docker",
            "run",
            "--detach",
            "--name",
            CONTAINER,
            "--cpus",
            "4",
            "--memory",
            "8g",
            "--publish",
            "127.0.0.1:18081:8080",
            "--env",
            "JAVA_OPTS=-Djenkins.install.runSetupWizard=false",
            "--volume",
            f"{HOME_DIR}:/var/jenkins_home",
            "--volume",
            f"{PLUGINS_DIR}:/usr/share/jenkins/ref/plugins:ro",
            "--volume",
            f"{E2E_DIR / 'init.groovy'}:/usr/share/jenkins/ref/init.groovy.d/01-e2e.groovy:ro",
            JENKINS_IMAGE,
        ]
    )


def wait_ready() -> None:
    deadline = time.monotonic() + 180
    while time.monotonic() < deadline:
        try:
            with urllib.request.urlopen(
                BASE_URL + "/job/unified-parameters-e2e/api/json", timeout=3
            ) as response:
                if response.status == 200 and (
                    HOME_DIR / "unified-parameters-e2e-ready"
                ).is_file():
                    return
        except OSError:
            pass
        time.sleep(1)
    run(["docker", "logs", CONTAINER], check=False)
    raise RuntimeError("Jenkins E2E controller did not become ready")


def p95(values: list[float]) -> float:
    ordered = sorted(values)
    return ordered[max(0, math.ceil(len(ordered) * 0.95) - 1)]


def attach_error_collector(page: Page, errors: list[dict[str, str]]) -> None:
    page.on(
        "pageerror",
        lambda error: errors.append(
            {
                "message": str(error),
                "stack": getattr(error, "stack", "") or "",
                "url": page.url,
            }
        ),
    )


async def wait_interactive(page: Page, *, all_fields: bool = False) -> None:
    await page.wait_for_selector(
        '[data-parameter-name="TARGETS"][data-up-interactive="true"]'
    )
    if all_fields:
        await page.wait_for_function(
            "() => document.querySelectorAll('[data-up-interactive=\"true\"]').length === 5"
        )


async def benchmark_navigation(
    browser: Browser, errors: list[dict[str, str]]
) -> list[float]:
    durations: list[float] = []
    for index in range(6):
        page = await browser.new_page(viewport={"width": 1440, "height": 1100})
        attach_error_collector(page, errors)
        started = time.perf_counter()
        await page.goto(BUILD_URL, wait_until="domcontentloaded")
        await wait_interactive(page)
        elapsed_ms = (time.perf_counter() - started) * 1000
        if index > 0:
            durations.append(elapsed_ms)
        await page.close()
    assert p95(durations) <= 2500, durations
    return durations


async def model_payload_bytes(page: Page) -> int:
    result = await page.evaluate(
        """async () => {
          const field = document.querySelector('[data-parameter-name="TARGETS"]');
          const form = field.closest('form');
          const action = new URL(form.action || location.href, location.href);
          action.pathname = action.pathname.replace(/\\/build\\/?$/, '/');
          action.search = '';
          const endpoint = new URL('unified-parameters/api/v1/resolve', action);
          const headers = {'Content-Type': 'application/json'};
          if (document.head.dataset.crumbHeader && document.head.dataset.crumbValue) {
            headers[document.head.dataset.crumbHeader] = document.head.dataset.crumbValue;
          }
          const response = await fetch(endpoint, {
            method: 'POST', credentials: 'same-origin', headers,
            body: JSON.stringify({parameter: 'TARGETS'})
          });
          const text = await response.text();
          if (!response.ok) throw new Error(text);
          return new TextEncoder().encode(text).length;
        }"""
    )
    assert result <= 5 * 1024 * 1024, result
    return int(result)


async def benchmark_search(page: Page) -> list[float]:
    queries = [
        "Target 0001",
        "9999",
        "target-50",
        "Primary",
        "Secondary",
        "canary",
        "international",
        "release",
        "no-such-option",
        "Target",
    ] * 10
    timings = await page.evaluate(
        """async (queries) => {
          const search = document.querySelector(
            '[data-parameter-name="TARGETS"] .unified-parameters-search'
          );
          const timings = [];
          for (const query of queries) {
            const started = performance.now();
            search.value = query;
            search.dispatchEvent(new Event('input', {bubbles: true}));
            await new Promise(requestAnimationFrame);
            await new Promise(requestAnimationFrame);
            timings.push(performance.now() - started);
          }
          search.value = '';
          search.dispatchEvent(new Event('input', {bubbles: true}));
          return timings;
        }""",
        queries,
    )
    assert p95(timings) <= 100, p95(timings)
    return [float(value) for value in timings]


async def benchmark_selection(page: Page) -> list[float]:
    timings = await page.evaluate(
        """async () => {
          const checkbox = document.querySelector(
            '[data-parameter-name="FEATURES"] .unified-parameters-option input[type="checkbox"]'
          );
          const timings = [];
          for (let index = 0; index < 100; index += 1) {
            const started = performance.now();
            checkbox.click();
            await new Promise(requestAnimationFrame);
            timings.push(performance.now() - started);
          }
          return timings;
        }"""
    )
    assert p95(timings) <= 100, p95(timings)
    return [float(value) for value in timings]


async def benchmark_validation(page: Page) -> list[float]:
    timings = await page.evaluate(
        """async () => {
          const form = document.querySelector('[data-parameter-name="TARGETS"]').closest('form');
          const action = new URL(form.action || location.href, location.href);
          action.pathname = action.pathname.replace(/\\/build\\/?$/, '/');
          action.search = '';
          const endpoint = new URL('unified-parameters/api/v1/validate', action);
          const headers = {'Content-Type': 'application/json'};
          if (document.head.dataset.crumbHeader && document.head.dataset.crumbValue) {
            headers[document.head.dataset.crumbHeader] = document.head.dataset.crumbValue;
          }
          const values = {
            CHANNEL: 'staging',
            RELEASE: 'stable',
            DEPLOY_MODE: 'safe',
            REGIONS: ['eu-central', 'us-east'],
            FEATURES: ['feature-a'],
            TARGETS: ['target-0001', 'target-0002']
          };
          const timings = [];
          for (let index = 0; index < 6; index += 1) {
            const started = performance.now();
            const response = await fetch(endpoint, {
              method: 'POST', credentials: 'same-origin', headers,
              body: JSON.stringify({values})
            });
            if (!response.ok) throw new Error(await response.text());
            const elapsed = performance.now() - started;
            if (index > 0) timings.push(elapsed);
          }
          return timings;
        }"""
    )
    assert p95(timings) <= 1000, timings
    return [float(value) for value in timings]


async def accessibility_audit(page: Page) -> dict[str, object]:
    report = await page.evaluate(
        """() => {
          function visible(element) {
            const style = getComputedStyle(element);
            return style.display !== 'none' && style.visibility !== 'hidden' && element.offsetParent !== null;
          }
          function parseColor(value) {
            const match = value.match(/rgba?\\(([^)]+)\\)/);
            if (!match) return null;
            const parts = match[1].split(/[, ]+/).filter(Boolean).map(Number);
            return {r: parts[0], g: parts[1], b: parts[2], a: parts.length > 3 ? parts[3] : 1};
          }
          function luminance(color) {
            const channels = [color.r, color.g, color.b].map((value) => {
              const normalized = value / 255;
              return normalized <= 0.03928
                ? normalized / 12.92
                : Math.pow((normalized + 0.055) / 1.055, 2.4);
            });
            return channels[0] * 0.2126 + channels[1] * 0.7152 + channels[2] * 0.0722;
          }
          function background(element) {
            let current = element;
            while (current) {
              const color = parseColor(getComputedStyle(current).backgroundColor);
              if (color && color.a > 0.95) return color;
              current = current.parentElement;
            }
            return {r: 255, g: 255, b: 255, a: 1};
          }
          const duplicateIds = [];
          const seen = new Set();
          document.querySelectorAll('.unified-parameters-field [id]').forEach((element) => {
            if (seen.has(element.id)) duplicateIds.push(element.id);
            seen.add(element.id);
          });
          const unnamed = [];
          document.querySelectorAll(
            '.unified-parameters-field button, .unified-parameters-field input, ' +
            '.unified-parameters-field select, .unified-parameters-field summary'
          ).forEach((element) => {
            if (!visible(element) || element.type === 'hidden') return;
            const name = element.getAttribute('aria-label') || element.title ||
              element.textContent.trim() || (element.labels && element.labels.length
                ? Array.from(element.labels).map((label) => label.textContent.trim()).join(' ')
                : '');
            if (!name) unnamed.push(element.outerHTML.slice(0, 160));
          });
          const missingDescriptions = [];
          document.querySelectorAll('[aria-describedby]').forEach((element) => {
            element.getAttribute('aria-describedby').split(/\\s+/).forEach((id) => {
              if (id && !document.getElementById(id)) missingDescriptions.push(id);
            });
          });
          const contrast = [];
          document.querySelectorAll(
            '.unified-parameters-field strong, .unified-parameters-field small, ' +
            '.unified-parameters-status, .unified-parameters-group-toggle, ' +
            '.unified-parameters-group-select, .unified-parameters-chip'
          ).forEach((element) => {
            if (!visible(element) || element.closest('[aria-disabled="true"]')) return;
            const foreground = parseColor(getComputedStyle(element).color);
            const backdrop = background(element);
            if (!foreground) return;
            const lighter = Math.max(luminance(foreground), luminance(backdrop));
            const darker = Math.min(luminance(foreground), luminance(backdrop));
            const ratio = (lighter + 0.05) / (darker + 0.05);
            if (ratio < 4.5) contrast.push({text: element.textContent.trim(), ratio});
          });
          return {duplicateIds, unnamed, missingDescriptions, contrast};
        }"""
    )
    assert not report["duplicateIds"], report
    assert not report["unnamed"], report
    assert not report["missingDescriptions"], report
    assert not report["contrast"], report
    return report


async def keyboard_and_interaction_checks(page: Page) -> None:
    release = page.locator('[data-parameter-name="RELEASE"]')
    release_search = release.locator(".unified-parameters-search")
    await release_search.fill("release-42")
    await release_search.press("Enter")
    assert (
        await release.locator(
            'input[data-up-generated-value][value="release-42"]'
        ).count()
        == 1
    )
    assert "Custom value" in await release.locator(
        ".unified-parameters-status"
    ).inner_text()

    target = page.locator('[data-parameter-name="TARGETS"]')
    chips = target.locator(".unified-parameters-chip")
    assert [text.replace("\n", "") for text in await chips.all_inner_texts()] == [
        "Target 0001x",
        "Target 0002x",
    ]

    target_search = target.locator(".unified-parameters-search")
    await target_search.focus()
    await target_search.press("Backspace")
    assert await chips.count() == 1
    assert "Target 0002" not in " ".join(await chips.all_inner_texts())

    await target_search.fill("Target 0002")
    await target.locator(".unified-parameters-option input").check()
    await target_search.fill("international")
    assert await target.locator(".unified-parameters-option").count() == 1
    await target_search.fill("canary")
    assert await target.locator(".unified-parameters-option").count() == 1
    await target_search.fill("")

    features = page.locator('[data-parameter-name="FEATURES"]')
    first_toggle = features.locator(".unified-parameters-group-toggle").first
    assert await first_toggle.get_attribute("aria-expanded") == "true"
    await first_toggle.click()
    assert await first_toggle.get_attribute("aria-expanded") == "false"
    await first_toggle.click()
    assert await first_toggle.get_attribute("aria-expanded") == "true"

    stable_group = features.locator(".unified-parameters-group-section").first
    first_checkbox = stable_group.locator('input[type="checkbox"]').first
    second_checkbox = stable_group.locator('input[type="checkbox"]').nth(1)
    await first_checkbox.focus()
    await first_checkbox.press("ArrowDown")
    assert await second_checkbox.evaluate("element => element === document.activeElement")

    await stable_group.locator(".unified-parameters-group-select").click()
    experimental_group = features.locator(".unified-parameters-group-section").nth(1)
    await experimental_group.locator(".unified-parameters-group-select").click()
    status = await features.locator(".unified-parameters-status").inner_text()
    assert "1 disabled skipped" in status
    details = stable_group.locator(".unified-parameters-details").first
    await details.locator("summary").click()
    assert "Machine value: feature-a" in await details.inner_text()

    assert await page.locator(".unified-parameters-option").count() <= MAX_RENDERED_OPTIONS
    assert await page.locator('input[type="radio"][value="legacy"]:disabled').count() == 1
    regions_select = page.locator(
        '[data-parameter-name="REGIONS"] select.unified-parameters-native-list'
    )
    assert await regions_select.count() == 1
    assert await regions_select.evaluate("element => element.multiple")


async def configuration_preview_check(page: Page) -> None:
    await page.goto(
        BASE_URL + "/job/up-config-e2e/configure",
        wait_until="networkidle",
    )
    panel = page.locator("[data-up-import-panel]").first
    await panel.locator("textarea").fill(
        """options:
  - value: android
    label: Android
    group: Mobile
  - value: web
    label: Web
    tags: [browser, release]
  - value: disabled
    disabled: true
    disabledReason: Maintenance
"""
    )
    await panel.locator("[data-up-import-preview-button]").click()
    status = panel.locator("[data-up-import-status]")
    await status.wait_for(state="visible")
    await page.wait_for_function(
        "element => element.textContent.includes('3 option(s) parsed')",
        arg=await status.element_handle(),
    )
    generated = await panel.locator("[data-up-import-generated]").inner_text()
    assert "Pipeline" in generated
    assert "Typed REST body" in generated
    assert "Environment export preview" in generated
    assert await panel.locator("tbody tr").count() == 3
    # Avoid a Jenkins 2.568.2 breadcrumb overflow race while the repeatable
    # editor changes page height; the button's own click handler still runs.
    await panel.locator("[data-up-import-apply-button]").evaluate(
        "button => button.click()"
    )
    assert "applied" in await status.inner_text()
    await page.screenshot(path=GALLERY_DIR / "configuration-preview.png", full_page=True)


async def migration_preview_check(page: Page) -> None:
    await page.goto(
        BASE_URL + "/job/unified-parameters-migration-e2e/unified-parameters/",
        wait_until="networkidle",
    )
    text = await page.locator(".up-migration-page").inner_text()
    assert "Class B" in text
    assert "UnifiedMultiChoiceParameterDefinition" in text
    assert "unifiedMultiChoice" in text
    assert "does not modify the Job" in text
    await page.screenshot(path=GALLERY_DIR / "migration-preview.png", full_page=True)
    clone_form = page.locator('form[action$="cloneMigration"]')
    await clone_form.locator('input[name="cloneName"]').fill(
        "unified-migration-clone-e2e"
    )
    await clone_form.locator('input[name="acceptDecisions"]').check()
    await clone_form.get_by_role("button", name="Clone and apply").click()
    await page.wait_for_url(
        BASE_URL + "/job/unified-migration-clone-e2e/"
    )
    clone_config = await page.request.get(
        BASE_URL + "/job/unified-migration-clone-e2e/config.xml"
    )
    assert "UnifiedMultiChoiceParameterDefinition" in await clone_config.text()

    await page.goto(
        BASE_URL
        + "/job/unified-migration-clone-e2e/unified-parameters/",
        wait_until="networkidle",
    )
    assert "APPLIED" in await page.locator(".up-migration-records").inner_text()
    await page.get_by_role("button", name="Rollback").click()
    await page.wait_for_url(BASE_URL + "/job/unified-migration-clone-e2e/")
    rolled_back = await page.request.get(
        BASE_URL + "/job/unified-migration-clone-e2e/config.xml"
    )
    assert "ExtendedChoiceParameterDefinition" in await rolled_back.text()


async def provider_checks(browser: Browser, errors: list[dict[str, str]]) -> None:
    page = await browser.new_page(viewport={"width": 1440, "height": 1000})
    attach_error_collector(page, errors)
    await page.goto(PROVIDER_BUILD_URL, wait_until="networkidle")
    await page.wait_for_selector(
        '[data-parameter-name="ENVIRONMENTS"][data-up-interactive="true"]'
    )
    await page.wait_for_selector(
        '[data-parameter-name="ARTIFACT"][data-up-interactive="true"]'
    )
    field = page.locator('[data-parameter-name="ENVIRONMENTS"]')
    assert "staging" in " ".join(
        await field.locator(".unified-parameters-chip").all_inner_texts()
    ).lower()
    search = field.locator(".unified-parameters-search")
    await search.fill("production")
    await field.locator(".unified-parameters-option input").check()
    artifact = page.locator('[data-parameter-name="ARTIFACT"]')
    first_snapshot = await artifact.locator("[data-up-snapshot-id]").input_value()
    assert first_snapshot.startswith("up-snapshot-")
    await page.locator(
        '[data-parameter-name="ENVIRONMENT"] select.unified-parameters-native-list'
    ).select_option("production")
    await page.wait_for_function(
        """(previous) => {
          const field = document.querySelector('[data-parameter-name="ARTIFACT"]');
          return field.dataset.upState === 'ready' &&
            field.querySelector('[data-up-snapshot-id]').value !== previous;
        }
        """,
        arg=first_snapshot,
    )
    dependencies_json = await artifact.locator(
        "[data-up-dependencies-json]"
    ).input_value()
    assert json.loads(dependencies_json) == {"ENVIRONMENT": "production"}
    await page.screenshot(path=GALLERY_DIR / "provider.png", full_page=True)
    await page.get_by_role("button", name="Build").click()
    await page.wait_for_url(
        BASE_URL + "/job/unified-parameters-provider-e2e/"
    )
    build = await page.request.get(
        BASE_URL
        + "/job/unified-parameters-provider-e2e/lastBuild/api/json"
        + "?tree=actions[parameters[name,value,providerSnapshot[*]]]"
    )
    build_parameters = {
        parameter["name"]: parameter
        for action in (await build.json())["actions"]
        for parameter in action.get("parameters", [])
    }
    assert build_parameters["ARTIFACT"]["providerSnapshot"]["providerId"] == (
        "shared-environments"
    )

    health = await page.request.get(
        BASE_URL
        + "/job/unified-parameters-provider-e2e/unified-parameters/api/v1/providers/health"
    )
    health_body = await health.json()
    assert health_body["providers"][0]["healthy"] is True
    assert health_body["providers"][0]["id"] == "shared-environments"

    no_js = await browser.new_context(java_script_enabled=False)
    no_js_page = await no_js.new_page()
    await no_js_page.goto(PROVIDER_BUILD_URL, wait_until="domcontentloaded")
    text = await no_js_page.locator(
        '[data-parameter-name="ENVIRONMENTS"]'
    ).inner_text()
    assert "uses shared Provider shared-environments" in text
    assert (
        await no_js_page.get_by_role(
            "button", name="Build without JavaScript"
        ).count()
        == 0
    )
    await no_js.close()
    await page.close()


async def form_and_retirement_checks(
    browser: Browser, errors: list[dict[str, str]]
) -> None:
    page = await browser.new_page(viewport={"width": 1440, "height": 1050})
    attach_error_collector(page, errors)
    await page.goto(FORM_BUILD_URL, wait_until="networkidle")
    form = page.locator('[data-parameter-name="DEPLOYMENT"]')
    await form.wait_for(state="visible")
    await page.wait_for_selector(
        '[data-parameter-name="DEPLOYMENT"][data-up-form-interactive="true"]'
    )
    await form.get_by_label("Service name", exact=True).fill("catalog")
    await form.get_by_label("replicas", exact=True).fill("4")
    await form.get_by_label("region", exact=True).select_option("eu-central")
    conditional = form.locator('[data-up-form-layout="advanced-panel"]')
    assert await conditional.is_hidden()
    await form.get_by_label("Enable advanced settings", exact=True).check()
    assert await conditional.is_visible()
    await form.get_by_label("features", exact=True).select_option(["audit", "canary"])
    payload = json.loads(
        await form.locator("[data-up-form-value]").input_value()
    )
    assert payload == {
        "service": "catalog",
        "replicas": 4,
        "advanced": True,
        "region": "eu-central",
        "features": ["audit", "canary"],
        "notes": "review before production",
    }
    await page.screenshot(path=GALLERY_DIR / "unified-form.png", full_page=True)
    await page.get_by_role("button", name="Build").click()
    try:
        await page.wait_for_url(BASE_URL + "/job/unified-parameters-form-e2e/")
    except Exception as error:
        body = await page.locator("body").inner_text()
        raise AssertionError(
            "Unified Form browser submission did not redirect. "
            f"url={page.url}\n{body[-4000:]}"
        ) from error
    build = await page.request.get(
        BASE_URL
        + "/job/unified-parameters-form-e2e/lastBuild/api/json"
        + "?tree=actions[parameters[name,value,canonicalJson,structuredValue]]"
    )
    parameters = {
        parameter["name"]: parameter
        for action in (await build.json())["actions"]
        for parameter in action.get("parameters", [])
    }
    assert parameters["DEPLOYMENT"]["value"]["service"] == "catalog"
    assert parameters["DEPLOYMENT"]["value"]["replicas"] == 4

    await page.goto(
        BASE_URL + "/unified-parameters-retirement/", wait_until="networkidle"
    )
    text = await page.locator(".up-retirement").inner_text()
    assert "extended-choice-parameter" in text.lower()
    assert "Active Jobs" in text
    assert "Historical builds" in text
    await page.screenshot(
        path=GALLERY_DIR / "retirement-dashboard.png", full_page=True
    )

    no_js = await browser.new_context(java_script_enabled=False)
    no_js_page = await no_js.new_page()
    await no_js_page.goto(FORM_BUILD_URL, wait_until="domcontentloaded")
    fallback = no_js_page.locator(
        '[data-parameter-name="DEPLOYMENT"] [data-up-form-fallback]'
    )
    assert await fallback.is_visible()
    assert '"service":"checkout"' in await fallback.locator("textarea").input_value()
    await no_js.close()
    await page.close()


async def no_javascript_check(browser: Browser) -> None:
    context = await browser.new_context(
        java_script_enabled=False, viewport={"width": 1440, "height": 1100}
    )
    page = await context.new_page()
    await page.goto(BUILD_URL, wait_until="domcontentloaded")
    target = page.locator('[data-parameter-name="TARGETS"]')
    assert await target.locator('input[type="checkbox"][name="TARGETS"]').count() == 200
    assert "This parameter has 10000 options" in await target.inner_text()
    await page.screenshot(path=GALLERY_DIR / "no-javascript.png", full_page=True)
    job_api = BASE_URL + "/job/unified-parameters-e2e/api/json"
    before_response = await page.request.get(job_api)
    before = await before_response.json()
    before_number = (before.get("lastBuild") or {}).get("number", 0)
    await page.get_by_role("button", name="Build without JavaScript").click()
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        response = await page.request.get(job_api)
        current = await response.json()
        if current.get("lastBuild") and current["lastBuild"]["number"] > before_number:
            break
        await asyncio.sleep(0.25)
    else:
        raise AssertionError("No-JavaScript form submission did not create a build")
    await context.close()


async def browser_checks() -> tuple[dict[str, object], dict[str, object]]:
    GALLERY_DIR.mkdir(parents=True, exist_ok=True)
    RESULTS_DIR.mkdir(parents=True, exist_ok=True)
    errors: list[dict[str, str]] = []
    async with async_playwright() as playwright:
        browser = await playwright.chromium.launch(headless=True)
        navigation_ms = await benchmark_navigation(browser, errors)
        page = await browser.new_page(viewport={"width": 1440, "height": 1100})
        attach_error_collector(page, errors)
        await page.goto(BUILD_URL, wait_until="networkidle")
        await wait_interactive(page, all_fields=True)

        enhancement_ms = [
            float(value)
            for value in await page.locator("[data-up-interactive=true]").evaluate_all(
                "elements => elements.map(element => element.dataset.upEnhancementMs)"
            )
        ]
        assert max(enhancement_ms) <= 500, enhancement_ms
        payload_bytes = await model_payload_bytes(page)
        search_ms = await benchmark_search(page)
        selection_ms = await benchmark_selection(page)
        validation_ms = await benchmark_validation(page)
        accessibility = await accessibility_audit(page)
        rendered_option_nodes = await page.locator(".unified-parameters-option").count()

        await page.screenshot(path=GALLERY_DIR / "light.png", full_page=True)
        await page.emulate_media(color_scheme="dark", forced_colors="none")
        await page.screenshot(path=GALLERY_DIR / "dark.png", full_page=True)
        await page.emulate_media(color_scheme="light", forced_colors="active")
        await page.screenshot(path=GALLERY_DIR / "high-contrast.png", full_page=True)
        await page.emulate_media(color_scheme="light", forced_colors="none")

        await keyboard_and_interaction_checks(page)
        await page.locator(
            '[data-parameter-name="TARGETS"] .unified-parameters-search'
        ).focus()
        await page.screenshot(path=GALLERY_DIR / "keyboard-focus.png", full_page=True)

        target_search = page.locator(
            '[data-parameter-name="TARGETS"] .unified-parameters-search'
        )
        await target_search.fill("Target 9999")
        option = page.locator(
            '[data-parameter-name="TARGETS"] .unified-parameters-option'
        )
        assert await option.count() == 1
        await option.locator("input").check()
        await page.get_by_role("button", name="Build").click()
        await page.wait_for_url(BASE_URL + "/job/unified-parameters-e2e/")
        build_api = await page.request.get(
            BASE_URL
            + "/job/unified-parameters-e2e/1/api/json?tree=actions[parameters[name,value]]"
        )
        actions = (await build_api.json())["actions"]
        parameters = {
            parameter["name"]: parameter.get("value")
            for action in actions
            for parameter in action.get("parameters", [])
        }
        assert parameters["RELEASE"] == "release-42"
        await page.screenshot(path=GALLERY_DIR / "desktop-build-result.png", full_page=True)

        await page.goto(
            BASE_URL + "/job/unified-parameters-e2e/1/rebuild/parameterized",
            wait_until="networkidle",
        )
        assert (
            await page.locator('input[type="checkbox"][value="target-9999"]').count()
            == 1
        )
        await page.screenshot(path=GALLERY_DIR / "rebuild.png", full_page=True)

        await configuration_preview_check(page)
        await migration_preview_check(page)
        await provider_checks(browser, errors)
        await form_and_retirement_checks(browser, errors)

        mobile = await browser.new_page(viewport={"width": 390, "height": 844})
        attach_error_collector(mobile, errors)
        await mobile.goto(BUILD_URL, wait_until="networkidle")
        await wait_interactive(mobile, all_fields=True)
        width = await mobile.evaluate("document.documentElement.scrollWidth")
        assert width == 390, width
        await mobile.screenshot(path=GALLERY_DIR / "mobile.png", full_page=True)
        await mobile.close()

        await no_javascript_check(browser)

        relevant_errors = [
            error
            for error in errors
            if not (
                (
                    "/plugin/extended-choice-parameter/" in error["stack"]
                    and any(
                        message in error["message"]
                        for message in EXTERNAL_SOURCE_ERRORS
                    )
                )
                or (
                    "/jsbundles/header.js" in error["stack"]
                    and error["message"]
                    == "Cannot read properties of undefined (reading 'replace')"
                )
            )
        ]
        assert not relevant_errors, relevant_errors

        benchmark = {
            "generatedAt": datetime.now(timezone.utc).isoformat(),
            "environment": {
                "jenkinsImage": JENKINS_IMAGE,
                "browser": "Chromium " + browser.version,
                "host": platform.platform(),
                "cpuLimit": 4,
                "memoryLimitGiB": 8,
                "optionCount": OPTION_COUNT,
                "cache": "warm after one navigation",
            },
            "navigationToInteractiveMs": {
                "runs": navigation_ms,
                "p95": p95(navigation_ms),
                "gate": 2500,
            },
            "modelToInteractiveMs": {
                "runs": enhancement_ms,
                "max": max(enhancement_ms),
                "gate": 500,
            },
            "searchMs": {"runs": search_ms, "p95": p95(search_ms), "gate": 100},
            "selectionToggleMs": {
                "runs": selection_ms,
                "p95": p95(selection_ms),
                "gate": 100,
            },
            "validationMs": {
                "runs": validation_ms,
                "p95": p95(validation_ms),
                "gate": 1000,
            },
            "payloadBytes": {"value": payload_bytes, "gate": 5 * 1024 * 1024},
            "renderedOptionNodes": {
                "value": rendered_option_nodes,
                "gate": MAX_RENDERED_OPTIONS,
            },
        }
        accessibility_report = {
            "generatedAt": datetime.now(timezone.utc).isoformat(),
            "automatedChecks": accessibility,
            "keyboardFlows": [
                "chip removal with Backspace",
                "checkbox ArrowDown navigation",
                "group collapse/expand",
                "group selection with disabled skip announcement",
                "details disclosure and visible focus",
            ],
            "themes": ["light", "dark", "forced-colors high contrast"],
            "viewport": "390x844 verified without horizontal overflow",
            "knownExternalWarnings": [
                "Extended Choice legacy global scripts fail without their bundled jQuery assumptions.",
                "Jenkins 2.568.2 breadcrumb overflow menu can call xmlEscape with an undefined label during responsive or large-layout transitions.",
            ],
        }
        await browser.close()
        return benchmark, accessibility_report


def verify_migration() -> None:
    plan = json.loads((HOME_DIR / "migration-plan.json").read_text())
    assert plan["classification"] == "B", plan
    assert plan["targetClass"].endswith(
        "UnifiedMultiChoiceParameterDefinition"
    ), plan


async def main() -> None:
    prepare_plugins()
    start()
    try:
        wait_ready()
        benchmark, accessibility = await browser_checks()
        verify_migration()
        (RESULTS_DIR / "1.0-e2e-benchmark.json").write_text(
            json.dumps(benchmark, indent=2, ensure_ascii=False) + "\n"
        )
        (RESULTS_DIR / "1.0-accessibility.json").write_text(
            json.dumps(accessibility, indent=2, ensure_ascii=False) + "\n"
        )
        print("E2E SUCCESS")
    finally:
        stop()


if __name__ == "__main__":
    asyncio.run(main())
