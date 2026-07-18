# Change Schema

A change proposal lives in `specs/changes/<id>/` as `proposal.md` (why),
optional `design.md` (tradeoffs) and `change.toon` (the agent source of truth).
`change.toon` is validated by `.agentic-template/bin/check-changes`.

## Structure

```yaml
change:
  id: CHG-001                 # required
  title: Short change title   # required
  status: proposed            # proposed | accepted | delivered | archived
  intent: One sentence.       # required
  audience_level: developer   # normal_folk | developer
  relates_to:                 # optional; knowledge IDs (must resolve)
    - SYS-001

deltas:                       # required, at least one
  - op: ADDED                 # ADDED | MODIFIED | REMOVED
    capability: theming       # required
    requirement: The app supports a dark theme.   # required
    scenarios:                # required, at least one (except REMOVED)
      - id: S1                # required, unique within the change
        when: user clicks the theme toggle        # required
        then: the app switches to dark mode       # required
        and:                  # optional
          - the choice persists across reloads

acceptance:                   # required, at least one
  - scenario: S1              # must reference a scenario id in deltas
    test_id: theming/dark-mode-toggle             # required
    layer: acceptance         # acceptance | component-integration | subcutaneous | unit

tasks:                        # optional
  - id: T1
    slice: failing acceptance test for S1
    status: pending

non_goals: []                 # optional
risks: []                     # optional
```

## Validation rules

- `change.id`, `change.title`, `change.intent`, `change.status` are present.
- `status` is one of the allowed values.
- Every delta has `op` in `ADDED`/`MODIFIED`/`REMOVED`, a `capability` and a
  `requirement`.
- Non-`REMOVED` deltas have at least one scenario; each scenario has `id`,
  `when` and `then`.
- Scenario ids are unique within the change.
- Every `acceptance` entry references an existing scenario id and has a
  `test_id` (the ATDD bridge). Every non-`REMOVED` scenario is covered by at
  least one acceptance entry.
- Every `relates_to` value that looks like a knowledge ID (`PREFIX-000`)
  resolves to an existing `.agents/knowledge/` entry.
