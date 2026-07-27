---
id: Q-005
type: question
title: Crossover operator policy
status: open
summary: The project needs an explicit first crossover policy that captures diversity of thought without unauditable raw diff recombination.
owners:
  - project-lead
relates_to:
  - SYS-001
risks:
  - RISK-001
evidence:
  - docs/architecture/evolutionary-operators.md
  - specs/changes/CHG-002-live-loop-policy/design.md
review_after: 2026-10-26
---

# Crossover Operator Policy

The working assumption is conceptual crossover: combine evidence-backed traits,
techniques, hypotheses or reviewer lessons from evaluated parents into a new
targeted mutation contract using the closed mutation operator enum. Raw
code-diff crossover remains deferred because it amplifies LLM nondeterminism
and is harder to audit.
