# File Parameters historical value contract

This characterizes the inspected native artifact for future compatibility work;
it does not describe an implemented reader or approve source retirement.

| Native type | Serial UID | Declared instance state |
| --- | --- | --- |
| AbstractFileParameterValue | -3637270082524081687 | String filename |
| StashedFileParameterValue | 1 | String tmpFile |
| Base64FileParameterValue | -5447497467668302565 | String base64 |

The abstract type extends Core ParameterValue. Both concrete types inherit that
abstract layer, so a string-value alias cannot reproduce their hierarchy or data.
The UIDs were measured against the inspected classes in an isolated JVM; no
constructor, production Run/CPS object or uploaded data was instantiated/read.

Native Stashed values initially reference a file in source-managed temporary
storage. Environment preparation may stash that file for the supplied build,
delete its temporary directory and clear tmpFile. Later reads unstash from the
build into a temporary directory and return its safe entry. The native safe-name
rule and stash key are distinct: a parameter name outside the accepted pattern
uses a fallback file entry, while the stash still uses the parameter name.
The inherited filename metadata participates in environment export and views.

Required compatibility evidence must therefore distinguish completed-stash and
pending-temporary-upload states. Merely preserving filename or accepting XML
cannot demonstrate binary restoration, download, wrapper cleanup or rebuild.
Do not silently discard a pending upload reference or reinterpret it as a Foundry
opaque upload identifier. The core plugin must not read arbitrary controller paths;
any pending-source storage support needs a separate reviewed ownership contract.

For each supported family, prove native-to-reader and reverse storage behavior,
byte identity in the build context, missing/expired data handling, ownership and
permission checks, filename/environment behavior, temporary cleanup, cold paused
Pipeline restoration and actual history/Rebuild UI. Original Pipeline symbols,
definitions, wrapper executions and Replay remain separate source-retirement gates.
New Foundry uploads retain their existing opaque-ID storage and transport contracts.

The native byte baseline passes on Jenkins 2.568.3 with the inspected source
artifact. One neutral Pipeline pauses while a Base64 value and Stashed values
named `UPLOAD` and `x` are inspected. A thirteen-byte synthetic binary payload
restores identically through all three native streams, including the short-name
fallback. Original upload temp files disappear and tmpFile clears after environment
preparation. Filename environment metadata matches; Base64 environment contains
the original encoding. Streams are closed and the neutral build completes SUCCESS.
This proves the native in-build baseline only: after-completion retention,
cold historical reads/download and an independent reader are still unverified.

A complete restart of that finished neutral build confirms the retention boundary:
Base64 bytes restore from persisted state; both ordinary Stashed values report
source data unavailable with native AbortException. No new build is run. This is
the measured ordinary fixture behavior, not a claim that all historical stashes
are missing. An explicit PreserveStashes configuration needs its own paired test;
the replacement must preserve available bytes and faithfully handle already
unavailable source data without fabricating content.

The paired explicit-preservation fixture sets PreserveStashesJobProperty to keep
five builds. After the same neutral build completes and a new full process starts,
both Stashed values and Base64 restore all thirteen expected bytes. No new build
is scheduled by the historical read. This supplies distinct available/missing
native baselines; an independent reader must now reproduce each within its scope.

An independently authored, unshipped typed prototype now reads copies of both
native fixtures after removing the source plugin and starting complete new
2.568.3 processes. Ordinary Stashed values report the same native AbortException;
Base64 restores exact bytes. In the preserved copy both Stashed names, including
the short-name fallback, and Base64 restore identical bytes. Historical filename
and reader class ownership verify, with no new builds or historical file rewrites.
The prototype uses build-bound StashManager operations and its own temporary
directory; it never opens the stored controller tmpFile path. Non-null source
references explicitly remain outside this prototype's contract and must be
investigated without silently dropping them. No upload factories, download API,
wrapper, product opt-in/lifecycle or universal CPS guarantee is provided by this
research result. Remaining states and product integration still need proof.


The prototype's separate-JVM storage test now passes 120 directional combinations:
twelve typed metadata values across Java, River and Jenkins/Items/Run XML, written
by native then read by prototype and vice versa. Filename cases include Unicode,
XML metacharacters and empty text; Base64 cases include empty and binary data.
Names/descriptions, filename environment and encoded-byte identity match, with
another XML roundtrip after read. Completed Stashed storage here covers metadata;
its available/missing byte parity is the separate real-controller result above.
Pending references and the remaining product/permission/wrapper/CPS gates are
still unproven.

The private typed prototype also passes a Jenkins 2.568.3 native-to-reader-to-native
paused Pipeline sequence through two full process restarts. Base64 and completed
Stashed values are retained as saved CPS locals; metadata and encoded value survive,
and the reader restores both thirteen-byte payloads from historical ParametersAction.
The same Pipeline resumes under the native plugin and completes SUCCESS. No
business logic or production build/history is used. This does not cover a paused
native wrapper execution, pending source temp reference, download/permission UI,
or product packaging/default-off/activation controls.
