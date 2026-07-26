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

## Architecture Fitness Functions

Fitness functions are deterministic checks for architecture characteristics,
not prose review reminders. Generated projects should identify the top 1-3
architecture risks and encode cheap checks where practical.

Examples:

- dependency direction and forbidden imports;
- public contract/schema drift;
- provider-specific code crossing clean boundaries;
- migration drift for persistence-backed systems;
- performance, accessibility, security or deployability budgets;
- container health checks and health-aware local dependencies.

Wire automated fitness functions into `.agentic-template/bin/project check`,
`.agentic-template/bin/project ready` or a specialised command they call. When
automation is not yet credible, record the manual validation path and revisit
trigger.

Current project-specific fitness function:

```sh
.agentic-template/bin/project lint
```

It protects the deterministic model boundary by failing when LangChain4j,
picocli, SQLite or JMH implementation dependencies leak into `core/` or
`application/`.

## Required Recording

Record meaningful validation in `HANDOFF.toon.tests_run`.

If validation is skipped or blocked, record:

- command or check;
- reason it did not run;
- risk left open;
- next verification path.

For non-trivial changes, also record the change handoff evidence:

- spec reference, or no-spec rationale;
- fitness-function delta, or no-change rationale;
- knowledge update, proposal or no-record rationale.

## CI Boundary

CI should call repository commands such as `project check`, `project test` and
`project ready`. CI YAML should not duplicate build logic that belongs in the
repository command surface.
