---
id: RISK-001
type: risk
title: Model self-approval
status: proposed
summary: A model that proposes a candidate could bias evaluation if it can also approve, score or promote that candidate.
owners:
  - architect
relates_to:
  - DOM-001
  - SYS-001
evidence:
  - user prompt 2026-07-25
  - PROJECT_PROFILE.toon
review_after: 2026-10-26
---

# Model Self-approval

The main safety risk in the first architecture is allowing a model-proposed
change to authorize itself. Deterministic validators, checks, benchmark evidence
and promotion policy must stay outside model authority.
