# Extended Choice historical value contract

This record characterizes the source value type for future source-specific history
work. It does not advertise an implemented reader or authorize plugin retirement.

The inspected `ExtendedChoiceParameterValue` extends Core `StringParameterValue`,
has explicit serial UID `7993744779892775177`, and declares no additional instance
fields. Its overridden variable resolver captures the supplied AbstractBuild.
For the matching parameter name it begins with the stored literal value.

The special JSON file behavior requires every following condition at resolution
time: a non-null AbstractBuild, its current project's ParametersDefinitionProperty,
a matching current definition of the native ExtendedChoiceParameterDefinition
class, type `PT_JSON`, and `saveJSONParameterToFile=true`. It writes under the
build's parameters directory and, if available, the workspace parameters directory,
then returns the generated file path. Otherwise it returns the literal. A null
build or a current Foundry definition does not satisfy that native-definition
condition, even if the value came from an earlier JSON build.

Consequently, the native value contract after complete current-definition migration
must be characterized separately from native JSON builds before migration. A
passive Core string reader may fit the former boundary, but serial fields alone
do not prove it. Required evidence includes actual native resolver behavior with
both current native and migrated definitions, environment/history UI/Rebuild,
paused CPS restoration and rollback. The captured resolver inner class is a
separate historical type to investigate; value-only acceptance cannot imply its
restoration or universal Replay support.

No migration may quietly discard the pre-migration JSON file behavior for a Job
that still depends on it. Its new definition and consumer require an explicit
equivalent workflow and acceptance. Historical source definitions, saved Pipeline
source and Replay also remain separate retirement gates.

Evidence was obtained from the exact source value and resolver bytecode, including
the explicit UID, inheritance, resolver predicates and writes. No production
build data was deserialized, and no production Run or CPS execution was loaded.
The real-Jenkins paired resolver test now passes on 2.528.3 with the actual native
plugin. One empty Freestyle build completes SUCCESS. The same native historical
value returns a literal with no definition and a null build; a current native JSON
definition with saving enabled writes the exact content and returns a file path.
Replacing that current definition with Foundry returns the literal and preserves
the existing parameters directory's file sizes and modification times. An
unrelated variable resolves null, environment export stays literal and the UID
matches. This fixture contains no business builders and performs no production
operations. It does not yet prove missing-class XML/River/CPS restoration or
history UI/Rebuild; those gates remain open.

An independently authored, unshipped value-only prototype also passes native-to-reader
and reader-to-native storage tests. Five synthetic literals cover empty, branch
text, padded comma text, quote/XML metacharacters and Unicode. Java serialization,
River and Jenkins/Items/Run XML retain name, value and description, with literal
environment and null-build resolver checks; XML is roundtripped again after read.
These are 50 directional literal/storage combinations, not production records.
The native and prototype classes are loaded in separate processes/classpaths,
without aliases or the plugin's same-name test fixtures. This proves the bounded
passive value storage contract only. Actual HPI activation, cold paused CPS,
UI/Rebuild, captured resolver class and source-definition/Replay checks remain
required before shipping or retiring the source plugin.
