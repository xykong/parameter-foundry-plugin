#!/usr/bin/env python3
"""Validate source migration fixture coverage without loading production data."""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FIXTURE_ROOT = ROOT / "docs" / "migration"
REQUIRED_PLUGINS = {
    "extended-choice-parameter",
    "uno-choice",
    "list-git-branches-parameter",
    "editable-choice",
    "custom-checkbox-parameter",
    "multiselect-parameter",
    "extensible-choice-parameter",
    "validating-string-parameter",
    "json-editor-parameter",
    "html-parameters",
    "json-parameter",
    "rest-list-parameter",
    "file-parameters",
}
FIXTURE_PATTERN = re.compile(r"^fixture:\s*([A-Za-z0-9][A-Za-z0-9._-]*)\s*$", re.MULTILINE)
PLUGIN_PATTERN = re.compile(
    r"^\s*(?:-\s*)?plugin:\s*([A-Za-z0-9][A-Za-z0-9._-]*)\s*$",
    re.MULTILINE,
)


def main() -> int:
    fixture_files = sorted(FIXTURE_ROOT.glob("*.yaml"))
    fixture_ids: dict[str, Path] = {}
    covered_plugins: set[str] = set()
    errors: list[str] = []

    for path in fixture_files:
        text = path.read_text(encoding="utf-8")
        matches = FIXTURE_PATTERN.findall(text)
        if not matches:
            errors.append(f"{path}: missing top-level fixture ID")
            continue
        fixture_id = matches[0]
        if fixture_id in fixture_ids:
            errors.append(f"{path}: duplicate fixture ID {fixture_id} (already in {fixture_ids[fixture_id]})")
        fixture_ids[fixture_id] = path
        covered_plugins.update(PLUGIN_PATTERN.findall(text))

    missing = sorted(REQUIRED_PLUGINS - covered_plugins)
    if missing:
        errors.append("missing fixture coverage: " + ", ".join(missing))

    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        return 1

    print(f"Validated {len(fixture_ids)} migration fixtures covering {len(covered_plugins)} source families.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
