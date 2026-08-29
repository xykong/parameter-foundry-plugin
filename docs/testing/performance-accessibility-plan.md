# Performance and Accessibility Test Plan

## 1. Reference Environments

Release results identify:

- Jenkins core version;
- plugin build commit;
- Java runtime;
- browser and browser version;
- OS/architecture;
- allocated CPU and memory;
- cold or warm cache;
- option count and average field lengths.

The default repeatable CI profile is:

- 4 vCPU;
- 8 GiB memory;
- local Jenkins with no external network dependency;
- headless stable Chrome;
- warm Jenkins process;
- five measured runs after one warm-up run.

Absolute numbers from a developer laptop are informational unless the
environment is recorded.

## 2. `1.0` Performance Gates

| Scenario | Gate |
|----------|------|
| 1,000 Inline options, warm local navigation | navigation-to-interactive p95 within 1.5 seconds |
| 1,000 Inline options, enhanced build page | interactive within 500 ms after model availability |
| 10,000 Inline options, warm local navigation | navigation-to-interactive p95 within 2.5 seconds |
| 10,000 Inline options, search | input-to-visible-result p95 within 100 ms |
| virtualized option DOM | no more than 200 option nodes |
| selection toggle | p95 within one 100 ms interaction window |
| 10,000-option enhanced model payload | no more than 5 MiB uncompressed and recorded |
| configuration validation of 10,000 options | completes within 1 second on the reference profile |

Timing definitions:

- “model availability” excludes Jenkins page navigation but includes plugin
  parsing/index construction;
- navigation-to-interactive starts before the Jenkins page request and ends
  when the parameter accepts keyboard input;
- search timing starts at input event and ends after painted result;
- p95 is calculated across at least 100 representative queries;
- queries include prefix, contains, no-result, group, tag, and Unicode labels.

No-JavaScript performance and functional gates apply to at most 200 static
options. Larger definitions must fail into the documented safe explanatory
state without rendering thousands of option nodes.

## 3. Provider Gates

Starting in `1.1`:

- default timeout 5 seconds, hard configured maximum 30 seconds;
- timeout does not block Jenkins request threads indefinitely;
- identical concurrent requests are coalesced;
- response-size and option-count limits are enforced before DOM rendering;
- cache hit/miss and last-known-good paths have separate benchmarks;
- 50 concurrent preview requests do not schedule 50 identical outbound calls.

## 4. Browser Memory

For the 10,000-option fixture:

- record heap before model load, after indexing, and after repeated filtering;
- no monotonic growth across 100 search/clear cycles;
- detached option nodes remain bounded;
- closing/removing a parameter component releases listeners and indexes.

## 5. Accessibility Gates

Automated:

- no serious/critical accessibility-tool violations;
- labels, descriptions, required state, and errors have programmatic
  relationships;
- valid roles and attributes for combobox/listbox/radio/checkbox;
- color contrast passes WCAG 2.2 AA in light, dark, and high-contrast themes.

Manual:

- complete keyboard-only configuration and build submission;
- screen-reader announcement of option count, selection, disabled reason,
  loading, error, and validation state;
- focus remains visible and stable during virtualization;
- zoom to 200% without loss of function;
- 390px viewport without horizontal control loss;
- reduced-motion setting avoids non-essential motion.

## 6. Regression Evidence

CI retains:

- raw benchmark JSON;
- environment manifest;
- browser trace for failures;
- accessibility report;
- screenshots for reference states;
- comparison against the last release.

A regression exceeding 20% requires investigation even when the absolute gate
still passes.

## 7. Current `1.0` Evidence

The August 29, 2026 reproducible run is stored in:

- [`results/1.0-e2e-benchmark.json`](./results/1.0-e2e-benchmark.json);
- [`results/1.0-accessibility.json`](./results/1.0-accessibility.json);
- [`../ui-gallery/images/unified-parameters/`](../ui-gallery/images/unified-parameters/).

The run uses a Jenkins `2.568.2-lts-jdk21` controller limited to 4 vCPU and
8 GiB, Chromium, 10,000 Inline options, one warm-up navigation, five measured
navigation/validation runs, and 100 measured search and selection operations.
