---
id: Q-010
type: question
title: Resource-aware model routing policy
status: open
summary: ADR-0006 fixes routing as a deterministic harness-control-plane concern; this question now tracks the evidence and calibration needed to make resource-aware selection cheaper without quality regression.
owners:
  - project-lead
relates_to:
  - SYS-001
  - CON-001
  - Q-006
  - ADR-0006
risks:
  - RISK-001
evidence:
  - .agents/skills/tooling/model-routing/SKILL.md
  - docs/structured-data.md
  - docs/architecture/evolutionary-operators.md
review_after: 2027-01-04
---

# Resource-aware model routing policy

ADR-0006 settles the architectural direction: routing belongs in the SAAA
control plane, is decided before invocation, and cannot weaken deterministic
approval. This question remains open for the empirical part of the problem:
which task characteristics discriminate, which usage/price evidence is reliable,
and whether cheap-first profile selection improves cost per successful task
without quality regression.

The application currently selects one explicit proposer profile and does not
route between model tiers. Future routing should treat remaining tokens,
provider credits, wall-clock budget, rate limits and retry allowance as
strategy state for the current experiment.

The policy should be deterministic and recorded before a candidate is proposed.
It may choose among an inexpensive model, a stronger model, a bounded context
packet, a retry, or an escalation. It must not change hard gates, fitness
authority, promotion thresholds, audit retention, or the requirement that the
model cannot approve its own result.

Required evidence before implementation:

- a declared resource budget and provider/model price identity;
- measured token and credit usage for each proposer attempt;
- a routing decision record containing the available budget, selected tier,
  reason and remaining budget;
- deterministic fallback behavior when usage is unavailable or a provider
  rejects a request;
- ablation evidence that routing improves experiment throughput or quality
  without hiding failed candidates or starving exploratory operators.

The first implementation should be a bounded routing policy over explicit
model tiers, not an opaque model-name heuristic. It should be evaluated as a
`model-routing-change` operator and remain separate from deterministic
candidate scoring.
