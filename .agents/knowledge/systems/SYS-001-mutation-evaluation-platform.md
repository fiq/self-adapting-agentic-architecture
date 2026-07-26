---
id: SYS-001
type: system
title: Mutation evaluation platform
status: proposed
summary: A local Java CLI coordinates model-proposed workflow mutations, Git worktree candidates, deterministic checks, benchmarks and promotion decisions.
owners:
  - project-lead
relates_to:
  - DOM-001
risks:
  - RISK-001
evidence:
  - README.md
  - docs/architecture/module-boundaries.md
  - specs/changes/CHG-001-mutation-fitness-loop/change.toon
review_after: 2026-10-26
---

# Mutation Evaluation Platform

The first system shape is local and sequential: one CLI invocation evaluates one
candidate at a time. Git, SQLite, checks, benchmarks and model access are
adapter responsibilities behind application ports.
