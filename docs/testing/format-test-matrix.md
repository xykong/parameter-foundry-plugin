# Format Compatibility Test Matrix

## Purpose

This matrix is the executable contract for transport formats. It verifies that
different wire representations preserve the same presence, typed shape,
canonical value, Provider binding, and queueing behavior. It does not require
legacy XML, Java class names, or source-plugin configuration formats to remain
unchanged.

The primary local fixture is
`FormatCompatibilityIntegrationTest`. Real Jenkins canary runs use the same
cases against isolated cloned Jobs and record only redacted aggregate results.

## Transport Matrix

| Transport | Format | Expected decoder contract | Automated evidence |
|---|---|---|---|
| Standard REST | Repeated | One field per selected multiple-choice value | `standardRestFormatsProduceTheSameCanonicalMultipleValue` |
| Standard REST | JSON array string | Exactly one field containing a JSON array string | `standardRestFormatsProduceTheSameCanonicalMultipleValue` |
| Standard REST | Delimited | Exactly one field split by the configured delimiter | `standardRestFormatsProduceTheSameCanonicalMultipleValue` |
| Standard REST | Absent | Missing value and marker invoke the configured default | `standardRestAbsentAndPresenceOnlyEmptyRemainDistinct` |
| Standard REST | Explicit empty | Presence marker without a value produces an empty optional selection | `standardRestAbsentAndPresenceOnlyEmptyRemainDistinct` |
| Browser/native form | Presence metadata | One reserved marker identifies rendered Unified parameters | `browserFormEmitsPresenceMetadataForNativeSubmission` |
| Standard REST | Unified Form | One form field carries one JSON object string | `standardRestFormUsesOneJsonStringField` |
| Typed REST | String | JSON string remains a scalar string | `typedApiPreservesStringArrayObjectAndExplicitNullShapes` |
| Typed REST | Array | JSON array remains an array until canonical sorting | `typedApiPreservesStringArrayObjectAndExplicitNullShapes` |
| Typed REST | Object | JSON object is validated against the stored form schema | `typedApiPreservesStringArrayObjectAndExplicitNullShapes` |
| Typed REST | Explicit empty | Empty array is distinct from an omitted parameter | `typedApiUsesDefaultsForOmittedValuesAndEmptyArrayForExplicitEmpty` |
| Typed REST | Explicit null | Optional single choice becomes canonical `null` | `typedApiPreservesStringArrayObjectAndExplicitNullShapes` |
| Typed REST | Wrong container | Wrong top-level or parameter shape fails with `INVALID_TYPE` | `typedApiRejectsWrongContainersInsteadOfApplyingDefaults` |
| CLI | JSON array | Multiple values use a JSON array argument | `cliJsonArrayAndDelimitedFormatsProduceTheSameCanonicalValue` |
| CLI | Delimited | Multiple values split using the configured compatibility delimiter | `cliJsonArrayAndDelimitedFormatsProduceTheSameCanonicalValue` |
| Provider submission | Dependency JSON | Hidden form metadata carries canonical dependency values | `standardDependencyJsonCanReuseTypedResolveSnapshot` |
| Provider submission | Snapshot ID | A matching build snapshot is reused across resolve and build | `standardDependencyJsonCanReuseTypedResolveSnapshot` |
| Provider submission | Expired snapshot | A stale snapshot is refreshed atomically before build | `expiredSnapshotIsRefreshedBeforeBuild` |
| Native file | Multipart | File content remains a multipart upload, not a typed choice value | `nativeFileMultipartFormatRemainsSeparateFromTypedJson` |

## Negative Matrix

Every negative case must return a stable error family before queue
scheduling. The queue length is unchanged.

| Case | Expected error code | Automated evidence |
|---|---|---|
| Unknown machine value | `UNIFIED_PARAMETER_UNKNOWN_VALUE` | `semanticAndShapeFailuresReturnStableCodesWithoutQueueing` |
| Disabled machine value | `UNIFIED_PARAMETER_DISABLED_VALUE` | `semanticAndShapeFailuresReturnStableCodesWithoutQueueing` |
| Duplicate repeated value | `UNIFIED_PARAMETER_DUPLICATE_VALUE` | `semanticAndShapeFailuresReturnStableCodesWithoutQueueing` |
| Malformed JSON-array transport | `UNIFIED_PARAMETER_MALFORMED_INPUT` | `semanticAndShapeFailuresReturnStableCodesWithoutQueueing` |
| Wrong Typed API container | `UNIFIED_PARAMETER_INVALID_TYPE` | `typedApiRejectsWrongContainersInsteadOfApplyingDefaults` |
| Cross-dependency Provider snapshot | `UNIFIED_PARAMETER_SNAPSHOT_INVALID` | `snapshotBindingFailureIsAConflictAndDoesNotQueue` |

HTTP status rules are:

- malformed or invalid values: `400`;
- unsupported or conflicting current definitions: `409`;
- a snapshot binding conflict: `409`;
- a successful build: `201` with a queue item.

## Provider and Dependency Assertions

For a single logical selection, the matrix compares:

- option format and typed shape;
- dependency JSON and canonical dependency digest;
- snapshot ownership, Provider digest, and option-set digest;
- canonical machine values and deterministic ordering;
- parameter value class and build-record metadata;
- environment export format;
- queue scheduling versus no-queue behavior.

Provider option order, browser click order, and submission order must not change
the canonical multiple-choice list. Labels, groups, tags, and disabled reasons
are presentation metadata and never become machine values.

## File Boundary

Core and reviewed file companion parameters remain on Jenkins native multipart
transport. They are not converted into Provider options or JSON arrays. The
Typed REST API must reject a Job containing an unsupported file definition
atomically rather than silently dropping the uploaded content.

## Canary Procedure

1. Run the local format fixture with the minimum Jenkins baseline and the
   release-target LTS.
2. Create only isolated cloned/canary Jobs in the real Jenkins controller.
3. Verify browser, standard REST, Typed REST, CLI, webhook/remote, upstream,
   Rebuild, Provider snapshot, and native multipart cases.
4. Do not modify business Jobs, uninstall source plugins, or copy production
   credentials, Job XML, parameter values, or internal endpoints into this
   repository.
5. Store operational details in the external Jenkins operations repository and
   commit only a redacted aggregate report here.

## Release Gate

The format gate passes only when all applicable rows pass on both declared
Jenkins versions, every failure preserves the queue invariant, and any
unsupported source or file capability has an explicit native or companion
boundary.
