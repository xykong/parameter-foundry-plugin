#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = ["playwright>=1.55,<2"]
# ///
"""Start a dedicated synthetic documentation controller using the UI Lab fixtures."""
import importlib.util
import os
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[2]
os.environ.pop("PF_LAB_FIXTURES", None)
spec = importlib.util.spec_from_file_location("foundry_e2e", ROOT / "tools/e2e/run-e2e.py")
lab = importlib.util.module_from_spec(spec)
spec.loader.exec_module(lab)
lab.CONTAINER = "parameter-foundry-docs-media"
lab.HOME_DIR = ROOT / "tools/e2e/.cache/docs-media-home"
source_plugins = lab.PLUGINS_DIR
lab.PLUGINS_DIR = ROOT / "tools/e2e/.cache/docs-media-plugins"
lab.PLUGINS_DIR.mkdir(exist_ok=True)
for plugin in source_plugins.glob("*.jpi"):
    shutil.copy2(plugin, lab.PLUGINS_DIR / plugin.name)
shutil.copy2(ROOT / "target/parameter-foundry.hpi", lab.PLUGINS_DIR / "parameter-foundry.jpi")
# The older fixture separates registry rows with a bare backslash; normalize
# those separators to escaped newlines only in this dedicated generated seed.
original_render = lab.render_init_groovy
def render_docs_seed():
    seed = original_render()
    text = seed.read_text()
    for host in ("registry", "harbor", "swr"):
        text = text.replace(chr(92) + host, chr(92) + "n" + host)
    output = ROOT / "tools/e2e/.cache/docs-media-seed.groovy"
    output.write_text(text)
    return output
lab.render_init_groovy = render_docs_seed
pins = {
    "apache-httpcomponents-client-5-api": ("io/jenkins/plugins", "5.6-191.vb_47e2b_41c698"),
    "antisamy-markup-formatter": ("org/jenkins-ci/plugins", "173.v680e3a_b_69ff3"),
    "workflow-step-api": ("org/jenkins-ci/plugins/workflow", "710.v3e456cc85233"),
}
for name, (group, version) in pins.items():
    source = Path.home() / ".m2/repository" / group / name / version / f"{name}-{version}.hpi"
    if not source.is_file():
        raise RuntimeError(f"Build with Maven first to resolve {name}:{version}")
    shutil.copy2(source, lab.PLUGINS_DIR / f"{name}.jpi")
lab.start()
lab.wait_ready()
print("Synthetic documentation controller ready at http://127.0.0.1:18081")
