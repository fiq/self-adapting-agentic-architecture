---
id: PAT-002
type: pattern
title: Bounded model-session efficiency
status: proposed
summary: Reuse one provider session only within a stable objective, role and permission scope; bound context and budgets, then reset for material changes or independent review.
owners:
  - project-lead
relates_to:
  - SYS-001
  - ARCH-001
  - Q-010
  - Q-011
  - PAT-003
risks:
  - RISK-001
evidence:
  - .agents/coordination/MODEL_ROUTING_POLICY.md
  - AGENTS.md
  - specs/changes/CHG-012-model-session-efficiency/change.toon
review_after: 2026-11-17
---

# Bounded model-session efficiency

Use a retained provider session for a single bounded objective, role and
permission scope so stable context can benefit from provider caching. Treat the
session as disposable execution state: repository sources, structured specs and
the handoff are the authority.

Start a fresh session for a changed objective, changed role or data scope,
independent review, transport failure or exhausted response reserve. Send a
source-referenced context packet rather than an opaque transcript. Record the
route, session or reset reason, budget and provider-reported usage where
available; do not invent cost data.

The pattern governs agent operations and does not introduce automatic SAAA
provider selection. Any such runtime behavior remains subject to Q-010's
measured-usage and ablation evidence, Q-011's constraints, and ARCH-001's
deterministic approval boundary.
