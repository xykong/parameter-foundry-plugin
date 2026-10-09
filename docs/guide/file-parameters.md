# File parameters

Parameter Foundry provides its own file upload parameters in the same HPI.
New Foundry file Jobs do not require the File Parameters plugin. Uploads use
Jenkins multipart transport and are separate from choices, Forms and Providers.

| Parameter | Pipeline symbol | Limit | Build access |
| --- | --- | --- | --- |
| Parameter Foundry Base64 File | `foundryBase64File` | 1 MiB decoded bytes | Base64 in the named environment variable, or a temporary file |
| Parameter Foundry Stashed File | `foundryStashedFile` | 100 MiB bytes | `unstash` using the parameter name, or a temporary file |

Both types have **Name** and **Description** configuration fields. Name is a
unique parameter name of at most 100 letters, digits, underscores, dots or
hyphens, beginning with a letter or underscore. It also names the stash and its
contained file. Description is displayed by Jenkins using its markup formatter.
The sanitized original upload basename is exported as `<name>_FILENAME`.
Upload filenames never select storage paths. Content is not extracted, executed
or MIME-validated; validation of business content belongs in the build.

## Pipeline

```groovy
pipeline {
  agent any
  parameters {
    foundryBase64File(name: 'SMALL', description: 'Small configuration file')
    foundryStashedFile(name: 'ARCHIVE', description: 'Binary upload')
  }
  stages {
    stage('Read upload') {
      steps {
        unstash 'ARCHIVE' // restores a file named ARCHIVE
        withFoundryFileParameter('SMALL') {
          // SMALL contains an agent-local temporary path within this block.
          sh 'test -f "$SMALL"'
        }
        withFoundryFileParameter('ARCHIVE') {
          sh 'test -f "$ARCHIVE"'
        }
      }
    }
  }
}
```

`withFoundryFileParameter(name: 'OPTIONAL', allowNoFile: true)` runs its body
when the parameter is absent. The default is `false`, which fails on absence.
A value of the wrong parameter type always fails, even with `allowNoFile`.
The wrapper removes its unique temporary directory on normal or failed exit.
Freestyle Jobs can use the **Bind Parameter Foundry file** build wrapper with
the same fields. An ordinary `unstash` restores into the workspace and follows
normal workspace cleanup rather than wrapper cleanup.

Base64 content can also be supplied to a downstream build:

```groovy
build job: 'downstream', parameters: [
  foundryBase64File(name: 'SMALL', base64: 'aGVsbG8=')
]
```

Base64 supports Pipeline `input` for small uploads. Stashed File needs a build
parameter lifecycle and is not supported as an `input` step upload. There is no
arbitrary controller-path or cross-Job stash lookup API.

## UI, REST and CLI

Choose the file in **Build with Parameters**, or use the standard multipart API:

```sh
curl -u "$JENKINS_USER:$JENKINS_TOKEN" \
  -F 'ARCHIVE=@./upload.zip' \
  "$JENKINS_URL/job/example/buildWithParameters"
```

Jenkins applies normal authentication, build permission and CSRF rules. An
omitted multipart file produces no value; an uploaded empty file is valid.
Global Jenkins or reverse-proxy upload limits may be stricter than the plugin.

With Jenkins CLI, `-p ARCHIVE=` reads stdin. A nonempty CLI value is Base64
content (limited to 1 MiB decoded bytes); use stdin for larger stashed uploads.
Neither form reads a caller-supplied controller file path.

The Foundry Typed JSON API rejects Jobs containing these binary definitions with
HTTP `409` and `PARAMETER_FOUNDRY_UNSUPPORTED_DEFINITION`. Use multipart instead.

## Retention and Rebuild

Stashed uploads initially occupy a plugin-owned pending-upload directory.
Cancelling a queued build removes that pending file. Once the build starts, a
retained copy belongs to its build directory and follows build deletion and
retention. Rebuild can reuse retained content or accept a replacement upload.
It cannot restore content after the retained file was deleted. Reuse applies to
Foundry uploads; historical File Parameters values are not converted. Supply a
fresh upload when rebuilding a legacy build against a migrated Job. Download
links require read permission on the Job and deliver an octet-stream attachment.
Treat Job read access as access to the uploaded content; these parameters are
not a secret vault. Base64 additionally exposes bytes through environment and
parameter metadata, so do not use it for credentials.

## Migrating File Parameters

The migration assistant offers class B targets for source Base64 File and
Stashed File definitions, preserving names and descriptions. Accept the upload
limits and update `withFileParameter` to `withFoundryFileParameter` (including
Freestyle wrappers). Ordinary `unstash 'NAME'` keeps the same name. Definitions
in a Jenkinsfile must be changed there.

Verify representative binary bytes, original filenames, stash and agent access,
missing-file handling, and Rebuild before treating the migration as complete.
Simulation reports `NEEDS_MANUAL_CHECK`, because historical option-value replay
cannot prove a file lifecycle. Keep the source plugin while any historical
builds, queued/running builds or generated sources still require its classes.
Jenkins Core File parameters are not automatically converted.
