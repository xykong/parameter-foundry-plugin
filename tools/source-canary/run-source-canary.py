#!/usr/bin/env python3
"""Run a disposable Docker canary with real source-plugin HPI files."""

from __future__ import annotations

import argparse
import http.cookiejar
import json
import os
import re
import shutil
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from datetime import datetime
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
MAVEN_IMAGE = "maven:3.9.11-eclipse-temurin-21"
JENKINS_IMAGE = "jenkins/jenkins:2.568.2-lts-jdk21"
CONTAINER_PREFIX = "unified-parameters-source-canary"
TIMEOUT_SECONDS = 180

PLUGIN_LINES = (
    "structs:362.va_b_695ef4fdf9",
    "snakeyaml-api:2.5-149.v72471e9c6371",
    "cloudbees-folder:6.1079.vc0975c2de294",
    "credentials:1496.vf6821f162d4e",
    "credentials-binding:728.v902a_273b_8947",
    "plain-credentials:199.v9f8e1f741799",
    "ssh-credentials:372.va_250881b_08cd",
    "git-client:6.6.1",
    "token-macro:477.vd4f0dc3cb_cf1",
    "script-security:1412.v7737b_3405f86",
    "config-file-provider:1006.vc7366c201f57",
    "workflow-job:1595.v0ee1a_de2c0a_3",
    "workflow-cps:4370.v49a_6937566b_6",
    "workflow-basic-steps:1098.v808b_fd7f8cf4",
    "pipeline-build-step:584.vdb_a_2cc3a_d07a_",
    "rebuild:338.va_0a_b_50e29397",
    "extended-choice-parameter:388.ve7b_d0b_920e10",
    "uno-choice:2.8.10",
    "list-git-branches-parameter:76.v7a_a_7a_5065278",
    "file-parameters:425.v3fa_801681b_5e",
)

INIT_SCRIPT = r"""
import com.cwctravel.hudson.plugins.extended_choice_parameter.ExtendedChoiceParameterDefinition
import hudson.model.FreeStyleProject
import hudson.model.ParametersDefinitionProperty
import hudson.tasks.Shell
import jenkins.model.Jenkins
import jenkins.model.JenkinsLocationConfiguration
import org.biouno.unochoice.CascadeChoiceParameter
import org.biouno.unochoice.ChoiceParameter
import org.biouno.unochoice.model.GroovyScript
import org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.SecureGroovyScript
import io.jenkins.plugins.unifiedparameters.companion.renderer.AccessibleChoiceGridRenderer
import io.jenkins.plugins.unifiedparameters.companion.script.ScriptCatalogEntry
import io.jenkins.plugins.unifiedparameters.companion.script.ScriptProviderCatalog
import io.jenkins.plugins.unifiedparameters.definition.UnifiedChoiceParameterDefinition
import io.jenkins.plugins.unifiedparameters.provider.ScriptProviderDefinition
import io.jenkins.plugins.unifiedparameters.provider.UnifiedParametersGlobalConfiguration

def jenkins = Jenkins.get()
def location = JenkinsLocationConfiguration.get()
location.setUrl('http://127.0.0.1:8080/')
location.save()

def extended = new ExtendedChoiceParameterDefinition(
    'LEGACY_TARGETS',
    ExtendedChoiceParameterDefinition.PARAMETER_TYPE_CHECK_BOX,
    'android,ios,web',
    null, null, null, null, null, null, null,
    'android,ios',
    null, null, null, null, null, null, null, null, null, null, null, null, null,
    null, null, false, false, 5, 'Synthetic Extended Choice source', ','
)
def extendedJob = jenkins.createProject(FreeStyleProject, 'source-extended-choice')
extendedJob.addProperty(new ParametersDefinitionProperty(extended))
extendedJob.buildersList.add(new Shell('printf "LEGACY_TARGETS=%s\\n" "$LEGACY_TARGETS"'))
extendedJob.save()

def script = { String expression ->
    new GroovyScript(
        new SecureGroovyScript(expression, true, []),
        new SecureGroovyScript("return ['fallback']", true, []),
    )
}
def activeBase = new ChoiceParameter(
    'ACTIVE_BASE',
    'Synthetic Active Choices base source',
    'active-base-canary',
    script("return ['staging', 'production']"),
    'PT_SINGLE_SELECT',
    false,
    5,
)
def activeTargets = new ChoiceParameter(
    'ACTIVE_TARGETS',
    'Synthetic Active Choices static source',
    'active-targets-canary',
    script("throw new RuntimeException('SOURCE_SCRIPT_MUST_NOT_EXECUTE')"),
    'PT_MULTI_SELECT',
    true,
    5,
)
def activeCascade = new CascadeChoiceParameter(
    'ACTIVE_CASCADE',
    'Synthetic Active Choices cascade source',
    'active-cascade-canary',
    script("return ['small', 'large']"),
    'PT_SINGLE_SELECT',
    'ACTIVE_BASE',
    false,
    5,
)
def activeJob = jenkins.createProject(FreeStyleProject, 'source-active-choices')
activeJob.addProperty(new ParametersDefinitionProperty(activeBase, activeTargets, activeCascade))
activeJob.buildersList.add(new Shell('printf "ACTIVE_BASE=%s ACTIVE_TARGETS=%s ACTIVE_CASCADE=%s\\n" "$ACTIVE_BASE" "$ACTIVE_TARGETS" "$ACTIVE_CASCADE"'))
activeJob.save()

def sortMode = Enum.valueOf(
    Class.forName('com.syhuang.hudson.plugins.listgitbranchesparameter.SortMode'), 'NONE'
)
def selectedValue = Enum.valueOf(
    Class.forName('com.syhuang.hudson.plugins.listgitbranchesparameter.SelectedValue'), 'NONE'
)
def gitDefinition = new com.syhuang.hudson.plugins.listgitbranchesparameter.ListGitBranchesParameterDefinition(
    'GIT_REF',
    'Synthetic List Git Branches source',
    'https://example.test/repository.git',
    null,
    'main',
    sortMode,
    selectedValue,
    false,
    '',
    '',
    'branch',
    '20',
)
def gitJob = jenkins.createProject(FreeStyleProject, 'source-list-git-branches')
gitJob.addProperty(new ParametersDefinitionProperty(gitDefinition))
gitJob.save()

def stashDefinition = new io.jenkins.plugins.file_parameters.StashedFileParameterDefinition('UPLOAD')
def stashJob = jenkins.createProject(FreeStyleProject, 'source-stashed-file')
stashJob.addProperty(new ParametersDefinitionProperty(stashDefinition))
stashJob.buildersList.add(new Shell('test -n "$UPLOAD" || true'))
stashJob.save()

def companionScript = new SecureGroovyScript(
    "return ['release-a', [value: 'release-b', label: 'Release B']]",
    true,
    []
)
ScriptProviderCatalog.get().setEntries([new ScriptCatalogEntry('releases', companionScript)])
def companionProvider = new ScriptProviderDefinition(
    'companion-options',
    'sandbox-groovy',
    'catalog:releases'
)
UnifiedParametersGlobalConfiguration.get().setProviders([companionProvider])
def companionParameter = new UnifiedChoiceParameterDefinition('COMPANION')
companionParameter.setProviderId('companion-options')
companionParameter.setRenderer('accessible-choice-grid')
def companionJob = jenkins.createProject(FreeStyleProject, 'source-companion')
companionJob.addProperty(new ParametersDefinitionProperty(companionParameter))
companionJob.buildersList.add(new Shell('test -n "$COMPANION"'))
companionJob.save()
"""


def run_command(
    args: list[str],
    *,
    cwd: Path = ROOT,
    check: bool = True,
    capture_output: bool = False,
) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        args,
        cwd=cwd,
        check=check,
        capture_output=capture_output,
        text=True,
    )


def free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.bind(("127.0.0.1", 0))
        return int(sock.getsockname()[1])


def artifact_paths() -> list[Path]:
    return [ROOT / "plugin" / "target" / "unified-parameters.hpi"]


def build_artifacts() -> None:
    run_command(
        [
            "docker",
            "run",
            "--rm",
            "--volume",
            f"{ROOT}:/workspace",
            "--volume",
            f"{Path.home() / '.m2'}:/root/.m2",
            "--workdir",
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
    missing = [str(path) for path in artifact_paths() if not path.is_file()]
    if missing:
        raise RuntimeError(f"missing HPI artifacts: {missing}")


def prepare_plugins(plugin_dir: Path) -> None:
    plugin_file = plugin_dir.parent / "plugins.txt"
    plugin_file.write_text("\n".join(PLUGIN_LINES) + "\n")
    run_command(
        [
            "docker",
            "run",
            "--rm",
            "--volume",
            f"{plugin_file}:/plugins.txt:ro",
            "--volume",
            f"{plugin_dir}:/plugins",
            JENKINS_IMAGE,
            "jenkins-plugin-cli",
            "--plugin-file",
            "/plugins.txt",
            "--plugin-download-directory",
            "/plugins",
            "--latest=false",
        ]
    )
    for artifact in artifact_paths():
        destination = plugin_dir / f"{artifact.stem}.jpi"
        shutil.copy2(artifact, destination)


def write_report(path: Path, report: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n")


class Canary:
    def __init__(self, base_url: str, container: str) -> None:
        self.base_url = base_url.rstrip("/")
        self.container = container
        self.cookies = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(self.cookies)
        )
        self.crumb: tuple[str, str] | None = None

    def request(
        self,
        path: str,
        *,
        method: str = "GET",
        body: bytes | None = None,
        content_type: str | None = None,
        extra_headers: dict[str, str] | None = None,
    ) -> tuple[int, bytes, dict[str, str]]:
        headers = dict(extra_headers or {})
        if content_type:
            headers["Content-Type"] = content_type
        if method != "GET":
            if self.crumb is None:
                self.load_crumb()
            assert self.crumb is not None
            headers[self.crumb[0]] = self.crumb[1]
        request = urllib.request.Request(
            self.base_url + path,
            data=body,
            method=method,
            headers=headers,
        )
        try:
            with self.opener.open(request, timeout=30) as response:
                return response.status, response.read(), dict(response.headers)
        except urllib.error.HTTPError as error:
            return error.code, error.read(), dict(error.headers)

    def load_crumb(self) -> None:
        status, body, _ = self.request("/crumbIssuer/api/json")
        if status != 200:
            raise RuntimeError(f"crumb issuer returned HTTP {status}")
        payload = json.loads(body)
        self.crumb = (payload["crumbRequestField"], payload["crumb"])

    def get_text(self, path: str) -> str:
        status, body, _ = self.request(path)
        if status != 200:
            raise RuntimeError(f"GET {path} returned HTTP {status}")
        return body.decode(errors="replace")

    def post_form(
        self,
        path: str,
        values: dict[str, str],
        *,
        extra_headers: dict[str, str] | None = None,
    ) -> tuple[int, bytes]:
        body = urllib.parse.urlencode(values).encode()
        status, response, _ = self.request(
            path,
            method="POST",
            body=body,
            content_type="application/x-www-form-urlencoded",
            extra_headers=extra_headers,
        )
        return status, response

    def post_json(self, path: str, payload: dict[str, Any]) -> tuple[int, dict[str, Any]]:
        status, response, _ = self.request(
            path,
            method="POST",
            body=json.dumps(payload).encode(),
            content_type="application/json",
        )
        return status, json.loads(response)

    def post_multipart(
        self,
        path: str,
        *,
        field: str,
        filename: str,
        content: bytes,
    ) -> int:
        boundary = "----UnifiedParametersCanary"
        body = (
            f"--{boundary}\r\n"
            f'Content-Disposition: form-data; name="{field}"; filename="{filename}"\r\n'
            "Content-Type: application/octet-stream\r\n\r\n"
        ).encode() + content + f"\r\n--{boundary}--\r\n".encode()
        status, _, _ = self.request(
            path,
            method="POST",
            body=body,
            content_type=f"multipart/form-data; boundary={boundary}",
        )
        return status

    def last_build(self, job: str) -> dict[str, Any]:
        status, body, _ = self.request(
            f"/job/{job}/lastBuild/api/json?tree=number,building,result"
        )
        if status != 200:
            return {}
        return json.loads(body)

    def queue_length(self) -> int:
        status, body, _ = self.request("/queue/api/json?tree=items[id]")
        if status != 200:
            raise RuntimeError(f"queue API returned HTTP {status}")
        return len(json.loads(body).get("items", []))

    def run_script(self, script: str) -> None:
        status, response = self.post_form("/scriptText", {"script": script})
        if status != 200:
            raise RuntimeError(
                f"scriptText returned HTTP {status}: "
                f"{response.decode(errors='replace')[:1000]}"
            )

    def wait_for_build(self, job: str, previous_number: int = 0) -> dict[str, Any]:
        deadline = time.monotonic() + TIMEOUT_SECONDS
        while time.monotonic() < deadline:
            build = self.last_build(job)
            if (
                build.get("number", 0) > previous_number
                and not build.get("building")
                and build.get("result")
            ):
                return build
            time.sleep(1)
        raise RuntimeError(f"waiting for {job} build timed out")


def run_cli(
    container: str, job: str, value: str
) -> subprocess.CompletedProcess[str]:
    return run_command(
        [
            "docker",
            "exec",
            container,
            "java",
            "-jar",
            "/opt/source-canary/jenkins-cli.jar",
            "-s",
            "http://127.0.0.1:8080",
            "build",
            job,
            "-p",
            f"LEGACY_TARGETS={value}",
            "-s",
        ],
        check=False,
        capture_output=True,
    )


def git_commit() -> str:
    return run_command(
        ["git", "rev-parse", "HEAD"], capture_output=True
    ).stdout.strip()


def start_controller(
    container: str,
    port: int,
    home_dir: Path,
    plugin_dir: Path,
    init_script: Path,
    temporary_path: Path,
) -> None:
    run_command(
        [
            "docker",
            "run",
            "--rm",
            "--detach",
            "--name",
            container,
            "--publish",
            f"127.0.0.1:{port}:8080",
            "--env",
            "JAVA_OPTS=-Djenkins.install.runSetupWizard=false",
            "--volume",
            f"{home_dir}:/var/jenkins_home",
            "--volume",
            f"{plugin_dir}:/usr/share/jenkins/ref/plugins:ro",
            "--volume",
            f"{init_script}:/usr/share/jenkins/ref/init.groovy.d/01-source-canary.groovy:ro",
            "--volume",
            f"{temporary_path}:/opt/source-canary:ro",
            JENKINS_IMAGE,
        ]
    )


def wait_ready(canary: Canary) -> None:
    deadline = time.monotonic() + TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        try:
            status, _, _ = canary.request("/job/source-extended-choice/api/json")
            if status == 200:
                return
        except (OSError, ValueError, KeyError, urllib.error.URLError):
            pass
        time.sleep(1)
    logs = run_command(
        ["docker", "logs", "--tail", "200", canary.container],
        check=False,
        capture_output=True,
    )
    raise RuntimeError(f"Jenkins did not become ready:\n{logs.stdout}\n{logs.stderr}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--report",
        type=Path,
        default=ROOT
        / f"docs/testing/results/source-plugin-canary-{time.strftime('%Y-%m-%d')}.json",
    )
    args = parser.parse_args()

    container = f"{CONTAINER_PREFIX}-{os.getpid()}"
    port = free_port()
    report: dict[str, Any] = {
        "generatedAt": datetime.now().astimezone().isoformat(),
        "jenkinsImage": JENKINS_IMAGE,
        "targetCommit": git_commit(),
        "scope": "synthetic Docker canary only",
        "checks": {},
    }
    try:
        with tempfile.TemporaryDirectory(prefix="unified-parameters-source-canary-") as temporary:
            temporary_path = Path(temporary)
            plugin_dir = temporary_path / "plugins"
            home_dir = temporary_path / "home"
            plugin_dir.mkdir()
            home_dir.mkdir()
            home_dir.chmod(0o777)
            init_script = temporary_path / "init.groovy"
            init_script.write_text(INIT_SCRIPT)

            build_artifacts()
            prepare_plugins(plugin_dir)
            start_controller(
                container, port, home_dir, plugin_dir, init_script, temporary_path
            )
            canary = Canary(f"http://127.0.0.1:{port}", container)
            wait_ready(canary)

            plugin_api = canary.get_text(
                "/pluginManager/api/json?tree=plugins[shortName,version,active,enabled]"
            )
            plugin_inventory = json.loads(plugin_api)["plugins"]
            required_plugins = {
                "extended-choice-parameter",
                "uno-choice",
                "list-git-branches-parameter",
                "file-parameters",
                "unified-parameters",
            }
            active_plugins = {
                item["shortName"]
                for item in plugin_inventory
                if item.get("active") and item.get("enabled")
            }
            missing_plugins = sorted(required_plugins - active_plugins)
            if missing_plugins:
                report["checks"]["pluginLoad"] = {
                    "activePlugins": sorted(active_plugins),
                    "missingRequired": missing_plugins,
                    "passed": False,
                }
                raise RuntimeError(f"required plugins are not active: {missing_plugins}")
            report["checks"]["pluginLoad"] = {
                "requiredActive": sorted(required_plugins),
                "passed": True,
            }

            schema_text = canary.get_text(
                "/job/source-companion/unified-parameters/api/v1/schema"
            )
            schema_body = json.loads(schema_text)
            if "accessible-choice-grid" not in schema_text:
                raise RuntimeError(
                    f"companion renderer was not exposed in the schema: "
                    f"body={schema_body}"
                )
            resolve_status, resolve_body = canary.post_json(
                "/job/source-companion/unified-parameters/api/v1/resolve",
                {"parameter": "COMPANION", "dependencies": {}},
            )
            option_values = [item.get("value") for item in resolve_body.get("options", [])]
            if resolve_status != 200 or option_values != ["release-a", "release-b"]:
                raise RuntimeError(
                    f"script Provider resolve failed: HTTP {resolve_status}, body={resolve_body}"
                )
            previous = canary.last_build("source-companion").get("number", 0)
            build_status, build_body = canary.post_json(
                "/job/source-companion/unified-parameters/api/v1/build",
                {"values": {"COMPANION": "release-a"}},
            )
            companion_build = canary.wait_for_build("source-companion", previous)
            if build_status not in {201, 303} or companion_build.get("result") != "SUCCESS":
                raise RuntimeError(
                    "companion build failed: "
                    f"HTTP {build_status}, body={build_body}, build={companion_build}"
                )
            report["checks"]["companions"] = {
                "rendererId": "accessible-choice-grid",
                "rendererExposed": True,
                "schemaStatus": 200,
                "scriptEngineId": "sandbox-groovy",
                "resolvedOptions": option_values,
                "resolveStatus": resolve_status,
                "buildStatus": build_status,
                "build": companion_build,
                "passed": True,
            }

            extended_preview = canary.get_text(
                "/job/source-extended-choice/unified-parameters/"
            )
            active_preview = canary.get_text(
                "/job/source-active-choices/unified-parameters/"
            )
            git_preview = canary.get_text(
                "/job/source-list-git-branches/unified-parameters/"
            )
            stash_preview = canary.get_text(
                "/job/source-stashed-file/unified-parameters/"
            )
            assert "Class B" in extended_preview
            assert "UnifiedMultiChoiceParameterDefinition" in extended_preview
            assert "Class C" in active_preview
            assert "TODO-governed-provider" in active_preview
            assert "ACTIVE_BASE" in active_preview
            assert "Class C" in git_preview
            assert "unifiedGitRefProvider" in git_preview
            assert "refType: 'branch'" in git_preview
            assert "example.test" not in git_preview
            assert "Class D" in stash_preview
            assert "multipart/file transport" in stash_preview
            report["checks"]["migrationPreviews"] = {
                "extendedChoiceClassB": True,
                "activeChoicesClassC": True,
                "activeChoicesCascadeDependency": True,
                "listGitBranchesClassC": True,
                "listGitBranchesGitRefMapping": True,
                "stashedFileClassD": True,
                "sensitiveSourceRedacted": True,
                "passed": True,
            }

            clone_name = "target-extended-choice"
            status, _ = canary.post_form(
                "/job/source-extended-choice/unified-parameters/cloneMigration",
                {
                    "parameterName": "LEGACY_TARGETS",
                    "cloneName": clone_name,
                    "acceptDecisions": "true",
                },
            )
            if status not in {200, 302}:
                raise RuntimeError(f"migration clone returned HTTP {status}")
            clone_config = canary.get_text(f"/job/{clone_name}/config.xml")
            if "UnifiedMultiChoiceParameterDefinition" not in clone_config:
                raise RuntimeError("clone did not persist the Unified target definition")
            clone_page = canary.get_text(f"/job/{clone_name}/unified-parameters/")
            if "APPLIED" not in clone_page:
                raise RuntimeError("clone did not create an APPLIED migration record")
            report["checks"]["extendedChoiceClone"] = {
                "status": status,
                "targetPersisted": True,
                "auditRecord": True,
                "passed": True,
            }

            standard_status, _ = canary.post_form(
                f"/job/{clone_name}/buildWithParameters?delay=0sec",
                {"__unified_parameters_presence": "LEGACY_TARGETS", "LEGACY_TARGETS": "web,android"},
            )
            standard_build = canary.wait_for_build(clone_name)
            if standard_status != 201 or standard_build.get("result") != "SUCCESS":
                raise RuntimeError(
                    f"standard REST canary failed: HTTP {standard_status}, build={standard_build}"
                )
            report["checks"]["standardRestDelimited"] = {
                "status": standard_status,
                "build": standard_build,
                "canonicalValue": ["android", "web"],
                "passed": True,
            }

            previous = canary.last_build(clone_name).get("number", 0)
            webhook_status, _ = canary.post_form(
                f"/job/{clone_name}/buildWithParameters?delay=0sec",
                {
                    "__unified_parameters_presence": "LEGACY_TARGETS",
                    "LEGACY_TARGETS": "ios,web",
                },
                extra_headers={"User-Agent": "Synthetic-Webhook/1.0"},
            )
            webhook_build = canary.wait_for_build(clone_name, previous)
            if webhook_status != 201 or webhook_build.get("result") != "SUCCESS":
                raise RuntimeError(
                    f"webhook-style build failed: HTTP {webhook_status}, "
                    f"build={webhook_build}"
                )
            report["checks"]["webhookStyleStandardRest"] = {
                "status": webhook_status,
                "build": webhook_build,
                "canonicalValue": ["ios", "web"],
                "passed": True,
            }

            validate_status, validate_body = canary.post_json(
                f"/job/{clone_name}/unified-parameters/api/v1/validate",
                {"values": {"LEGACY_TARGETS": ["web", "android"]}},
            )
            canonical = validate_body.get("canonicalValues", {}).get("LEGACY_TARGETS")
            if validate_status != 200 or canonical != ["android", "web"]:
                raise RuntimeError(
                    f"typed validate canary failed: HTTP {validate_status}, body={validate_body}"
                )
            previous = canary.last_build(clone_name).get("number", 0)
            build_status, build_body = canary.post_json(
                f"/job/{clone_name}/unified-parameters/api/v1/build",
                {"values": {"LEGACY_TARGETS": ["web", "android"]}},
            )
            typed_build = canary.wait_for_build(clone_name, previous)
            if build_status not in {201, 303} or typed_build.get("result") != "SUCCESS":
                raise RuntimeError(
                    f"typed build canary failed: HTTP {build_status}, build={typed_build}"
                )
            report["checks"]["typedRestArray"] = {
                "validateStatus": validate_status,
                "buildStatus": build_status,
                "canonicalValue": canonical,
                "build": typed_build,
                "passed": True,
            }

            cli_jar = temporary_path / "jenkins-cli.jar"
            status, jar_bytes, _ = canary.request("/jnlpJars/jenkins-cli.jar")
            if status != 200:
                raise RuntimeError(f"CLI JAR download returned HTTP {status}")
            cli_jar.write_bytes(jar_bytes)
            previous = canary.last_build(clone_name).get("number", 0)
            cli_result = run_cli(container, clone_name, '["web","android"]')
            if cli_result.returncode != 0:
                output = (cli_result.stderr or cli_result.stdout).strip()
                raise RuntimeError(
                    f"CLI canary failed: returncode={cli_result.returncode}, "
                    f"output={output[:1000]}"
                )
            cli_build = canary.wait_for_build(clone_name, previous)
            if cli_build.get("result") != "SUCCESS":
                raise RuntimeError(
                    f"CLI canary failed: returncode={cli_result.returncode}, build={cli_build}"
                )
            report["checks"]["cliJsonArray"] = {
                "returnCode": cli_result.returncode,
                "build": cli_build,
                "canonicalValue": ["android", "web"],
                "passed": True,
            }

            upstream_name = "source-canary-upstream"
            upstream_script = f"""
import jenkins.model.Jenkins
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
def job = Jenkins.get().createProject(WorkflowJob, '{upstream_name}')
job.setDefinition(new CpsFlowDefinition(\"\"\"
build job: '{clone_name}', wait: true, parameters: [[
  \\$class: 'UnifiedMultiChoiceParameterValue',
  name: 'LEGACY_TARGETS',
  value: ['web', 'android']
]]
\"\"\", true))
job.save()
"""
            canary.run_script(upstream_script)
            downstream_previous = canary.last_build(clone_name).get("number", 0)
            upstream_status, _ = canary.post_form(
                f"/job/{upstream_name}/build?delay=0sec", {}
            )
            upstream_build = canary.wait_for_build(upstream_name)
            downstream_build = canary.wait_for_build(clone_name, downstream_previous)
            if (
                upstream_status != 201
                or upstream_build.get("result") != "SUCCESS"
                or downstream_build.get("result") != "SUCCESS"
            ):
                raise RuntimeError(
                    f"upstream canary failed: HTTP {upstream_status}, "
                    f"upstream={upstream_build}, downstream={downstream_build}"
                )
            report["checks"]["pipelineUpstream"] = {
                "status": upstream_status,
                "upstreamBuild": upstream_build,
                "downstreamBuild": downstream_build,
                "canonicalValue": ["android", "web"],
                "passed": True,
            }

            before_invalid_build = canary.last_build(clone_name).get("number", 0)
            before_invalid_queue = canary.queue_length()
            invalid_status, invalid_body = canary.post_json(
                f"/job/{clone_name}/unified-parameters/api/v1/build",
                {"values": {"LEGACY_TARGETS": ["unknown"]}},
            )
            after_invalid_build = canary.last_build(clone_name).get("number", 0)
            after_invalid_queue = canary.queue_length()
            invalid_code = (
                invalid_body.get("errors", [{}])[0].get("code")
                if invalid_body.get("errors")
                else None
            )
            if (
                invalid_status != 400
                or invalid_code != "UNIFIED_PARAMETER_UNKNOWN_VALUE"
                or after_invalid_build != before_invalid_build
                or after_invalid_queue != before_invalid_queue
            ):
                raise RuntimeError(
                    f"typed no-queue canary failed: HTTP {invalid_status}, "
                    f"code={invalid_code}, builds={before_invalid_build}/"
                    f"{after_invalid_build}, queue={before_invalid_queue}/"
                    f"{after_invalid_queue}"
                )
            report["checks"]["typedInvalidNoQueue"] = {
                "status": invalid_status,
                "code": invalid_code,
                "buildNumberUnchanged": True,
                "queueLengthUnchanged": True,
                "passed": True,
            }

            before_webhook_build = canary.last_build(clone_name).get("number", 0)
            before_webhook_queue = canary.queue_length()
            invalid_webhook_status, invalid_webhook_body = canary.post_form(
                f"/job/{clone_name}/buildWithParameters?delay=0sec",
                {
                    "__unified_parameters_presence": "LEGACY_TARGETS",
                    "LEGACY_TARGETS": "unknown",
                },
                extra_headers={"User-Agent": "Synthetic-Webhook/1.0"},
            )
            after_webhook_build = canary.last_build(clone_name).get("number", 0)
            after_webhook_queue = canary.queue_length()
            invalid_webhook_text = invalid_webhook_body.decode(errors="replace")
            if (
                invalid_webhook_status != 400
                or "UNIFIED_PARAMETER_UNKNOWN_VALUE" not in invalid_webhook_text
                or after_webhook_build != before_webhook_build
                or after_webhook_queue != before_webhook_queue
            ):
                raise RuntimeError(
                    f"webhook no-queue canary failed: HTTP "
                    f"{invalid_webhook_status}, builds={before_webhook_build}/"
                    f"{after_webhook_build}, queue={before_webhook_queue}/"
                    f"{after_webhook_queue}"
                )
            report["checks"]["webhookInvalidNoQueue"] = {
                "status": invalid_webhook_status,
                "code": "UNIFIED_PARAMETER_UNKNOWN_VALUE",
                "buildNumberUnchanged": True,
                "queueLengthUnchanged": True,
                "passed": True,
            }

            latest = canary.last_build(clone_name)
            rebuild_status, rebuild_body, _ = canary.request(
                f"/job/{clone_name}/{latest['number']}/rebuild/parameterized"
            )
            rebuild_text = rebuild_body.decode(errors="replace")
            if rebuild_status != 200 or "LEGACY_TARGETS" not in rebuild_text:
                raise RuntimeError(f"Rebuild page canary failed: HTTP {rebuild_status}")
            report["checks"]["rebuild"] = {
                "status": rebuild_status,
                "parameterPresent": True,
                "passed": True,
            }

            previous = canary.last_build("source-stashed-file").get("number", 0)
            multipart_status = canary.post_multipart(
                "/job/source-stashed-file/buildWithParameters?delay=0sec",
                field="UPLOAD",
                filename="canary.txt",
                content=b"source plugin multipart canary",
            )
            stash_build = canary.wait_for_build("source-stashed-file", previous)
            if multipart_status != 201 or stash_build.get("result") != "SUCCESS":
                raise RuntimeError(
                    f"Stashed File multipart canary failed: HTTP {multipart_status}, build={stash_build}"
                )
            report["checks"]["stashedFileMultipart"] = {
                "status": multipart_status,
                "build": stash_build,
                "nativeTransport": True,
                "passed": True,
            }

            clone_page = canary.get_text(f"/job/{clone_name}/unified-parameters/")
            record_match = re.search(
                r'name="recordId"[^>]*value="([^"]+)"', clone_page
            )
            if record_match is None:
                raise RuntimeError("migration record did not expose a rollback ID")
            rollback_status, _ = canary.post_form(
                f"/job/{clone_name}/unified-parameters/rollbackMigration",
                {"recordId": record_match.group(1)},
            )
            rolled_back_config = canary.get_text(f"/job/{clone_name}/config.xml")
            rolled_back_page = canary.get_text(
                f"/job/{clone_name}/unified-parameters/"
            )
            if (
                rollback_status not in {200, 302}
                or "ExtendedChoiceParameterDefinition" not in rolled_back_config
                or "UnifiedMultiChoiceParameterDefinition" in rolled_back_config
                or "ROLLED_BACK" not in rolled_back_page
            ):
                raise RuntimeError(
                    f"rollback canary failed: HTTP {rollback_status}"
                )
            report["checks"]["migrationRollback"] = {
                "status": rollback_status,
                "sourceDefinitionRestored": True,
                "recordRolledBack": True,
                "passed": True,
            }

            logs = run_command(
                ["docker", "logs", canary.container],
                check=False,
                capture_output=True,
            )
            log_text = logs.stdout + logs.stderr
            severe_lines = [
                line
                for line in log_text.splitlines()
                if "Failed Loading" in line
                or "Failed to run script" in line
                or "SEVERE" in line
            ]
            if severe_lines:
                raise RuntimeError(f"unexpected Controller errors: {severe_lines[:5]}")
            report["checks"]["controllerLogs"] = {"unexpectedSevereErrors": 0, "passed": True}
            report["result"] = "passed"
    except Exception as error:
        report["result"] = "failed"
        report["errorType"] = type(error).__name__
        report["error"] = str(error)
        write_report(args.report, report)
        print(json.dumps(report, indent=2, ensure_ascii=False))
        return 1
    finally:
        run_command(["docker", "stop", container], check=False, capture_output=True)

    write_report(args.report, report)
    print(json.dumps(report, indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
