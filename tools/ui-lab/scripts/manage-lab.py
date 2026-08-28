#!/usr/bin/env -S uv run --script

from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import subprocess
import time
import urllib.error
import urllib.request
import zipfile
from pathlib import Path
from typing import Any

LAB_DIR = Path(__file__).resolve().parents[1]
CACHE_DIR = LAB_DIR / ".cache"
PLUGIN_CACHE_DIR = CACHE_DIR / "plugins"
HOME_CACHE_DIR = CACHE_DIR / "home"
STATE_DIR = CACHE_DIR / "state"
PROFILES_DIR = LAB_DIR / "profiles"
INIT_DIR = LAB_DIR / "common" / "init.groovy.d"
JENKINS_IMAGE = "jenkins/jenkins:2.568.2-lts-jdk21"
JENKINS_CORE = "2.568.2"
CONTAINER_NAME = "jenkins-parameter-plugin-ui-lab"
BASE_URL = "http://127.0.0.1:18080"


def run(
    args: list[str],
    *,
    check: bool = True,
    capture_output: bool = False,
) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        args,
        check=check,
        capture_output=capture_output,
        text=True,
    )


def profile_names() -> list[str]:
    return sorted(
        path.name
        for path in PROFILES_DIR.iterdir()
        if path.is_dir() and (path / "plugins.txt").is_file()
    )


def require_profile(profile: str) -> Path:
    path = PROFILES_DIR / profile
    if profile not in profile_names():
        raise RuntimeError(
            f"未知 profile: {profile}；可选值: {', '.join(profile_names())}"
        )
    return path


def digest_file(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def safe_remove_tree(path: Path, parent: Path) -> None:
    resolved = path.resolve()
    parent_resolved = parent.resolve()
    if resolved == parent_resolved or parent_resolved not in resolved.parents:
        raise RuntimeError(f"拒绝删除非缓存目录: {resolved}")
    if path.exists():
        shutil.rmtree(path)


def container_exists() -> bool:
    result = run(
        ["docker", "container", "inspect", CONTAINER_NAME],
        check=False,
        capture_output=True,
    )
    return result.returncode == 0


def stop() -> None:
    if container_exists():
        run(["docker", "container", "rm", "--force", CONTAINER_NAME])


def read_manifest_version(path: Path) -> str:
    with zipfile.ZipFile(path) as archive:
        manifest = archive.read("META-INF/MANIFEST.MF").decode(errors="replace")
    current_key = ""
    values: dict[str, str] = {}
    for line in manifest.replace("\r\n", "\n").splitlines():
        if line.startswith(" ") and current_key:
            values[current_key] += line[1:]
            continue
        if ": " not in line:
            continue
        current_key, value = line.split(": ", 1)
        values[current_key] = value
    return values.get("Plugin-Version", "")


def plugin_inventory(profile: str) -> list[dict[str, str]]:
    plugin_dir = PLUGIN_CACHE_DIR / profile
    inventory = []
    for plugin in sorted(plugin_dir.glob("*.jpi")):
        inventory.append(
            {
                "name": plugin.stem,
                "version": read_manifest_version(plugin),
                "sha256": digest_file(plugin),
            }
        )
    return inventory


def prepare_profile(profile: str) -> None:
    profile_dir = require_profile(profile)
    plugins_file = profile_dir / "plugins.txt"
    plugin_dir = PLUGIN_CACHE_DIR / profile
    state_file = STATE_DIR / f"{profile}.json"
    desired = {
        "jenkinsImage": JENKINS_IMAGE,
        "pluginsSha256": digest_file(plugins_file),
    }

    if state_file.is_file() and plugin_dir.is_dir():
        current = json.loads(state_file.read_text())
        metadata = json.loads((profile_dir / "profile.json").read_text())
        plugin_name = metadata.get("plugin")
        target = plugin_dir / f"{plugin_name}.jpi" if plugin_name else None
        if current == desired and (target is None or target.is_file()):
            print(f"PREPARED|{profile}|cached")
            return

    safe_remove_tree(plugin_dir, PLUGIN_CACHE_DIR)
    plugin_dir.mkdir(parents=True, exist_ok=True)
    STATE_DIR.mkdir(parents=True, exist_ok=True)

    run(
        [
            "docker",
            "run",
            "--rm",
            "--volume",
            f"{plugins_file}:/plugins.txt:ro",
            "--volume",
            f"{plugin_dir}:/plugins",
            JENKINS_IMAGE,
            "jenkins-plugin-cli",
            "--plugin-file",
            "/plugins.txt",
            "--plugin-download-directory",
            "/plugins",
            "--latest=true",
        ]
    )

    metadata = json.loads((profile_dir / "profile.json").read_text())
    plugin_name = metadata.get("plugin")
    if plugin_name:
        target = plugin_dir / f"{plugin_name}.jpi"
        if not target.is_file():
            raise RuntimeError(f"{profile} 缺少目标插件文件: {target.name}")
        actual_version = read_manifest_version(target)
        if actual_version != metadata["version"]:
            raise RuntimeError(
                f"{profile} 目标版本不一致: {actual_version} != {metadata['version']}"
            )

    state_file.write_text(json.dumps(desired, indent=2) + "\n")
    print(f"PREPARED|{profile}|plugins={len(plugin_inventory(profile))}")


def prepare(profiles: list[str]) -> None:
    for profile in profiles:
        prepare_profile(profile)


def wait_ready(profile: str, home_dir: Path, timeout_seconds: int = 180) -> None:
    deadline = time.monotonic() + timeout_seconds
    last_error = ""
    while time.monotonic() < deadline:
        ready_file = home_dir / "ui-lab-ready"
        try:
            with urllib.request.urlopen(
                BASE_URL + "/job/ui-lab-freestyle/api/json", timeout=3
            ) as response:
                if response.status == 200 and ready_file.is_file():
                    actual = ready_file.read_text().strip()
                    if actual != profile:
                        raise RuntimeError(
                            f"ready profile 不一致: {actual} != {profile}"
                        )
                    print(f"READY|{profile}|{BASE_URL}")
                    return
        except (urllib.error.URLError, TimeoutError, OSError) as exc:
            last_error = type(exc).__name__
        time.sleep(2)

    logs = run(
        ["docker", "logs", "--tail", "240", CONTAINER_NAME],
        check=False,
        capture_output=True,
    )
    raise RuntimeError(
        f"{profile} Jenkins 启动超时，最后错误={last_error}\n"
        f"{logs.stdout}\n{logs.stderr}"
    )


def start(profile: str) -> None:
    profile_dir = require_profile(profile)
    prepare_profile(profile)
    stop()

    home_dir = HOME_CACHE_DIR / profile
    safe_remove_tree(home_dir, HOME_CACHE_DIR)
    home_dir.mkdir(parents=True, exist_ok=True)
    os.chmod(home_dir, 0o777)

    plugin_dir = PLUGIN_CACHE_DIR / profile
    run(
        [
            "docker",
            "run",
            "--detach",
            "--name",
            CONTAINER_NAME,
            "--publish",
            "127.0.0.1:18080:8080",
            "--env",
            f"LAB_PROFILE={profile}",
            "--env",
            "JAVA_OPTS=-Djenkins.install.runSetupWizard=false",
            "--volume",
            f"{home_dir}:/var/jenkins_home",
            "--volume",
            f"{plugin_dir}:/usr/share/jenkins/ref/plugins:ro",
            "--volume",
            f"{INIT_DIR}:/var/jenkins_home/init.groovy.d:ro",
            "--volume",
            f"{profile_dir}:/opt/parameter-lab/profile:ro",
            JENKINS_IMAGE,
        ],
        capture_output=True,
    )
    wait_ready(profile, home_dir)


def status() -> dict[str, Any]:
    if not container_exists():
        return {"running": False}
    inspection = run(
        [
            "docker",
            "container",
            "inspect",
            CONTAINER_NAME,
            "--format",
            "{{json .State}}",
        ],
        capture_output=True,
    )
    return {"running": True, "state": json.loads(inspection.stdout)}


def clean() -> None:
    stop()
    safe_remove_tree(HOME_CACHE_DIR, CACHE_DIR)
    print("CLEANED|runtime-home")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="管理隔离 Jenkins 参数插件 UI Lab。")
    subparsers = parser.add_subparsers(dest="command", required=True)

    prepare_parser = subparsers.add_parser("prepare")
    prepare_parser.add_argument(
        "--profile",
        action="append",
        choices=profile_names(),
        dest="profiles",
    )

    start_parser = subparsers.add_parser("start")
    start_parser.add_argument("profile", choices=profile_names())

    subparsers.add_parser("stop")
    subparsers.add_parser("status")
    subparsers.add_parser("clean")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if args.command == "prepare":
        prepare(args.profiles or profile_names())
    elif args.command == "start":
        start(args.profile)
    elif args.command == "stop":
        stop()
    elif args.command == "status":
        print(json.dumps(status(), indent=2))
    elif args.command == "clean":
        clean()


if __name__ == "__main__":
    main()
