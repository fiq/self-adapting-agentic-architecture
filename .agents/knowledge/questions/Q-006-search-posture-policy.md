---
id: Q-006
type: question
title: Search posture operator policy
status: open
summary: The loop needs explicit semantics for local hill climbing and bounded exploratory leaps so exploitation and moonshot variants are deliberate.
owners:
  - project-lead
relates_to:
  - SYS-001
  - CON-001
risks:
  - RISK-001
evidence:
  - docs/architecture/evolutionary-operators.md
  - specs/changes/CHG-002-live-loop-policy/design.md
review_after: 2026-10-26
---

# Search Posture Operator Policy

The working assumption is to add two search posture operators:

- `hill-climb`: local, fitness-aware exploitation near an evaluated parent.
- `exploratory-leap`: bounded moonshot exploration with explicit risk budget.

Both operators remain fitness-function aware. The model may propose or realize
the variant, but deterministic evidence still selects or discards it.
