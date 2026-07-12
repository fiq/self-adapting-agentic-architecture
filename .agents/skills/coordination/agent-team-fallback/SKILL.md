---
name: agent-team-fallback
description: Define graceful degradation when persistent agent teams, subagents or preferred models are unavailable.
---

# Agent Team Fallback

## Outcome

Preserve acceptance and review gates even when the preferred agent topology
is unavailable. Never pretend a single agent constitutes an independent team.

## Fallback order

1. Persistent agent team with bounded role ownership.
2. Independent subagents.
3. Sequential role passes.
4. Single lead agent using an explicit review checklist.

## Required roles

Roles are selected by risk. Do not activate every role automatically. Roles
may include:

- product/domain;
- architecture;
- implementation;
- testing;
- security;
- operations;
- UX/accessibility;
- independent reviewer.

## When degrading

Record the following in `HANDOFF.toon`:

- why the preferred topology was unavailable;
- which fallback level was used;
- which independent review or challenge was lost;
- what additional human review is needed to compensate.

Always:

- preserve the same acceptance and review gates;
- keep tests and validation evidence;
- record branch, worktree and commit state;
- identify safe bounded next work.

Never:

- mark a single-agent pass as equivalent to independent review;
- skip acceptance evidence because the team was smaller;
- hide the degradation from downstream readers.
