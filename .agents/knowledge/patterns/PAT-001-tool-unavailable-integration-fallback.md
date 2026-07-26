---
id: PAT-001
type: pattern
title: Tool-unavailable integration fallback
status: proposed
summary: When PR tooling is unavailable, explicitly authorized branch integration can proceed only with deterministic validation, risk-appropriate actor review when available, lead self-review and handoff evidence.
owners:
  - project-lead
relates_to:
  - SYS-001
evidence:
  - AGENTS.md
  - docs/wiki/development.md
  - HANDOFF.toon
review_after: 2026-10-27
---

# Tool-unavailable Integration Fallback

Default integration remains branch plus PR. If GitHub app or CLI tooling cannot
open a PR, a lead agent can integrate without a PR only when the user explicitly
authorizes it.

Required guardrails:

- keep work on a bounded branch;
- run deterministic repository checks and relevant specialized tests;
- request at least one risk-appropriate reviewer actor or subagent when agent
  tooling is available;
- if a reviewer actor times out while actor tooling is still available, retry
  with another actor or get explicit human review, then address any findings
  from that substituted review before merge;
- address actor or human review findings before merge, then self-review the
  branch in code-review style;
- if actor review tooling is unavailable, disclose the missing actor-review
  gate, get explicit user authorization for the degraded single-lead path, and
  record the degraded fallback level, lost independent challenge and
  compensating validation or human review needed;
- record the fallback reason, validation results, actor-review or substituted
  human-review result, branch and commit state in `HANDOFF.toon`;
- merge to `main` only after explicit authorization;
- push without force-pushing.
