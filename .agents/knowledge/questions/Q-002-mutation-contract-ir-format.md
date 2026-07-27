---
id: Q-002
type: question
title: Behavior-first mutation contract and IR format
status: open
summary: The first behavior-first mutation contract and internal representation need an explicit deterministic contract.
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

# Behavior-first Mutation Contract and IR Format

The current thin slice keeps `Mutation.patch` as bounded text. `CHG-002`
corrects that vocabulary: the mutation is the behavioral variation of the
implementation or workflow genotype, while a Git diff is the realization used
to materialize a candidate. The proposal uses a TOON audit envelope plus a
canonical S-expression mutation IR for deterministic parsing, validation,
targeted mutation and later crossover.
