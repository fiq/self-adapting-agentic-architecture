---
id: Q-004
type: question
title: Initial fitness objectives and weights
status: open
summary: The first deterministic scorer needs explicit phenotype-based hard gates, objective weights and benchmark budget semantics.
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

# Initial Fitness Objectives and Weights

The first scorer should make behavioral correctness a hard gate and use
benchmarks as weighted evidence rather than letting performance dominate task
success. The current proposal scores phenotype evidence: task success,
reliability, cost and latency budgets, behavioral safety and parsimony.
