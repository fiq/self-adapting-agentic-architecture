# Testing

Drive design outside-in, from the boundary in (ATDD-aligned): a `change.toon`
scenario's acceptance test is written first and fails for the right reason
before any implementation.

```
WHEN/THEN scenario ──► acceptance test (fails) ──► drive inward ──► passes
```

Choose the boundary test's fidelity by risk and known architectural direction:

```
acceptance (end-to-end)   high value / risk, user-visible
component-integration     bounded slice, real internal wiring
subcutaneous              logic under the UI where UI cost > its risk
```

Underneath, keep a testing-trophy balance and a thin real-dependency
confirmation layer where semantics matter:

```
                         E2E
                          /\
                         /few\
                       /------\
                      /component\
                    /integration\
                   /------------\
                  / contract     \
                 /----------------\
                / unit/domain      \
               /____________________\
```

Use real dependencies where semantics matter and cost is reasonable. Do not let
mocked tests overclaim confidence. See
`.agents/skills/workflow/outside-in-tdd/SKILL.md`.
