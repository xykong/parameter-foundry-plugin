#!/usr/bin/env python3
"""Read-only Jenkins parameter inventory with redacted output.

The tool intentionally reports aggregate counts only. It does not write Job
XML, option values, credentials, tokens, or endpoint-specific configuration.
"""

from __future__ import annotations

import argparse
import base64
import concurrent.futures
import json
import os
import re
import ssl
import stat
import sys
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from collections import Counter, deque
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


@dataclass(frozen=True)
class AuditConfig:
    base_url: str
    username: str
    secret: str
    auth_file: Path | None
    workers: int
    max_items: int
    timeout: float


class JenkinsClient:
    def __init__(self, config: AuditConfig) -> None:
        self._base_url = config.base_url.rstrip("/")
        encoded = base64.b64encode(f"{config.username}:{config.secret}".encode()).decode()
        self._headers = {"Authorization": f"Basic {encoded}"}
        self._timeout = config.timeout
        self._context = ssl.create_default_context()

    def get_json(self, path: str) -> dict[str, Any]:
        status, body = self.get(path)
        if status != 200:
            raise RuntimeError(f"HTTP {status}")
        try:
            decoded = json.loads(body)
        except json.JSONDecodeError as exc:
            raise RuntimeError("invalid JSON response") from exc
        if not isinstance(decoded, dict):
            raise RuntimeError("JSON response is not an object")
        return decoded

    def get(self, path: str) -> tuple[int, bytes]:
        request = urllib.request.Request(self._base_url + path, headers=self._headers)
        try:
            with urllib.request.urlopen(request, timeout=self._timeout, context=self._context) as response:
                return response.status, response.read()
        except urllib.error.HTTPError as exc:
            return exc.code, b""


def load_secret(auth_file: Path, key: str) -> str:
    mode = stat.S_IMODE(auth_file.stat().st_mode)
    if mode & 0o077:
        raise ValueError(f"auth file must be readable only by its owner, got mode {mode:04o}")
    payload = json.loads(auth_file.read_text(encoding="utf-8"))
    credentials = payload.get("credentials", payload)
    secret = credentials.get(key)
    if not isinstance(secret, str) or not secret:
        raise ValueError(f"auth file does not contain a non-empty secret at key {key!r}")
    return secret


def item_path(url: str) -> str:
    parsed = urllib.parse.urlparse(url)
    path = parsed.path
    return path if path.endswith("/") else path + "/"


def discover_items(client: JenkinsClient, max_items: int) -> tuple[list[dict[str, str]], int]:
    root = client.get_json("/api/json?" + urllib.parse.urlencode({"tree": "jobs[name,url,_class]"}))
    queue: deque[dict[str, Any]] = deque(root.get("jobs", []))
    items: list[dict[str, str]] = []
    seen: set[str] = set()
    errors = 0

    while queue and len(items) < max_items:
        raw = queue.popleft()
        if not isinstance(raw, dict) or not isinstance(raw.get("url"), str):
            continue
        path = item_path(raw["url"])
        if path in seen:
            continue
        seen.add(path)
        items.append({"path": path, "class": str(raw.get("_class", ""))})
        try:
            children = client.get_json(path + "api/json?" + urllib.parse.urlencode({"tree": "jobs[name,url,_class]"}))
        except RuntimeError:
            errors += 1
            continue
        nested = children.get("jobs", [])
        if isinstance(nested, list):
            queue.extend(item for item in nested if isinstance(item, dict))
    return items, errors


def child_text(node: ET.Element, name: str) -> str:
    child = node.find(name)
    if child is None or child.text is None:
        return ""
    return child.text.strip()


def has_text(node: ET.Element, names: tuple[str, ...]) -> bool:
    return any(child_text(node, name) for name in names)


def normalized_class_name(xml_tag: str) -> str:
    return xml_tag.replace("__", ".").replace("_", ".")


def classify_definition(node: ET.Element) -> tuple[str, str, dict[str, Any]]:
    raw_class = node.tag
    class_name = normalized_class_name(raw_class)
    mode = child_text(node, "type")
    detail: dict[str, Any] = {}

    if class_name.endswith("ExtendedChoiceParameterDefinition"):
        value = child_text(node, "value")
        default = child_text(node, "defaultValue")
        delimiter = child_text(node, "multiSelectDelimiter") or ","
        options = [part.strip() for part in value.split(delimiter)] if value else []
        defaults = [part.strip() for part in default.split(delimiter)] if default else []
        detail.update(
            dynamic=has_text(
                node,
                (
                    "propertyFile",
                    "propertyKey",
                    "groovyScript",
                    "groovyScriptFile",
                    "defaultPropertyFile",
                    "defaultPropertyKey",
                    "defaultGroovyScript",
                    "defaultGroovyScriptFile",
                    "javascript",
                    "javascriptFile",
                ),
            ),
            delimiter=delimiter,
            quote_value=child_text(node, "quoteValue").lower() == "true",
            missing_default=not bool(default),
            stale_default=any(item and item not in options for item in defaults),
            empty_option=any(not item for item in options),
        )
        return "extended-choice", mode or "UNKNOWN", detail

    if class_name.endswith("CascadeChoiceParameter"):
        detail["dependency_declared"] = bool(
            child_text(node, "referencedParameters") or child_text(node, "referencedParameter")
        )
        return "active-choices", mode or "CascadeChoiceParameter", detail

    if class_name.endswith("ChoiceParameter") or class_name.endswith("DynamicReferenceParameter"):
        return "active-choices", mode or class_name.rsplit(".", 1)[-1], detail

    if class_name.endswith("ListGitBranchesParameterDefinition"):
        return "list-git-branches", mode or "UNKNOWN", detail

    if class_name.startswith("io.jenkins.plugins.parameterfoundry.companion.file."):
        kind = class_name.rsplit(".", 1)[-1]
        return "foundry-file", (child_text(node, "deliveryMode") or "workspace") if kind == "FoundryFileParameterDefinition" else kind, detail

    if class_name.endswith("FileParameterDefinition") or class_name.endswith("StashedFileParameterDefinition"):
        return "file", class_name.rsplit(".", 1)[-1], detail

    if class_name.startswith("hudson.model.") or class_name.startswith("jenkins.model."):
        return "jenkins-core", class_name.rsplit(".", 1)[-1], detail

    return "other", class_name.rsplit(".", 1)[-1], detail


def inspect_item(client: JenkinsClient, item: dict[str, str]) -> tuple[str, int, Counter[str], Counter[str], Counter[str]]:
    status, body = client.get(item["path"] + "config.xml")
    if status != 200:
        return "config-read-error", 0, Counter(), Counter(), Counter()
    try:
        root = ET.fromstring(body)
    except ET.ParseError:
        return "config-parse-error", 0, Counter(), Counter(), Counter()

    definitions = 0
    classes: Counter[str] = Counter()
    modes: Counter[str] = Counter()
    families: Counter[str] = Counter()
    for property_node in root.iter():
        if not property_node.tag.endswith("ParametersDefinitionProperty"):
            continue
        parameter_definitions = property_node.find("parameterDefinitions")
        if parameter_definitions is None:
            continue
        for definition in list(parameter_definitions):
            family, mode, detail = classify_definition(definition)
            class_name = normalized_class_name(definition.tag)
            definitions += 1
            classes[class_name] += 1
            modes[f"{family}::{mode}"] += 1
            if family == "extended-choice":
                for key in (
                    "dynamic",
                    "quote_value",
                    "missing_default",
                    "stale_default",
                    "empty_option",
                ):
                    families[f"extended-choice::{key}::{str(detail[key]).lower()}"] += 1
            elif family == "active-choices":
                families[
                    "active-choices::dependency_declared::"
                    + str(detail.get("dependency_declared", False)).lower()
                ] += 1
            else:
                families[family] += 1
    return "ok", definitions, classes, modes, families


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", required=True, help="Jenkins base URL; no default is used.")
    parser.add_argument("--username", default="admin")
    parser.add_argument("--secret")
    parser.add_argument("--auth-file", type=Path)
    parser.add_argument("--auth-key", default="jenkins_oidc_escape_hatch_secret")
    parser.add_argument("--workers", type=int, default=8)
    parser.add_argument("--max-items", type=int, default=5000)
    parser.add_argument("--timeout", type=float, default=30.0)
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:])
    if args.workers < 1 or args.workers > 32:
        raise SystemExit("--workers must be between 1 and 32")
    if args.max_items < 1 or args.max_items > 100_000:
        raise SystemExit("--max-items must be between 1 and 100000")
    if bool(args.secret) == bool(args.auth_file):
        raise SystemExit("provide exactly one of --secret or --auth-file")

    secret = args.secret or load_secret(args.auth_file, args.auth_key)
    config = AuditConfig(
        base_url=args.base_url,
        username=args.username,
        secret=secret,
        auth_file=args.auth_file,
        workers=args.workers,
        max_items=args.max_items,
        timeout=args.timeout,
    )
    client = JenkinsClient(config)
    items, discovery_errors = discover_items(client, config.max_items)
    status_counts: Counter[str] = Counter()
    class_counts: Counter[str] = Counter()
    mode_counts: Counter[str] = Counter()
    family_counts: Counter[str] = Counter()
    with concurrent.futures.ThreadPoolExecutor(max_workers=config.workers) as executor:
        futures = [executor.submit(inspect_item, client, item) for item in items]
        for future in concurrent.futures.as_completed(futures):
            status, definition_count, classes, modes, families = future.result()
            status_counts[status] += 1
            class_counts.update(classes)
            mode_counts.update(modes)
            family_counts.update(families)
            if definition_count == 0:
                status_counts["items-without-parameters"] += 1

    report = {
        "auditedAt": datetime.now(timezone.utc).isoformat(),
        "baseUrlHost": urllib.parse.urlparse(config.base_url).hostname,
        "itemsScanned": len(items),
        "statusCounts": dict(sorted(status_counts.items())),
        "discoveryErrors": discovery_errors,
        "definitionClasses": dict(class_counts.most_common()),
        "definitionModes": dict(mode_counts.most_common()),
        "familySignals": dict(family_counts.most_common()),
        "redaction": {
            "jobNames": "omitted",
            "optionValues": "omitted",
            "credentials": "omitted",
            "jobXml": "omitted",
        },
    }
    print(json.dumps(report, indent=2, ensure_ascii=False))
    return 0 if not discovery_errors and not any(
        status_counts[key] for key in ("config-read-error", "config-parse-error")
    ) else 2


if __name__ == "__main__":
    raise SystemExit(main())
