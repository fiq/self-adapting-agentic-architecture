---
id: ADR-0006
type: decision
title: Budget-aware harness optimisation is a control-plane responsibility
status: proposed
summary: SAAA should optimise and select versioned harness profiles around existing agents, starting with deterministic cheap-first routing and evidence-backed escalation, while models remain unable to approve themselves, grant themselves budget or weaken deterministic gates.
owners:
  - architect
tags:
  - product-direction
  - harness
  - cost
  - routing
relates_to:
  - ARCH-001
  - SYS-001
  - Q-010
  - Q-011
  - Q-013
risks:
  - RISK-001
decisions:
  - ADR-0002
  - ADR-0003
supersedes: []
superseded_by: null
evidence:
  - docs/decisions/0006-budget-aware-harness-optimisation.md
  - .agents/knowledge/questions/Q-010-resource-aware-model-routing.md
  - PROJECT_PROFILE.toon
reviewed_at: null
review_after: 2027-01-04
---

# ADR-0006: Budget-aware harness optimisation

Details live in
`docs/decisions/0006-budget-aware-harness-optimisation.md`. This knowledge node
exists so specs, wiki pages and later routing changes can link the decision by
id.

## Summary

SAAA is the control plane around existing agents, not another general-purpose
agent runtime. The thing it should learn to select and improve is a versioned
harness profile: model route plus the bounded context, retrieval, tool,
workflow, verification and resource policy needed for a class of work.

The first policy is deliberately deterministic and cheap-first. It chooses from
explicit profiles before invocation, records the reason and available budget,
and escalates only after observable failure or verification evidence. The model
cannot grant itself a larger budget, choose its own escalation, alter hard gates
or approve its own result.

## Load-bearing rule

Optimisation may change **how work is attempted**. It may not change **who has
authority to decide whether the result is acceptable**. `ARCH-001` remains the
boundary.

## Delivery

The ADR names a prerequisite and six independently shippable slices. The
prerequisite, slice 0, is a live proposer filling a generation with its
`AgentUsage` recorded, because cost per successful task cannot be measured
without it. Then: profile contract, deterministic selection, progressive
escalation and cumulative usage, failure-directed adaptation, trace/eval corpus
ingestion, and cost/quality selection/export. Selection and escalation run over
an offline evaluation corpus; live per-task escalation needs its own decision.
Each slice receives a `CHG-*` id only when it is actually proposed.

`Q-010` stays open for the empirical routing question rather than the
architectural one.
