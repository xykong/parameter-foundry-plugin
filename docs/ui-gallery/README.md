# Jenkins 参数插件真实 UI Gallery

图片由隔离 Jenkins 和真实插件自动生成，不是手工 mock。

交互式版本见 [`index.html`](./index.html)，版本和场景元数据见
[`manifest.json`](./manifest.json)。

## Finite whole-response redirects

Snapshot `0.25.377-SNAPSHOT`, Jenkins 2.568.3, Chromium 153:
[exact-artifact browser report](../testing/results/raw-rest-redirect-ui-lab.json).
The real global form saves an exact owned destination permission and retains
its final-URI failure selector. Five followed statuses, final HTTP failure,
undeclared-target refusal and recovery pass at 1440 and 390 pixels. The dedicated
undeclared-target trap records zero requests. All 36 browser cases pass, including
16 redirect cases; same-HPI native source values/order/defaults match in 21 cases.
Complete Maven verify: 552 tests, zero failures/errors, eight conditional skips.
No builds are submitted. The addresses shown are isolated synthetic peers.
Touch, assistive technology, dynamic resize and unrestricted native redirect
compatibility are not certified. This is component acceptance, not production migration.


[![Final HTTP failure default on mobile](images/parameter-foundry/raw-rest-redirect-final-failure-mobile.png)](images/parameter-foundry/raw-rest-redirect-final-failure-mobile.png)

[![Undeclared destination refusal](images/parameter-foundry/raw-rest-redirect-denied-desktop.png)](images/parameter-foundry/raw-rest-redirect-denied-desktop.png)

## Explicit nonredirect response bodies

Snapshot `0.25.376-SNAPSHOT`, Jenkins 2.568.3, Chromium 153:
[exact-artifact browser report](../testing/results/raw-rest-status-policy-ui-lab.json).
The actual global form changes the policy from its HTTP-status default and saves it.
Four status/default cases pass at 1440 and 390 pixels, with old mappings checked alongside.
Full Maven verification reports 539 tests, zero failures/errors and eight prerequisite skips.
No build is submitted. Configuration evidence uses the actual field viewport; oversized
full-page capture independently triggers Core's breadcrumb defect at a temporary 1×1 layout.
Dynamic resize, touch and assistive behavior are not certified by these captures.

[![Explicit response status policy](images/parameter-foundry/raw-rest-status-policy-config-desktop.png)](images/parameter-foundry/raw-rest-status-policy-config-desktop.png)

[![Empty native 304 default](images/parameter-foundry/raw-rest-status-empty-desktop.png)](images/parameter-foundry/raw-rest-status-empty-desktop.png)

[![Native 308 body on mobile](images/parameter-foundry/raw-rest-status-body-mobile.png)](images/parameter-foundry/raw-rest-status-body-mobile.png)

## Declared DNS failures

Draft `0.24.373-SNAPSHOT`: [browser evidence](../testing/results/raw-rest-dns-ui-lab.json).
The desktop fixture preserves the Connection Profile while restoring only its
owned DNS name. Its JVM DNS caches are disabled explicitly for the test;
production DNS configuration is unchanged. Complete Maven verification passes
523 tests with zero failures/errors and six skips; the exact full-build HPI
passes the browser gate.

[![Declared DNS error choice](images/parameter-foundry/raw-rest-dns-unresolved-desktop.png)](images/parameter-foundry/raw-rest-dns-unresolved-desktop.png)

[![DNS restored with the same profile](images/parameter-foundry/raw-rest-dns-recovered-desktop.png)](images/parameter-foundry/raw-rest-dns-recovered-desktop.png)

## Whole-response REST choices

Snapshot `0.23.371-SNAPSHOT`, Jenkins 2.568.3, Chromium 153. The
[browser report](../testing/results/raw-rest-provider-ui-lab.json) records exact
artifact and coverage. Body/default checks cover 1440 and 390 pixels; fallback,
recovery and native keyboard typeahead cover desktop only. No build submitted.

[![Mobile raw response choice](images/parameter-foundry/raw-rest-mobile.png)](images/parameter-foundry/raw-rest-mobile.png)

[![Recovered choice selected by keyboard typeahead](images/parameter-foundry/raw-rest-recovery-keyboard.png)](images/parameter-foundry/raw-rest-recovery-keyboard.png)

## Unified file delivery modes

Snapshot `0.5.274-SNAPSHOT`: [verification](../testing/file-delivery-modes-verification-2026-10-09.md)
and [browser report](../testing/results/foundry-file-modes-ui-lab.json).

[![Three delivery modes](images/parameter-foundry/file-delivery-modes.png)](images/parameter-foundry/file-delivery-modes.png)

[![Mobile file delivery](images/parameter-foundry/file-delivery-modes-mobile.png)](images/parameter-foundry/file-delivery-modes-mobile.png)

[Configuration](images/parameter-foundry/file-delivery-configuration.png) ·
[Core migration preview](images/parameter-foundry/core-file-migration-preview.png)

## Parameter Foundry file uploads

Generated on a disposable Jenkins 2.568.2 controller without File Parameters.
The [UI Lab report](../testing/results/foundry-files-ui-lab.json) records browser
and platform details, actual desktop/mobile submission, restart and Rebuild.

[![Desktop file upload](images/parameter-foundry/file-upload.png)](images/parameter-foundry/file-upload.png)

[![Mobile file upload](images/parameter-foundry/file-upload-mobile.png)](images/parameter-foundry/file-upload-mobile.png)

## Active Choices

`uno-choice:2.8.10` · Maintained

### Freestyle configuration

[![Active Choices Freestyle configuration](images/active-choices/freestyle-config.png)](images/active-choices/freestyle-config.png)

### Freestyle default state

[![Active Choices Freestyle default state](images/active-choices/freestyle-default.png)](images/active-choices/freestyle-default.png)

### Freestyle user interaction

[![Active Choices Freestyle user interaction](images/active-choices/freestyle-interaction.png)](images/active-choices/freestyle-interaction.png)

### Pipeline default state

[![Active Choices Pipeline default state](images/active-choices/pipeline-default.png)](images/active-choices/pipeline-default.png)

### Pipeline Rebuild state

[![Active Choices Pipeline Rebuild state](images/active-choices/pipeline-rebuild.png)](images/active-choices/pipeline-rebuild.png)

## Jenkins Core Parameters

`jenkins-core:2.568.2` · Core

### Freestyle configuration

[![Jenkins Core Parameters Freestyle configuration](images/core/freestyle-config.png)](images/core/freestyle-config.png)

### Freestyle default state

[![Jenkins Core Parameters Freestyle default state](images/core/freestyle-default.png)](images/core/freestyle-default.png)

### Freestyle user interaction

[![Jenkins Core Parameters Freestyle user interaction](images/core/freestyle-interaction.png)](images/core/freestyle-interaction.png)

### Pipeline default state

[![Jenkins Core Parameters Pipeline default state](images/core/pipeline-default.png)](images/core/pipeline-default.png)

### Pipeline Rebuild state

[![Jenkins Core Parameters Pipeline Rebuild state](images/core/pipeline-rebuild.png)](images/core/pipeline-rebuild.png)

## Custom Checkbox Parameter

`custom-checkbox-parameter:1.76.v08d845691023` · Security warning

### Freestyle configuration

[![Custom Checkbox Parameter Freestyle configuration](images/custom-checkbox/freestyle-config.png)](images/custom-checkbox/freestyle-config.png)

### Freestyle default state

[![Custom Checkbox Parameter Freestyle default state](images/custom-checkbox/freestyle-default.png)](images/custom-checkbox/freestyle-default.png)

### Freestyle user interaction

[![Custom Checkbox Parameter Freestyle user interaction](images/custom-checkbox/freestyle-interaction.png)](images/custom-checkbox/freestyle-interaction.png)

### Pipeline default state

[![Custom Checkbox Parameter Pipeline default state](images/custom-checkbox/pipeline-default.png)](images/custom-checkbox/pipeline-default.png)

### Pipeline Rebuild state

[![Custom Checkbox Parameter Pipeline Rebuild state](images/custom-checkbox/pipeline-rebuild.png)](images/custom-checkbox/pipeline-rebuild.png)

## Editable Choice

`editable-choice:71.v02a291ebbe45` · Up for adoption

### Freestyle configuration

[![Editable Choice Freestyle configuration](images/editable-choice/freestyle-config.png)](images/editable-choice/freestyle-config.png)

### Freestyle default state

[![Editable Choice Freestyle default state](images/editable-choice/freestyle-default.png)](images/editable-choice/freestyle-default.png)

### Freestyle user interaction

[![Editable Choice Freestyle user interaction](images/editable-choice/freestyle-interaction.png)](images/editable-choice/freestyle-interaction.png)

### Pipeline default state

[![Editable Choice Pipeline default state](images/editable-choice/pipeline-default.png)](images/editable-choice/pipeline-default.png)

### Pipeline Rebuild state

[![Editable Choice Pipeline Rebuild state](images/editable-choice/pipeline-rebuild.png)](images/editable-choice/pipeline-rebuild.png)

## Extended Choice Parameter

`extended-choice-parameter:388.ve7b_d0b_920e10` · Deprecated

### Freestyle configuration

[![Extended Choice Parameter Freestyle configuration](images/extended-choice/freestyle-config.png)](images/extended-choice/freestyle-config.png)

### Freestyle default state

[![Extended Choice Parameter Freestyle default state](images/extended-choice/freestyle-default.png)](images/extended-choice/freestyle-default.png)

### Freestyle user interaction

[![Extended Choice Parameter Freestyle user interaction](images/extended-choice/freestyle-interaction.png)](images/extended-choice/freestyle-interaction.png)

### Pipeline default state

[![Extended Choice Parameter Pipeline default state](images/extended-choice/pipeline-default.png)](images/extended-choice/pipeline-default.png)

### Pipeline Rebuild state

[![Extended Choice Parameter Pipeline Rebuild state](images/extended-choice/pipeline-rebuild.png)](images/extended-choice/pipeline-rebuild.png)

## Extensible Choice Parameter

`extensible-choice-parameter:255.vfa_41f46828ef` · Up for adoption

### Freestyle configuration

[![Extensible Choice Parameter Freestyle configuration](images/extensible-choice/freestyle-config.png)](images/extensible-choice/freestyle-config.png)

### Freestyle default state

[![Extensible Choice Parameter Freestyle default state](images/extensible-choice/freestyle-default.png)](images/extensible-choice/freestyle-default.png)

### Freestyle user interaction

[![Extensible Choice Parameter Freestyle user interaction](images/extensible-choice/freestyle-interaction.png)](images/extensible-choice/freestyle-interaction.png)

### Pipeline default state

[![Extensible Choice Parameter Pipeline default state](images/extensible-choice/pipeline-default.png)](images/extensible-choice/pipeline-default.png)

### Pipeline Rebuild state

[![Extensible Choice Parameter Pipeline Rebuild state](images/extensible-choice/pipeline-rebuild.png)](images/extensible-choice/pipeline-rebuild.png)

## HTML Parameters

`html-parameters:10.v9a_56672a_96a_5` · Maintained

### Freestyle configuration

[![HTML Parameters Freestyle configuration](images/html-parameters/freestyle-config.png)](images/html-parameters/freestyle-config.png)

### Freestyle default state

[![HTML Parameters Freestyle default state](images/html-parameters/freestyle-default.png)](images/html-parameters/freestyle-default.png)

### Freestyle user interaction

[![HTML Parameters Freestyle user interaction](images/html-parameters/freestyle-interaction.png)](images/html-parameters/freestyle-interaction.png)

### Pipeline default state

[![HTML Parameters Pipeline default state](images/html-parameters/pipeline-default.png)](images/html-parameters/pipeline-default.png)

### Pipeline Rebuild state

[![HTML Parameters Pipeline Rebuild state](images/html-parameters/pipeline-rebuild.png)](images/html-parameters/pipeline-rebuild.png)

## Json Editor Parameter

`json-editor-parameter:54.v71962f37a_416` · Maintained

### Freestyle configuration

[![Json Editor Parameter Freestyle configuration](images/json-editor/freestyle-config.png)](images/json-editor/freestyle-config.png)

### Freestyle default state

[![Json Editor Parameter Freestyle default state](images/json-editor/freestyle-default.png)](images/json-editor/freestyle-default.png)

### Freestyle user interaction

[![Json Editor Parameter Freestyle user interaction](images/json-editor/freestyle-interaction.png)](images/json-editor/freestyle-interaction.png)

### Pipeline default state

[![Json Editor Parameter Pipeline default state](images/json-editor/pipeline-default.png)](images/json-editor/pipeline-default.png)

### Pipeline Rebuild state

[![Json Editor Parameter Pipeline Rebuild state](images/json-editor/pipeline-rebuild.png)](images/json-editor/pipeline-rebuild.png)

## JSON Parameter

`json-parameter:120.v3e5546f06794` · Maintained

### Freestyle configuration

[![JSON Parameter Freestyle configuration](images/json-parameter/freestyle-config.png)](images/json-parameter/freestyle-config.png)

### Reactive request in flight

[![JSON Parameter Reactive request in flight](images/json-parameter/reactive-loading.png)](images/json-parameter/reactive-loading.png)

### Freestyle default state

[![JSON Parameter Freestyle default state](images/json-parameter/freestyle-default.png)](images/json-parameter/freestyle-default.png)

### Freestyle reactive update

[![JSON Parameter Freestyle reactive update](images/json-parameter/freestyle-interaction.png)](images/json-parameter/freestyle-interaction.png)

### Pipeline default state

[![JSON Parameter Pipeline default state](images/json-parameter/pipeline-default.png)](images/json-parameter/pipeline-default.png)

### Pipeline Rebuild state

[![JSON Parameter Pipeline Rebuild state](images/json-parameter/pipeline-rebuild.png)](images/json-parameter/pipeline-rebuild.png)

## Multiselect Parameter

`multiselect-parameter:1.8` · Maintained

### Freestyle configuration

[![Multiselect Parameter Freestyle configuration](images/multiselect/freestyle-config.png)](images/multiselect/freestyle-config.png)

### Freestyle default state

[![Multiselect Parameter Freestyle default state](images/multiselect/freestyle-default.png)](images/multiselect/freestyle-default.png)

### Freestyle user interaction

[![Multiselect Parameter Freestyle user interaction](images/multiselect/freestyle-interaction.png)](images/multiselect/freestyle-interaction.png)

### Pipeline default state

[![Multiselect Parameter Pipeline default state](images/multiselect/pipeline-default.png)](images/multiselect/pipeline-default.png)

### Pipeline Rebuild state

[![Multiselect Parameter Pipeline Rebuild state](images/multiselect/pipeline-rebuild.png)](images/multiselect/pipeline-rebuild.png)

## REST List Parameter

`rest-list-parameter:362.v52147f86a_172` · Maintained

### Freestyle configuration

[![REST List Parameter Freestyle configuration](images/rest-list/freestyle-config.png)](images/rest-list/freestyle-config.png)

### Freestyle default state

[![REST List Parameter Freestyle default state](images/rest-list/freestyle-default.png)](images/rest-list/freestyle-default.png)

### Freestyle user interaction

[![REST List Parameter Freestyle user interaction](images/rest-list/freestyle-interaction.png)](images/rest-list/freestyle-interaction.png)

### Pipeline default state

[![REST List Parameter Pipeline default state](images/rest-list/pipeline-default.png)](images/rest-list/pipeline-default.png)

### Pipeline Rebuild state

[![REST List Parameter Pipeline Rebuild state](images/rest-list/pipeline-rebuild.png)](images/rest-list/pipeline-rebuild.png)

### Provider empty result

[![REST List Parameter Provider empty result](images/rest-list/provider-empty.png)](images/rest-list/provider-empty.png)

### Provider parsing error

[![REST List Parameter Provider parsing error](images/rest-list/provider-error.png)](images/rest-list/provider-error.png)

## Validating String Parameter

`validating-string-parameter:345.v0f4330488a_f6` · Maintained

### Freestyle configuration

[![Validating String Parameter Freestyle configuration](images/validating-string/freestyle-config.png)](images/validating-string/freestyle-config.png)

### Freestyle default state

[![Validating String Parameter Freestyle default state](images/validating-string/freestyle-default.png)](images/validating-string/freestyle-default.png)

### Freestyle validation error

[![Validating String Parameter Freestyle validation error](images/validating-string/freestyle-interaction.png)](images/validating-string/freestyle-interaction.png)

### Pipeline default state

[![Validating String Parameter Pipeline default state](images/validating-string/pipeline-default.png)](images/validating-string/pipeline-default.png)

### Pipeline Rebuild state

[![Validating String Parameter Pipeline Rebuild state](images/validating-string/pipeline-rebuild.png)](images/validating-string/pipeline-rebuild.png)

## Parameter Foundry

`parameter-foundry:0.1-SNAPSHOT` · Target implementation

These screenshots are generated by `uv run tools/e2e/run-e2e.py` from the
current HPI on Jenkins `2.568.2-lts-jdk21`.

### Light theme build form

[![Parameter Foundry light theme](images/parameter-foundry/light.png)](images/parameter-foundry/light.png)

### Dark Theme system mode

[![Parameter Foundry dark theme](images/parameter-foundry/dark.png)](images/parameter-foundry/dark.png)

### Forced-colors high contrast

[![Parameter Foundry high contrast](images/parameter-foundry/high-contrast.png)](images/parameter-foundry/high-contrast.png)

### Keyboard focus and 10,000-option list

[![Parameter Foundry keyboard focus](images/parameter-foundry/keyboard-focus.png)](images/parameter-foundry/keyboard-focus.png)

### 390px responsive layout

[![Parameter Foundry mobile](images/parameter-foundry/mobile.png)](images/parameter-foundry/mobile.png)

### No-JavaScript fallback

[![Parameter Foundry no JavaScript](images/parameter-foundry/no-javascript.png)](images/parameter-foundry/no-javascript.png)

### Configuration import preview

[![Parameter Foundry configuration preview](images/parameter-foundry/configuration-preview.png)](images/parameter-foundry/configuration-preview.png)

### Migration preview

[![Parameter Foundry migration preview](images/parameter-foundry/migration-preview.png)](images/parameter-foundry/migration-preview.png)

### Foundry Form

[![Parameter Foundry form](images/parameter-foundry/foundry-form.png)](images/parameter-foundry/foundry-form.png)

### Source-plugin retirement dashboard

[![Parameter Foundry retirement dashboard](images/parameter-foundry/retirement-dashboard.png)](images/parameter-foundry/retirement-dashboard.png)

### Rebuild

[![Parameter Foundry Rebuild](images/parameter-foundry/rebuild.png)](images/parameter-foundry/rebuild.png)

## Finite raw TLS declaration component (0.24.374-SNAPSHOT)

Exact complete-build HPI browser checks on Jenkins2.568.3/Java21, Chromium153,
macOS host, English UI; 1440×1000 and390×844 viewports. No builds submitted.

- [TLS declaration fields](./images/parameter-foundry/raw-rest-tls-fingerprint-configuration-desktop.png)
- [Declared TLS choice, desktop](./images/parameter-foundry/raw-rest-tls-declared-desktop.png)
- [Declared TLS choice, mobile](./images/parameter-foundry/raw-rest-tls-declared-mobile.png)
- [Unknown TLS refusal after scrolling, mobile](./images/parameter-foundry/raw-rest-tls-unknown-mobile.png)
- [Recovery after explicit valid-origin change](./images/parameter-foundry/raw-rest-tls-valid-origin-desktop.png)

Mobile Core sticky controls can initially overlap the final status; the error was
scrolled into view and hit-tested unobscured. A separate parameter-free Core Job
reproduces Jenkins2.568.3's long-name breadcrumb overflow exception. Product
fixtures use shorter names and retain strict pageerror checks. These checks do not
certify screen-reader/native-popup-arrow behavior or production migration.


## Legacy Git DEFAULT list selection (0.26.380-SNAPSHOT)

Real native List Git Branches76 and Foundry pages, Jenkins2.568.3/JDK21,
Chromium153.0.8010.12, macOS host. The literal API default is absent from
the offered options, so both lists remain unselected. Mobile captures are
scrolled and top/bottom hit-tested clear of Core's sticky buttons.

- [Native,1440x1000](./images/parameter-foundry/git-default-native-1440.png)
- [Foundry,1440x1000](./images/parameter-foundry/git-default-foundry-1440.png)
- [Native,390x844](./images/parameter-foundry/git-default-native-390.png)
- [Foundry,390x844](./images/parameter-foundry/git-default-foundry-390.png)

[UP-AC-077 evidence](../testing/results/git-default-list-native-ui-lab.json)
records30paired UI cases and42captured/detached form pairs. No production
repository or credentials are used. Physical-device and screen-reader testing
remain separate. The long-name Core breadcrumb control is not a product pass.
