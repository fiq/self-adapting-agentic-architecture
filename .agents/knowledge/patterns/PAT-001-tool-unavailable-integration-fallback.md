---
id: PAT-001
type: pattern
title: Tool-unavailable integration fallback
status: proposed
summary: When PR tooling is unavailable, explicitly authorized branch self-review can substitute for PR integration if validation and handoff evidence are preserved.
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
- self-review the branch in code-review style;
- record the fallback reason, validation results, branch and commit state in
  `HANDOFF.toon`;
- merge to `main` only after explicit authorization;
- push without force-pushing.
