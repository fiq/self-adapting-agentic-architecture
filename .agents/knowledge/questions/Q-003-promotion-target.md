---
id: Q-003
type: question
title: Candidate promotion target
status: open
summary: The first promotion implementation needs an explicit target such as a local candidate branch, registry entry or protected integration branch.
owners:
  - project-lead
relates_to:
  - SYS-001
risks:
  - RISK-001
evidence:
  - CUSTOMIZE_THIS_PROJECT.toon
  - PROJECT_PROFILE.toon
  - specs/changes/CHG-002-live-loop-policy/design.md
review_after: 2026-10-26
---

# Candidate Promotion Target

Promotion is deterministic, but the destination is not yet approved. `CHG-002`
proposes local `refs/heads/promoted/<candidate-id>` plus SQLite metadata only,
with no direct writes to `main` or production.
