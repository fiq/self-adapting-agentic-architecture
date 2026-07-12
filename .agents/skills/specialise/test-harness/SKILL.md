---
name: test-harness
description: Specialise a testing trophy harness from runtime, framework and risk evidence; expose applicable commands.
---

# Test Harness

Inspect language, framework, application shape, existing tests and CI.
Identify behavioural and integration risks, then select a small testing
trophy. Select test layers from actual risks, not from a fixed checklist.

## Expose applicable commands

- `.agentic-template/bin/project test`
- `.agentic-template/bin/project contract-test`
- `.agentic-template/bin/project integration-test`
- `.agentic-template/bin/project component-test`
- `.agentic-template/bin/project e2e-test`
- `.agentic-template/bin/project image-test`
- `.agentic-template/bin/project compose-test`
- `.agentic-template/bin/project infra-check`

Only expose commands that apply to the project. Non-applicable commands must
be explicitly recorded as such in `PROJECT_PROFILE.toon` or
`AGENTS.md`.

## Real dependency semantics

Use real dependency semantics when they are cheap and materially important.
Prefer Testcontainers for lifecycle-managed integration-test dependencies
where the stack supports it.

## After `/init`

Do not leave generic placeholders after `/init`. Unspecialised targets must
fail clearly and point to `.agentic-template/bin/project init`.

## Do not

- Add every test layer by default.
- Stub commands with no-op exits.
- Hide test failures behind a passing exit code.
