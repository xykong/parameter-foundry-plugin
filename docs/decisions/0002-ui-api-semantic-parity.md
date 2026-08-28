# ADR 0002: UI and API Semantic Parity

- Status: Accepted
- Date: August 28, 2026

## Decision

All Jenkins entry points must use one parameter evaluation service for:

- provider resolution;
- defaults and empty values;
- canonical value parsing;
- validation;
- `ParameterValue` creation;
- environment export;
- stable error codes.

UI renderers may differ visually, but the same logical input must produce the
same canonical value and validation result through UI, REST, typed REST, CLI,
Pipeline `build`, webhook, remote trigger, and Rebuild.

Any parity failure is a release blocker.
