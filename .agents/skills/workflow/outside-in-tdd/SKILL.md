---
name: outside-in-tdd
description: Drive design from the boundary in, starting from a change scenario's acceptance test.
---

# Outside-in TDD

## Outcome

Drive design from the boundary in. Each `change.toon` scenario's acceptance
test is written first (ATDD) and fails for the right reason before any
implementation, then design pressure moves inward.

## Loop

```
change.toon scenario (WHEN/THEN)
   ▼ acceptance[].test_id  ── write the boundary test first (it fails)
   ▼ drive inward ── collaborators appear as needed; mock at boundaries
   ▼ inner unit tests ── shape the design
   ▼ boundary test passes ── refactor (boy-scout) with tests green
   ▼ real-dependency confirm ── thin layer where cheap and material
```

## Choosing the boundary test's fidelity (by risk)

| Fidelity | Use when |
|---|---|
| acceptance (end-to-end) | user-visible behaviour, high value or risk |
| component-integration | a bounded slice with real internal wiring |
| subcutaneous | logic just under the UI; UI cost outweighs its risk |

Pick per scenario from actual risk and known architectural direction, not a
fixed rule. Keep a thin real-dependency confirmation layer (Testcontainers
where cheap) for boundaries that inner tests mock.

## Rules

- The acceptance test comes from the scenario, not the implementation.
- Mock at boundaries to drive design; do not let mocked tests overclaim — real
  semantics that matter get a real-dependency test.
- Refactor only with tests green; leave the path cleaner (boy-scout).

## Do not

- Write the implementation before its failing boundary test.
- Force acceptance-level fidelity where a cheaper layer proves the same risk.
