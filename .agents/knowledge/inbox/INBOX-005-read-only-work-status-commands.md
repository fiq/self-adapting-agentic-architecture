---
id: INBOX-005
type: inbox
title: Read-only work status commands
status: proposed
summary: Template users benefit from read-only `project backlog` and `project worktree-status` commands that expose handoff and worktree state without mutating branches or files.
proposal_type: pattern
relates_to:
  - INBOX-004
evidence:
  - .agentic-template/bin/backlog
  - .agentic-template/bin/worktree-status
  - .agents/skills/coordination/backlog-status/SKILL.md
  - .agents/skills/coordination/worktree-status/SKILL.md
created_during: import vrunnable workflow status patterns
recommended_action: promote to PAT if these commands reduce coordination drift in generated projects
expires_after: 2026-10-26
---

# Read-Only Work Status Commands

## Proposal

Expose current work and worktree state through read-only repository commands:

- `project backlog` reads `HANDOFF.toon` and claimed `.worktrees/`.
- `project worktree-status` reads `git worktree` state and current handoff
  branch.

## Rationale

This imports the useful part of `vrunnable`'s `feature-backlog` and
`worktree-status` skills while avoiding merge automation or destructive cleanup
in the generic template.
