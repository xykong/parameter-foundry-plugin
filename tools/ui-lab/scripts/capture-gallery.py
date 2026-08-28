#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = [
#   "playwright>=1.55,<2",
# ]
# ///

from __future__ import annotations

import argparse
import asyncio
import importlib.util
import json
import subprocess
from datetime import UTC, datetime
from html import escape
from pathlib import Path
from typing import Any

from playwright.async_api import BrowserContext, Page, async_playwright

LAB_DIR = Path(__file__).resolve().parents[1]
REPOSITORY_DIR = LAB_DIR.parents[1]
GALLERY_DIR = REPOSITORY_DIR / "docs" / "ui-gallery"
IMAGES_DIR = GALLERY_DIR / "images"
PROFILES_DIR = LAB_DIR / "profiles"
MANAGER_PATH = LAB_DIR / "scripts" / "manage-lab.py"
BASE_URL = "http://127.0.0.1:18080"
VIEWPORT = {"width": 1440, "height": 1100}


def load_manager() -> Any:
    spec = importlib.util.spec_from_file_location(
        "parameter_ui_lab_manager", MANAGER_PATH
    )
    if spec is None or spec.loader is None:
        raise RuntimeError("无法加载 manage-lab.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


MANAGER = load_manager()


def profile_names() -> list[str]:
    return MANAGER.profile_names()


def git_commit() -> str:
    result = subprocess.run(
        ["git", "rev-parse", "HEAD"],
        cwd=LAB_DIR,
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip()


def profile_metadata(profile: str) -> dict[str, Any]:
    return json.loads((PROFILES_DIR / profile / "profile.json").read_text())


async def wait_render(page: Page) -> None:
    await page.wait_for_load_state("domcontentloaded")
    await page.wait_for_timeout(1100)


async def screenshot(
    page: Page,
    profile: str,
    scenario: str,
    title: str,
    category: str,
    entries: list[dict[str, Any]],
) -> None:
    target_dir = IMAGES_DIR / profile
    target_dir.mkdir(parents=True, exist_ok=True)
    target = target_dir / f"{scenario}.png"
    await page.screenshot(path=target, full_page=True)
    entries.append(
        {
            "profile": profile,
            "scenario": scenario,
            "title": title,
            "category": category,
            "path": target.relative_to(GALLERY_DIR).as_posix(),
            "url": page.url,
            "viewport": VIEWPORT,
            "theme": "light",
        }
    )


async def interact_choice_page(page: Page, profile: str) -> None:
    if profile == "core":
        await page.locator("select").first.select_option("testing")
        textboxes = page.locator("input[type=text]:visible")
        if await textboxes.count():
            await textboxes.first.fill("feature/unified-parameters")
        checkbox = page.locator("input[type=checkbox]:visible")
        if await checkbox.count():
            await checkbox.first.uncheck(force=True)
        return

    if profile in {"extended-choice", "active-choices"}:
        multi = page.locator("select[multiple]").first
        if await multi.count():
            await multi.select_option(["android", "ohos"])
        checkbox = page.locator("input[type=checkbox][value=notifications]")
        if await checkbox.count():
            await checkbox.check(force=True)
        radio = page.locator("input[type=radio][value=debug]")
        if await radio.count():
            await radio.check(force=True)
        singles = page.locator("select:not([multiple])")
        if await singles.count():
            await singles.last.select_option("testing")
        return

    if profile == "extensible-choice":
        selects = page.locator("select")
        if await selects.count():
            await selects.first.select_option("testing")
            if await selects.count() > 1:
                await selects.last.select_option("eu-central")
        text_inputs = page.locator(
            "form input[type=text]:visible, form input:not([type]):visible"
        )
        if await text_inputs.count():
            await text_inputs.last.fill("custom-region")
        return

    if profile == "editable-choice":
        inputs = page.locator(
            "form input[type=text][name=value]:visible, "
            "form input:not([type])[name=value]:visible"
        )
        if await inputs.count():
            await inputs.first.fill("custom-channel")
            if await inputs.count() > 1:
                await inputs.last.fill("testing")
        return

    if profile == "rest-list":
        selects = page.locator("select")
        if await selects.count():
            await selects.first.select_option("testing")
            return
        combobox = page.get_by_role("combobox").first
        if await combobox.count():
            await combobox.click()
            option = page.get_by_text("Testing", exact=True)
            if await option.count():
                await option.click()
        return

    if profile == "custom-checkbox":
        checkbox = page.locator("input[type=checkbox][value=web]")
        if await checkbox.count():
            await checkbox.check(force=True)
        return

    if profile == "validating-string":
        textbox = page.locator("input[type=text][name=value]:visible").first
        await textbox.fill("not-semver")
        await textbox.press("Tab")
        await page.wait_for_timeout(900)
        return

    if profile == "multiselect":
        selects = page.locator("select")
        if await selects.count():
            await selects.first.select_option(label="Desktop")
            await page.wait_for_timeout(350)
            if await selects.count() > 1:
                await selects.nth(1).select_option(label="macOS")
                await page.wait_for_timeout(350)
        return

    if profile == "json-parameter":
        selects = page.locator("select")
        if await selects.count():
            await selects.first.select_option("Bob")
            await page.wait_for_timeout(1000)
        return

    if profile == "html-parameters":
        branch = page.locator("#html-parameters-branch")
        if await branch.count():
            await branch.fill("release/2.0")
        environment = page.locator("#html-parameters-env")
        if await environment.count():
            await environment.select_option("prod")
        tests = page.locator("#html-parameters-tests")
        if await tests.count():
            await tests.uncheck(force=True)
        return

    if profile == "json-editor":
        selects = page.locator(".json-editor select, [data-schemaid] select")
        if await selects.count():
            await selects.first.select_option("production")
        numbers = page.locator("input[type=number]:visible")
        if await numbers.count():
            await numbers.first.fill("6")


async def assert_default_state(page: Page, profile: str) -> None:
    if profile == "core":
        if await page.locator("select option").count() < 3:
            raise RuntimeError("Core Choice 未完整渲染")
        if await page.locator("input[type=checkbox]").count() < 1:
            raise RuntimeError("Core Boolean 未渲染")
        return

    if profile in {"extended-choice", "active-choices"}:
        if await page.locator("select[multiple] option").count() < 4:
            raise RuntimeError(f"{profile} 多选选项未完整渲染")
        if await page.locator("input[type=checkbox]").count() < 4:
            raise RuntimeError(f"{profile} Checkbox 未完整渲染")
        if await page.locator("input[type=radio]").count() < 3:
            raise RuntimeError(f"{profile} Radio 未完整渲染")
        return

    if profile == "extensible-choice":
        if await page.locator("select option").count() < 3:
            raise RuntimeError("Extensible Choice 固定 Provider 未加载")
        if (
            await page.locator(
                "form input[type=text]:visible, form input:not([type]):visible"
            ).count()
            < 1
        ):
            raise RuntimeError("Extensible Choice editable 控件未加载")
        return

    if profile == "editable-choice":
        if (
            await page.locator(
                "form input[type=text][name=value]:visible, "
                "form input:not([type])[name=value]:visible"
            ).count()
            < 2
        ):
            raise RuntimeError("Editable Choice 控件未完整加载")
        return

    if profile == "rest-list":
        options = page.locator("select option")
        if await options.count() < 4:
            raise RuntimeError("REST List Provider 未返回完整选项")
        labels = await options.all_text_contents()
        if any(not label.strip() for label in labels):
            raise RuntimeError("REST List Provider 存在空展示值")
        return

    if profile == "custom-checkbox":
        if (
            await page.locator(
                ".custom-checkbox-parameter input[type=checkbox]"
            ).count()
            < 5
        ):
            raise RuntimeError("Custom Checkbox 选项未完整渲染")
        return

    if profile == "validating-string":
        if await page.locator("input[type=text][name=value]:visible").count() < 1:
            raise RuntimeError("Validating String 控件未渲染")
        return

    if profile == "multiselect":
        if await page.locator("select").count() < 3:
            raise RuntimeError("Multiselect 级联下拉框未完整渲染")
        return

    if profile == "json-parameter":
        if await page.locator("select").count() < 2:
            raise RuntimeError("JSON Parameter 引用下拉框未完整渲染")
        return

    if profile == "html-parameters":
        if await page.locator("#html-parameters-branch").count() != 1:
            raise RuntimeError("HTML Parameters 表单未渲染")
        return

    if profile == "json-editor" and await page.locator("input, select").count() < 3:
        raise RuntimeError("Json Editor Schema 表单未渲染")


async def wait_last_build(context: BrowserContext) -> int:
    deadline = asyncio.get_running_loop().time() + 90
    while asyncio.get_running_loop().time() < deadline:
        response = await context.request.get(
            BASE_URL
            + "/job/ui-lab-pipeline/lastBuild/api/json?tree=number,building,result"
        )
        if response.status == 200:
            payload = await response.json()
            if not payload.get("building") and payload.get("result"):
                return int(payload["number"])
        await asyncio.sleep(1)
    raise RuntimeError("等待 Pipeline 构建完成超时")


async def wait_job_page(page: Page, timeout_seconds: int = 30) -> None:
    expected = BASE_URL + "/job/ui-lab-pipeline/"
    deadline = asyncio.get_running_loop().time() + timeout_seconds
    while asyncio.get_running_loop().time() < deadline:
        if page.url == expected:
            return
        await asyncio.sleep(0.1)
    raise RuntimeError(f"Pipeline Build 后未返回 Job 页面，当前 URL={page.url}")


async def capture_profile(
    browser: Any,
    profile: str,
    entries: list[dict[str, Any]],
) -> None:
    MANAGER.start(profile)
    context = await browser.new_context(
        viewport=VIEWPORT,
        color_scheme="light",
        locale="en-US",
    )
    page = await context.new_page()
    try:
        await page.goto(BASE_URL + "/job/ui-lab-freestyle/configure")
        await wait_render(page)
        await screenshot(
            page,
            profile,
            "freestyle-config",
            "Freestyle configuration",
            "configuration",
            entries,
        )

        if profile == "json-parameter":
            loading_request = True

            async def delay_first_reference_request(route: Any) -> None:
                nonlocal loading_request
                if loading_request:
                    loading_request = False
                    await asyncio.sleep(2.5)
                await route.continue_()

            await page.route("**/loadOptions", delay_first_reference_request)
            await page.goto(BASE_URL + "/job/ui-lab-freestyle/build?delay=0sec")
            await page.wait_for_load_state("domcontentloaded")
            await page.wait_for_timeout(250)
            await screenshot(
                page,
                profile,
                "reactive-loading",
                "Reactive request in flight",
                "loading",
                entries,
            )
            await page.wait_for_timeout(2800)
            await page.unroute("**/loadOptions")
        else:
            await page.goto(BASE_URL + "/job/ui-lab-freestyle/build?delay=0sec")
            await wait_render(page)
        await assert_default_state(page, profile)
        await screenshot(
            page,
            profile,
            "freestyle-default",
            "Freestyle default state",
            "default",
            entries,
        )

        await interact_choice_page(page, profile)
        await page.wait_for_timeout(350)
        interaction_title = "Freestyle user interaction"
        interaction_category = "interaction"
        if profile == "validating-string":
            interaction_title = "Freestyle validation error"
            interaction_category = "validation"
        elif profile == "json-parameter":
            interaction_title = "Freestyle reactive update"
            interaction_category = "reactive"
        await screenshot(
            page,
            profile,
            "freestyle-interaction",
            interaction_title,
            interaction_category,
            entries,
        )

        await page.goto(BASE_URL + "/job/ui-lab-pipeline/build?delay=0sec")
        await wait_render(page)
        await screenshot(
            page,
            profile,
            "pipeline-default",
            "Pipeline default state",
            "pipeline",
            entries,
        )

        await page.get_by_role("button", name="Build").click()
        await wait_job_page(page)
        build_number = await wait_last_build(context)

        await page.goto(BASE_URL + f"/job/ui-lab-pipeline/{build_number}/rebuild")
        await wait_render(page)
        retry = page.get_by_role("button", name="Retry using POST")
        if await retry.count():
            await retry.click()
            await wait_render(page)
        await screenshot(
            page,
            profile,
            "pipeline-rebuild",
            "Pipeline Rebuild state",
            "rebuild",
            entries,
        )

        if profile == "rest-list":
            for job, scenario, title, category in [
                ("ui-lab-empty", "provider-empty", "Provider empty result", "empty"),
                ("ui-lab-error", "provider-error", "Provider parsing error", "error"),
            ]:
                await page.goto(BASE_URL + f"/job/{job}/build?delay=0sec")
                await wait_render(page)
                await screenshot(page, profile, scenario, title, category, entries)
    finally:
        await context.close()
        MANAGER.stop()


def render_gallery(
    profiles: list[dict[str, Any]],
    screenshots: list[dict[str, Any]],
    generated_at: str,
) -> str:
    profile_buttons = "\n".join(
        (
            f'<button class="filter-chip" data-profile="{escape(item["id"])}">'
            f'<span style="--chip:{escape(item["accent"])}"></span>'
            f"{escape(item['title'])}</button>"
        )
        for item in profiles
    )
    cards = []
    metadata = {item["id"]: item for item in profiles}
    for item in screenshots:
        profile = metadata[item["profile"]]
        component_name = profile.get("plugin") or "jenkins-core"
        cards.append(
            f"""
            <article class="shot-card"
              data-profile="{escape(item["profile"])}"
              data-category="{escape(item["category"])}">
              <a class="shot-image" href="{escape(item["path"])}" target="_blank">
                <img src="{escape(item["path"])}" alt="{escape(profile["title"] + " " + item["title"])}" loading="lazy">
              </a>
              <div class="shot-body">
                <div class="shot-kicker">
                  <span style="--accent:{escape(profile["accent"])}"></span>
                  {escape(profile["title"])}
                </div>
                <h2>{escape(item["title"])}</h2>
                <p>{escape(profile["description"])}</p>
                <dl>
                  <div><dt>Plugin</dt><dd>{escape(component_name)}:{escape(profile["version"])}</dd></div>
                  <div><dt>State</dt><dd>{escape(item["category"])}</dd></div>
                </dl>
              </div>
            </article>
            """
        )

    document = f"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Jenkins Parameter Plugin UI Field Guide</title>
  <style>
    :root {{
      --ink: #17211d;
      --paper: #f5f0e4;
      --paper-strong: #fffaf0;
      --line: #c9c0ad;
      --blue: #165d7a;
      --orange: #df642d;
      --shadow: 0 18px 60px rgba(31, 37, 31, .14);
    }}
    * {{ box-sizing: border-box; }}
    html {{ scroll-behavior: smooth; }}
    body {{
      margin: 0;
      color: var(--ink);
      background:
        linear-gradient(rgba(23,33,29,.035) 1px, transparent 1px),
        linear-gradient(90deg, rgba(23,33,29,.035) 1px, transparent 1px),
        var(--paper);
      background-size: 28px 28px;
      font-family: Georgia, "Times New Roman", serif;
    }}
    .hero {{
      min-height: 58vh;
      padding: clamp(48px, 8vw, 120px) clamp(24px, 8vw, 128px) 60px;
      display: grid;
      grid-template-columns: minmax(0, 1.5fr) minmax(280px, .7fr);
      gap: 56px;
      align-items: end;
      border-bottom: 1px solid var(--line);
      background:
        radial-gradient(circle at 75% 20%, rgba(223,100,45,.17), transparent 28%),
        linear-gradient(135deg, rgba(22,93,122,.12), transparent 48%);
    }}
    .eyebrow, .shot-kicker, dt, .filter-title {{
      font-family: "SFMono-Regular", Consolas, monospace;
      text-transform: uppercase;
      letter-spacing: .12em;
      font-size: 12px;
    }}
    h1 {{
      max-width: 900px;
      margin: 12px 0 18px;
      font-size: clamp(52px, 8vw, 112px);
      line-height: .88;
      letter-spacing: -.055em;
      font-weight: 700;
    }}
    .lede {{
      max-width: 760px;
      margin: 0;
      font-size: clamp(18px, 2vw, 28px);
      line-height: 1.35;
    }}
    .hero-note {{
      padding: 28px;
      border: 1px solid var(--ink);
      background: var(--paper-strong);
      box-shadow: 10px 10px 0 var(--orange);
      font-size: 16px;
      line-height: 1.55;
    }}
    .filters {{
      position: sticky;
      top: 0;
      z-index: 10;
      display: flex;
      gap: 10px;
      align-items: center;
      flex-wrap: wrap;
      padding: 18px clamp(20px, 5vw, 72px);
      background: rgba(245,240,228,.94);
      backdrop-filter: blur(16px);
      border-bottom: 1px solid var(--line);
    }}
    button {{
      border: 1px solid var(--ink);
      border-radius: 999px;
      padding: 9px 14px;
      background: transparent;
      color: inherit;
      cursor: pointer;
      font: 600 13px "SFMono-Regular", Consolas, monospace;
    }}
    button:hover, button.active {{ color: var(--paper-strong); background: var(--ink); }}
    .filter-chip span, .shot-kicker span {{
      display: inline-block;
      width: 9px;
      height: 9px;
      margin-right: 7px;
      border-radius: 50%;
      background: var(--chip, var(--accent));
    }}
    main {{
      padding: 54px clamp(20px, 5vw, 72px) 110px;
    }}
    .grid {{
      display: grid;
      grid-template-columns: repeat(12, minmax(0, 1fr));
      gap: 26px;
    }}
    .shot-card {{
      grid-column: span 6;
      overflow: hidden;
      border: 1px solid var(--line);
      background: var(--paper-strong);
      box-shadow: var(--shadow);
      transition: transform .2s ease;
    }}
    .shot-card:nth-child(5n + 1) {{ grid-column: span 8; }}
    .shot-card:nth-child(5n + 2) {{ grid-column: span 4; }}
    .shot-card:hover {{ transform: translateY(-4px); }}
    .shot-image {{
      display: block;
      height: 420px;
      overflow: auto;
      background: #fff;
      border-bottom: 1px solid var(--line);
    }}
    .shot-image img {{
      display: block;
      width: 100%;
      height: auto;
    }}
    .shot-body {{ padding: 26px; }}
    .shot-kicker {{ color: #5f665e; }}
    h2 {{ margin: 12px 0 8px; font-size: 28px; line-height: 1.05; }}
    .shot-body p {{ min-height: 48px; line-height: 1.5; color: #555e56; }}
    dl {{ margin: 20px 0 0; display: grid; gap: 8px; }}
    dl div {{ display: grid; grid-template-columns: 84px 1fr; gap: 12px; }}
    dt {{ color: #777; }}
    dd {{ margin: 0; font: 13px "SFMono-Regular", Consolas, monospace; overflow-wrap: anywhere; }}
    .hidden {{ display: none; }}
    footer {{
      padding: 30px clamp(20px, 5vw, 72px);
      border-top: 1px solid var(--line);
      font: 13px "SFMono-Regular", Consolas, monospace;
    }}
    @media (max-width: 900px) {{
      .hero {{ grid-template-columns: 1fr; min-height: auto; }}
      .shot-card, .shot-card:nth-child(5n + 1), .shot-card:nth-child(5n + 2) {{ grid-column: 1 / -1; }}
      .shot-image {{ height: 320px; }}
      .filter-title {{ width: 100%; }}
    }}
  </style>
</head>
<body>
  <header class="hero">
    <div>
      <div class="eyebrow">Real Jenkins UI · Reproducible visual benchmark</div>
      <h1>Parameter Plugin Field Guide</h1>
      <p class="lede">Five real plugins. Five repeatable states each. No mockups—every image comes from Jenkins {escape(MANAGER.JENKINS_CORE)} running the pinned plugin.</p>
    </div>
    <aside class="hero-note">
      Generated {escape(generated_at)}.<br>
      Runtime is isolated, unsecured, localhost-only, and contains no production data.
    </aside>
  </header>
  <nav class="filters" aria-label="Gallery filters">
    <span class="filter-title">Plugin</span>
    <button class="active" data-profile="all">All plugins</button>
    {profile_buttons}
    <span class="filter-title filter-break">State</span>
    <button class="active" data-category="all">All states</button>
    <button data-category="configuration">Configuration</button>
    <button data-category="default">Default</button>
    <button data-category="interaction">Interaction</button>
    <button data-category="pipeline">Pipeline</button>
    <button data-category="rebuild">Rebuild</button>
    <button data-category="validation">Validation</button>
    <button data-category="reactive">Reactive</button>
    <button data-category="empty">Empty</button>
    <button data-category="error">Error</button>
    <button data-category="loading">Loading</button>
  </nav>
  <main>
    <section class="grid">
      {"".join(cards)}
    </section>
  </main>
  <footer>Jenkins Parameter Plugin UI Lab · source manifest: manifest.json</footer>
  <script>
    const profileButtons = [...document.querySelectorAll('[data-profile]')];
    const categoryButtons = [...document.querySelectorAll('[data-category]')];
    const cards = [...document.querySelectorAll('.shot-card')];
    let selectedProfile = 'all';
    let selectedCategory = 'all';
    const applyFilters = () => cards.forEach(card => {{
      const profileMatch = selectedProfile === 'all' || card.dataset.profile === selectedProfile;
      const categoryMatch = selectedCategory === 'all' || card.dataset.category === selectedCategory;
      card.classList.toggle('hidden', !profileMatch || !categoryMatch);
    }});
    profileButtons.forEach(button => button.addEventListener('click', () => {{
      profileButtons.forEach(item => item.classList.remove('active'));
      button.classList.add('active');
      selectedProfile = button.dataset.profile;
      applyFilters();
    }}));
    categoryButtons.forEach(button => button.addEventListener('click', () => {{
      categoryButtons.forEach(item => item.classList.remove('active'));
      button.classList.add('active');
      selectedCategory = button.dataset.category;
      applyFilters();
    }}));
  </script>
</body>
</html>
"""
    return "\n".join(line.rstrip() for line in document.splitlines()) + "\n"


def render_readme(
    profiles: list[dict[str, Any]],
    screenshots: list[dict[str, Any]],
) -> str:
    by_profile: dict[str, list[dict[str, Any]]] = {}
    for item in screenshots:
        by_profile.setdefault(item["profile"], []).append(item)
    lines = [
        "# Jenkins 参数插件真实 UI Gallery",
        "",
        "图片由隔离 Jenkins 和真实插件自动生成，不是手工 mock。",
        "",
        "交互式版本见 [`index.html`](./index.html)，版本和场景元数据见",
        "[`manifest.json`](./manifest.json)。",
        "",
    ]
    for profile in profiles:
        component_name = profile.get("plugin") or "jenkins-core"
        lines.extend(
            [
                f"## {profile['title']}",
                "",
                f"`{component_name}:{profile['version']}` · {profile['status']}",
                "",
            ]
        )
        for item in by_profile.get(profile["id"], []):
            lines.extend(
                [
                    f"### {item['title']}",
                    "",
                    (
                        f"[![{profile['title']} {item['title']}]"
                        f"({item['path']})]({item['path']})"
                    ),
                    "",
                ]
            )
    return "\n".join(lines).rstrip() + "\n"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="生成 Jenkins 参数插件真实 UI Gallery。"
    )
    parser.add_argument(
        "--profile",
        action="append",
        choices=profile_names(),
        dest="profiles",
    )
    return parser.parse_args()


async def async_main(profiles_to_capture: list[str]) -> None:
    GALLERY_DIR.mkdir(parents=True, exist_ok=True)
    IMAGES_DIR.mkdir(parents=True, exist_ok=True)
    entries: list[dict[str, Any]] = []
    generated_at = datetime.now(UTC).isoformat()

    async with async_playwright() as playwright:
        browser = await playwright.chromium.launch(channel="chrome", headless=True)
        try:
            for profile in profiles_to_capture:
                print(f"CAPTURE|{profile}|start", flush=True)
                await capture_profile(browser, profile, entries)
                print(f"CAPTURE|{profile}|complete", flush=True)
        finally:
            await browser.close()
            MANAGER.stop()

    profiles = []
    for profile in profiles_to_capture:
        metadata = profile_metadata(profile)
        metadata["id"] = profile
        metadata["inventory"] = MANAGER.plugin_inventory(profile)
        profiles.append(metadata)

    manifest = {
        "schemaVersion": 1,
        "generatedAt": generated_at,
        "generatedCommit": git_commit(),
        "jenkinsCore": MANAGER.JENKINS_CORE,
        "jenkinsImage": MANAGER.JENKINS_IMAGE,
        "viewport": VIEWPORT,
        "profiles": profiles,
        "screenshots": entries,
    }
    (GALLERY_DIR / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n"
    )
    (GALLERY_DIR / "index.html").write_text(
        render_gallery(profiles, entries, generated_at)
    )
    (GALLERY_DIR / "README.md").write_text(render_readme(profiles, entries))
    print(
        f"GALLERY|profiles={len(profiles)}|screenshots={len(entries)}|"
        f"path={GALLERY_DIR / 'index.html'}"
    )


def main() -> None:
    args = parse_args()
    profiles = args.profiles or profile_names()
    asyncio.run(async_main(profiles))


if __name__ == "__main__":
    main()
