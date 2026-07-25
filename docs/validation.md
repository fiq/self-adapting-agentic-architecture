# Validation

Validation proves behaviour and repository contract, not just a green command.

## Core Rules

- Do not weaken tests to fit broken production behaviour.
- If a test is wrong, state why and fix the expectation.
- If production behaviour is wrong, keep or strengthen the proving test.
- If the harness is broken, repair it without losing representative coverage.
- Pick the cheapest credible layer for the risk.

## Layers

| Layer | Use |
|---|---|
| Unit/domain | Pure logic, invariants and edge cases |
| Contract | Public API, event, file or schema compatibility |
| Component/integration | Real internal wiring and cheap dependencies |
| E2E/manual | Runtime, UX, platform or operational risks CI cannot prove |

## Required Recording

Record meaningful validation in `HANDOFF.toon.tests_run`.

If validation is skipped or blocked, record:

- command or check;
- reason it did not run;
- risk left open;
- next verification path.

## CI Boundary

CI should call repository commands such as `project check`, `project test` and
`project ready`. CI YAML should not duplicate build logic that belongs in the
repository command surface.
