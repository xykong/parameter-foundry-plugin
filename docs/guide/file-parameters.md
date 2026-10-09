# File parameters

Parameter Foundry provides one `foundryFile` entry with three delivery modes.
The older `foundryBase64File` and `foundryStashedFile` entries remain supported.
No File Parameters plugin is needed for these first-party types. File content
uses Jenkins multipart transport, separately from choices, Forms and Providers.

| Delivery mode | Default upload limit | Build access |
| --- | --- | --- |
| `workspace` (default) | 100 MiB | A file at a configured workspace-relative destination; the named environment variable contains the original upload basename |
| `base64` | 1 MiB decoded bytes | Base64 in the named environment variable, or a temporary file through the wrapper |
| `stash` | 100 MiB | `unstash` using the parameter name, or a temporary file through the wrapper |

These sizes are configurable Foundry defaults, not limits prescribed by Jenkins
Core or File Parameters. `maxUploadBytes: 0` inherits the selected delivery mode's
default. A positive byte count overrides it; negative values are rejected.
Base64 also has Java string/array and process environment limits, so use workspace
or stash for large content. Controller/proxy multipart limits may be stricter.

## Configuration fields

| Field | Meaning |
| --- | --- |
| `name` | Parameter identity. Prefer a simple letter/underscore-led identifier of at most 100 characters. Base64/stash names permit letters, digits, underscore, dot and hyphen. Workspace mode additionally accepts safe relative-path names for Core migration compatibility. |
| `description` | Description shown beside the upload control, formatted by Jenkins. |
| `deliveryMode` | `workspace`, `base64` or `stash`; defaults to `workspace`. |
| `workspacePath` | Workspace-mode relative destination; defaults to `name`. Ignored for other modes. Parent folders are created and ordinary existing files are replaced. |
| `maxUploadBytes` | Decoded byte limit; `0` inherits the mode default. Also available on the two older definitions. |

Workspace paths reject absolute paths, parent/dot/empty segments, drive/stream
syntax, control characters, reserved device names and symbolic-link destinations.
The original upload filename is metadata and never selects a storage path.
Content is not unpacked or executed by the plugin.

## Workspace delivery and Core compatibility

```groovy
parameters {
  foundryFile(name: 'UPLOAD', deliveryMode: 'workspace',
              workspacePath: 'inputs/data.zip', maxUploadBytes: 536870912)
}
```

Freestyle automatically places the upload before build steps. Pipeline must
allocate its workspace and use the wrapper before reading the destination:

```groovy
node {
  withFoundryFileParameter('UPLOAD') {
    sh 'test -f inputs/data.zip'
    // UPLOAD is the original filename, preserving Core's environment semantics.
  }
}
```

In workspace mode the wrapper does not replace the filename environment variable
with a temporary path, and the destination remains after the block. Workspace
mode does not add `<name>_FILENAME`, preserving independently configured variables
with that name. Base64/stash export that suffix. If the upload is omitted, no file
is placed or deleted, including an old file left in that workspace. Clean the
workspace explicitly when your workflow requires a fresh directory.

Migrating Core File preserves its original parameter identity and relative
location, including path-like names such as `inputs/data.zip`. Those unusual
environment keys retain Core's shell limitations; for new Jobs, use a simple
parameter name with a separate `workspacePath`.

## Base64 and stash delivery

```groovy
pipeline {
  agent any
  parameters {
    foundryFile(name: 'SMALL', deliveryMode: 'base64', maxUploadBytes: 2097152)
    foundryFile(name: 'ARCHIVE', deliveryMode: 'stash')
  }
  stages {
    stage('Read upload') {
      steps {
        unstash 'ARCHIVE' // restores a file named ARCHIVE
        withFoundryFileParameter('SMALL') { sh 'test -f "$SMALL"' }
        withFoundryFileParameter('ARCHIVE') { sh 'test -f "$ARCHIVE"' }
      }
    }
  }
}
```

For these two modes, the wrapper binds the parameter environment variable to an
agent-local temporary path and removes its unique directory on normal or failed
exit. `allowNoFile: true` allows an absent value; a wrong-type value always fails.
Freestyle can use the **Bind Parameter Foundry file** wrapper with the same
`name` and `allowNoFile` fields. Ordinary `unstash` uses normal workspace cleanup.

The older configuration symbols remain valid. Small downstream values still use
`foundryBase64File(name: 'SMALL', base64: 'aGVsbG8=')`; the receiving Job's current
file type and upload limit are checked before delivery. Base64 is also supported
for Pipeline `input`; workspace and stash are not mid-build upload transports.

## UI, REST and CLI

Choose a file in **Build with Parameters**, or send standard multipart content:

```sh
curl -u "$JENKINS_USER:$JENKINS_TOKEN" \
  -F 'UPLOAD=@./upload.zip' \
  "$JENKINS_URL/job/example/buildWithParameters"
```

Jenkins applies authentication, build permissions and CSRF rules. Omitted files
produce no value; an explicitly uploaded empty file is valid. With Jenkins CLI,
`-p UPLOAD=` reads stdin. A nonempty CLI value is Base64-encoded file content.
Both forms enforce the configured decoded-byte limit; neither reads a supplied
controller path. Workspace-mode CLI stdin exports `stdin` as the original name.

Typed JSON rejects Jobs containing binary definitions with HTTP `409` and
`PARAMETER_FOUNDRY_UNSUPPORTED_DEFINITION`. Use multipart for file bytes.

## Retention, Rebuild and migration

Workspace and stash uploads begin in a plugin-owned staging directory. Cancelled
queued uploads and rejected HTTP/CLI submissions are removed immediately. Crash
orphans older than 15 minutes are recovered periodically after Jenkins finishes
starting; pending queue items, running builds and active uploads are protected.
Once started, a copy belongs to the build directory
and follows build retention/deletion. Each Rebuild gets its own retained copy;
removing an earlier build does not invalidate a later copy. Rebuild uses the
current Job's delivery configuration and limit. A delivery-mode change needs a
replacement upload; reducing a limit below retained content rejects reuse.

Downloads require Job read permission and use attachment/octet-stream headers.
Treat Job read access as access to uploaded bytes. Base64 additionally exposes
content in environment and parameter metadata and is unsuitable for secrets.

Core File and File Parameters definitions have class B migrations. Review limits,
workspace placement or wrapper changes, and historical dependencies. Core
workspace paths and original filename keys are preserved; `unstash` keeps its
name. Change `withFileParameter` to `withFoundryFileParameter` for source-plugin
wrappers, and change generated Pipeline definitions in their Jenkinsfile.

Binary simulation returns `NEEDS_MANUAL_CHECK`; option-value replay cannot prove
upload lifecycle equivalence. Verify bytes, original names, remote access, missing
files and Rebuild. Historical Core/source-plugin values are not converted:
rebuilding those legacy builds against a migrated Job needs a fresh upload. Keep
File Parameters installed while historical/generated/queued references need it.
