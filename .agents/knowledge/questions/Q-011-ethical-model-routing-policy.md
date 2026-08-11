---
id: Q-011
type: question
title: Ethical and preference-aware model routing policy
status: open
summary: Model routing should be configurable against personal or organisational ethics and preferences, including local execution, provider selection, open weights, jurisdiction, data retention, energy and licensing.
owners:
  - project-lead
relates_to:
  - SYS-001
  - Q-010
  - ADR-0003
risks:
  - RISK-001
evidence:
  - specs/changes/CHG-006-agent-harness-boundary/change.toon
  - docs/wiki/architecture.md
review_after: 2026-11-11
---

# Ethical and preference-aware model routing policy

SAAA should eventually let an operator configure how model and harness choices
align with their ethics and operating preferences. Possible dimensions include
local versus remote execution, open-weight versus hosted models, provider trust,
jurisdiction, data retention, energy use, licensing and cost.

The policy needs two levels:

- hard constraints, such as never sending source to a provider or jurisdiction;
- soft preferences, which rank eligible routes and remain visible in the
  recorded decision.

The policy must be explicit, versioned and auditable. Missing or unverifiable
provider claims should fail closed for hard constraints rather than receive an
invented score. Ethical preferences must not silently weaken deterministic
validation, fitness, promotion or audit requirements.

Required evidence before implementation:

- a provider/harness capability and provenance vocabulary;
- a versioned preference profile with hard constraints and weighted preferences;
- route decisions recording the profile, facts used, exclusions and rationale;
- tests showing unavailable metadata cannot bypass a hard constraint;
- an ablation showing the policy's quality, cost and availability trade-offs.
