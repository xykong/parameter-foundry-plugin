#!/usr/bin/env python3
"""Run Jenkins Plugin Compatibility Tester in Docker against the support matrix."""

from __future__ import annotations

import argparse
import json
import re
import shutil
import subprocess
import tempfile
from datetime import datetime
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
MAVEN_IMAGE = "maven:3.9.11-eclipse-temurin-21"
PCT_REPOSITORY = "https://github.com/jenkinsci/plugin-compat-tester.git"
PCT_REF = "8a4e8125284289f5af87a91c717ada7f022b2daf"
SUPPORTED_CORES = ("2.528.3", "2.568.2")
PLUGIN_IDS = (
    "unified-parameters",
    "unified-parameters-migration-extended-choice",
    "unified-parameters-migration-active-choices",
    "unified-parameters-migration-list-git-branches",
    "unified-parameters-migration-additional-sources",
)
TEST_SUMMARY = re.compile(
    r"Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)"
)


def run_command(
    args: list[str],
    *,
    cwd: Path = ROOT,
    check: bool = True,
    log_file: Path | None = None,
) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(
        args,
        cwd=cwd,
        check=False,
        capture_output=True,
        text=True,
    )
    if log_file is not None:
        log_file.parent.mkdir(parents=True, exist_ok=True)
        log_file.write_text(result.stdout + result.stderr)
    if check and result.returncode != 0:
        tail = (result.stdout + result.stderr).splitlines()[-40:]
        raise RuntimeError(
            f"command failed with exit status {result.returncode}: "
            f"{' '.join(args)}\n" + "\n".join(tail)
        )
    return result


def docker_maven(
    mounts: list[tuple[Path, str, str]],
    workdir: str,
    command: list[str],
    log_file: Path,
) -> subprocess.CompletedProcess[str]:
    args = ["docker", "run", "--rm"]
    for host_path, container_path, mode in mounts:
        args.extend(["--volume", f"{host_path}:{container_path}:{mode}"])
    args.extend(["--workdir", workdir, MAVEN_IMAGE])
    args.extend(command)
    return run_command(args, log_file=log_file)


def git_commit() -> str:
    return run_command(["git", "rev-parse", "HEAD"]).stdout.strip()


def ensure_clean_checkout() -> None:
    status = run_command(["git", "status", "--porcelain"]).stdout.strip()
    if status:
        raise RuntimeError("PCT requires a clean repository checkout.")


def clone_repository(destination: Path) -> None:
    run_command(["git", "clone", "--local", "--no-hardlinks", str(ROOT), str(destination)])


def clone_pct(destination: Path) -> None:
    run_command(["git", "clone", "--filter=blob:none", PCT_REPOSITORY, str(destination)])
    run_command(["git", "fetch", "--depth", "1", "origin", PCT_REF], cwd=destination)
    run_command(["git", "checkout", "--detach", PCT_REF], cwd=destination)


def build_pct(
    pct_source: Path,
    maven_cache: Path,
    core: str,
    log_file: Path,
) -> None:
    docker_maven(
        [
            (pct_source, "/work/pct", "rw"),
            (maven_cache, "/root/.m2", "rw"),
        ],
        "/work/pct",
        [
            "mvn",
            "-B",
            "-ntp",
            "clean",
            "package",
            "-DskipTests",
            f"-Djenkins.version={core}",
        ],
        log_file,
    )


def install_local_reactor(
    repository: Path,
    maven_cache: Path,
    core: str,
    log_file: Path,
) -> None:
    compiler_properties = (
        ["-Dmaven.compiler.release=21", "-Dmaven.compiler.testRelease=21"]
        if core == "2.568.2"
        else []
    )
    docker_maven(
        [
            (repository, "/work/repo", "rw"),
            (maven_cache, "/root/.m2", "rw"),
        ],
        "/work/repo",
        [
            "mvn",
            "-B",
            "-ntp",
            "install",
            "-DskipTests",
            "-Dspotbugs.skip=true",
            "-Dspotless.check.skip=true",
            f"-Djenkins.version={core}",
            *compiler_properties,
        ],
        log_file,
    )


def run_pct(
    pct_source: Path,
    repository: Path,
    maven_cache: Path,
    workdir: Path,
    core: str,
    log_file: Path,
) -> subprocess.CompletedProcess[str]:
    workdir.mkdir(parents=True, exist_ok=True)
    return docker_maven(
        [
            (pct_source, "/work/pct", "rw"),
            (repository, "/work/repo", "rw"),
            (maven_cache, "/root/.m2", "rw"),
            (workdir, "/work/pct-work", "rw"),
        ],
        "/work/pct",
        [
            "java",
            "-jar",
            "/work/pct/target/plugins-compat-tester-cli.jar",
            "test-plugins",
            "--war",
            "/work/pct/target/megawar.war",
            "--local-checkout-dir",
            "/work/repo",
            "--include-plugins",
            ",".join(PLUGIN_IDS),
            "--working-dir",
            "/work/pct-work",
            "--fail-fast=false",
        ],
        log_file,
    )


def summarize_logs(workdir: Path) -> dict[str, dict[str, Any]]:
    summary: dict[str, dict[str, Any]] = {}
    for log_file in sorted(workdir.glob("logs/*/*.log")):
        plugin = log_file.parent.name
        text = log_file.read_text(errors="replace")
        matches = TEST_SUMMARY.findall(text)
        if not matches:
            summary[plugin] = {"buildSuccess": "BUILD SUCCESS" in text}
            continue
        tests, failures, errors, skipped = matches[-1]
        summary[plugin] = {
            "tests": int(tests),
            "failures": int(failures),
            "errors": int(errors),
            "skipped": int(skipped),
            "buildSuccess": "BUILD SUCCESS" in text,
        }
    return summary


def write_report(path: Path, report: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--report",
        type=Path,
        default=ROOT
        / f"docs/testing/results/pct-{datetime.now().strftime('%Y-%m-%d')}.json",
    )
    args = parser.parse_args()

    ensure_clean_checkout()
    report: dict[str, Any] = {
        "generatedAt": datetime.now().astimezone().isoformat(),
        "targetCommit": git_commit(),
        "pctRepository": PCT_REPOSITORY,
        "pctCommit": PCT_REF,
        "mavenImage": MAVEN_IMAGE,
        "scope": "local pre-release PCT in Docker",
        "versions": [],
    }

    with tempfile.TemporaryDirectory(prefix="unified-parameters-pct-") as temporary:
        temporary_path = Path(temporary)
        pct_source = temporary_path / "pct"
        repository = temporary_path / "repository"
        maven_cache = Path.home() / ".m2"
        clone_pct(pct_source)
        clone_repository(repository)

        for core in SUPPORTED_CORES:
            version: dict[str, Any] = {
                "jenkinsCore": core,
                "result": "failed",
            }
            version_dir = temporary_path / core
            version_dir.mkdir()
            try:
                build_pct(
                    pct_source,
                    maven_cache,
                    core,
                    version_dir / "pct-build.log",
                )
                install_local_reactor(
                    repository,
                    maven_cache,
                    core,
                    version_dir / "reactor-install.log",
                )
                pct_result = run_pct(
                    pct_source,
                    repository,
                    maven_cache,
                    version_dir / "pct-work",
                    core,
                    version_dir / "pct-run.log",
                )
                version["exitStatus"] = pct_result.returncode
                version["plugins"] = summarize_logs(version_dir / "pct-work")
                version["result"] = (
                    "passed"
                    if pct_result.returncode == 0
                    and len(version["plugins"]) == len(PLUGIN_IDS)
                    and all(
                        item.get("buildSuccess")
                        and item.get("failures", 0) == 0
                        and item.get("errors", 0) == 0
                        for item in version["plugins"].values()
                    )
                    else "failed"
                )
            except Exception as error:
                version["errorType"] = type(error).__name__
                version["error"] = str(error)
            report["versions"].append(version)
            write_report(args.report, report)

    report["result"] = (
        "passed"
        if len(report["versions"]) == len(SUPPORTED_CORES)
        and all(version["result"] == "passed" for version in report["versions"])
        else "failed"
    )
    write_report(args.report, report)
    print(json.dumps(report, indent=2, ensure_ascii=False))
    return 0 if report["result"] == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())
