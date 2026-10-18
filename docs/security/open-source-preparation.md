# Preparing a Public Source Repository

Publication is a separate operation from sanitizing a checkout. A clean current
file tree does not make old commits, tags, release archives, or screenshots safe
to publish.

## Publication scope

Include product source, synthetic tests, build configuration, public guides,
license notices, and reviewed UI Lab images. Keep controller configuration,
authentication files, organization-specific fixtures, operational evidence,
scanner reports containing private identifiers, and old release binaries in an
external private archive. The public maintainer identity is
`xy.kong <xy.kong@gmail.com>`.

The organization-branch migration fixture uses the synthetic `ORG_BRANCH` mode.
It is not an upstream List Git Branches mode and does not identify any private
fork. Unknown source modes remain subject to the adapter's explicit review
boundary.

Historical `dist/` paths and artifact digests in release records refer to archived
binaries. They are not files distributed in the public source checkout.

## Authorized In-Place History Cleanup

When all history in the existing repository must be publishable, create a
complete backup of its Git metadata outside the repository before rewriting.
Obtain explicit authorization to rewrite commit and tag IDs. Preserve the
approved public author and committer identity.

Rewrite all local branches, remote-tracking references, annotated tag messages,
file paths, and text blobs. Remove historical binary release archives rather
than assuming text replacement can sanitize their contents. Inspect historical
images independently. Keep replacement rules and scanner reports containing
private identifiers outside the public repository.

Linked worktree indexes can keep original blobs alive after the refs have been
rewritten. Back up and check those worktrees for staged and unstaged edits
before synchronizing them to their rewritten branches. Preserve any user edits
outside the repository instead of discarding them.

Expire reflogs, repack and prune obsolete objects, then enumerate the complete
object database with `git cat-file --batch-all-objects`. Scan commit, tag, tree,
and blob data and verify there are no remaining unreachable objects. Scanning
only `git log --all` or `git rev-list --all` is insufficient for this check.
Use `git fsck --full --strict` to verify repository integrity. Confirm that the
current source is unchanged and that the expected branch and tag names remain.

Remove remote connections during cleanup so a routine fetch cannot reintroduce
old history. Publishing or replacing remote history remains a separately
authorized operation. Existing clones and remote copies are not sanitized by a
local rewrite.

## Isolated Snapshot Alternative

The following workflow is an alternative when preserving the repository's
history is not required. It does not sanitize the original repository.

## Verification

1. Export only the reviewed tracked files to a separate candidate directory.
   Exclude `.git`, ignored files, caches, local builds, and historical binaries.
   Preserve the existing private repository and its references unchanged.
2. Scan file contents and paths against an external private denylist. Scan
   archives recursively; a text-only scan cannot inspect nested JAR contents.
3. Run a secret scanner on the exported files and on every history reference
   intended for publication. Review each finding without printing credentials.
   Jenkins plugin dependency versions may resemble generic API keys; classify
   exact findings individually rather than excluding whole directories.
4. Inspect screenshot text and metadata. Record the provenance of any synthetic
   addresses from isolated UI Lab networks. OCR is a discovery aid and can miss
   small or obscured text.
5. Run `python3 tools/migration/validate-fixtures.py` and `mvn -B -ntp verify`.
   Record test prerequisites and skips rather than treating them as passes.
6. Initialize a separate candidate repository from the sanitized snapshot when
   the old history contains private material. Do not copy the original Git
   object database, remotes, tags, or Git bundles. If preserving full history is
   required instead, prepare a separately authorized history sanitization and
   rescan all reachable objects.
7. Review the candidate file manifest and scan results before authorizing a
   public push. A sanitized snapshot does not authorize changing an existing
   remote's visibility, rewriting published history, or publishing old tags.

The repository extends the default Gitleaks rules with a rule-specific,
path-and-exact-line allowlist for reviewed dependency pins. A negative control
confirmed that a synthetic secret added on the next line of the same file is
still detected. See the [upstream configuration reference](https://github.com/gitleaks/gitleaks#configuration).

Example scanner invocations, with reports kept outside the public directory:

```bash
gitleaks dir /path/to/public-candidate --redact --max-archive-depth 5 \
  --report-format json --report-path /private/reports/tree.json
gitleaks git /path/to/public-candidate --redact --log-opts='--all' \
  --report-format json --report-path /private/reports/history.json
```

When findings are resolved, rescan the exact candidate that will be pushed.
Source preparation does not create a new production plugin release.
