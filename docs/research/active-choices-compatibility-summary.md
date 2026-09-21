# Active Choices Compatibility Validation Summary

- Validation date: August 27, 2026
- Jenkins: `2.568.2`
- Active Choices: `2.8.10`
- Evidence status: sanitized product-research summary

## Purpose

This summary preserves the product-relevant findings that motivated the
Parameter Foundry parity contract. Production Job names, credentials,
controller configuration, and organization-specific upgrade records remain
outside this repository.

## Observed Results

- Build with Parameters supported single choice, multi-select, checkbox, and
  radio interactions in the tested configuration.
- A single comma-delimited API string was accepted for the tested multi-value
  parameter.
- The tested webhook, Pipeline upstream, and remote-trigger paths scheduled
  builds.
- Rebuild preserved the value but rendered the parameter as a plain text
  control rather than the source choice Renderer.
- Repeating the same form field for the tested multi-value parameter produced
  an HTTP 500 response.
- Omitting a multi-select parameter with no configured default resulted in the
  first option being selected in the tested API path.

## Product Conclusions

The test demonstrates useful capability but not one stable semantic contract
across every Jenkins entry point. Parameter Foundry therefore treats the
following as release-blocking requirements:

- repeated, delimiter, typed-array, absent, and explicit-empty input have
  documented deterministic decoding;
- invalid input produces HTTP 4xx and never HTTP 500;
- Rebuild uses the configured Renderer family and server validation;
- no-default behavior is explicit rather than inferred as the first option;
- UI, REST, CLI, Pipeline, webhook, remote trigger, and Rebuild run the same
  evaluation fixtures.

## Reproduction Boundary

The repository UI Lab contains synthetic, non-production Active Choices
profiles and screenshots. Future compatibility claims should be reproduced
there or in committed Jenkins Test Harness fixtures.

The original operational validation record is intentionally not copied
because repository separation prohibits production-specific evidence and Job
data in this project.
