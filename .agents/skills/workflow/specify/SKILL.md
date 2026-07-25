---
name: specify
description: Create proportional OpenSpec-shaped structured change proposals for meaningful changes.
---

# Specify

Produce a proportional spec for a meaningful change using the project
structured-data policy (`PROJECT_PROFILE.toon.structured_data`) as a
`specs/changes/<id>/` proposal:

- `proposal.md` — why, intent, non-goals (Markdown, rationale only);
- `design.md` — meaningful tradeoffs (Markdown, optional);
- structured change artifact — the agent source of truth:
  `ADDED`/`MODIFIED`/`REMOVED`
  deltas, each requirement carrying `WHEN/THEN` scenarios, an `acceptance`
  map from scenario to test (the ATDD bridge), and `tasks`.

Use `specs/templates/change.toon` for the template default. If a generated
project chooses S-expressions for state/contracts, specialise equivalent
templates and validation before relying on them. Link `relates_to` to knowledge
IDs. Validate with `project check-changes`. Trivial mechanical changes need no
spec.
