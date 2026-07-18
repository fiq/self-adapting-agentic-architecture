---
name: specify
description: Create proportional OpenSpec-shaped, TOON-encoded change proposals for meaningful changes.
---

# Specify

Produce a proportional spec for a meaningful change as a
`specs/changes/<id>/` proposal:

- `proposal.md` — why, intent, non-goals (Markdown, rationale only);
- `design.md` — meaningful tradeoffs (Markdown, optional);
- `change.toon` — the agent source of truth: `ADDED`/`MODIFIED`/`REMOVED`
  deltas, each requirement carrying `WHEN/THEN` scenarios, an `acceptance`
  map from scenario to test (the ATDD bridge), and `tasks`.

Use `specs/templates/change.toon`. Link `relates_to` to knowledge IDs.
Validate with `project check-changes`. Trivial mechanical changes need no spec.
