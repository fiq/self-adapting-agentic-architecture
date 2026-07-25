---
id: INBOX-003
type: inbox
title: Context packets for context-window-aware delegation
status: proposed
summary: Delegated agent work should receive a bounded semantic packet sized to the target context window, with source refs before snippets and no opaque semantic transport blobs.
proposal_type: pattern
relates_to:
  - INBOX-002
evidence:
  - user request for template users to benefit from context-window-aware agent handoffs
  - .agents/skills/tooling/context-packet/SKILL.md
  - .agents/coordination/CONTEXT_POLICY.md
created_during: add context-window-aware delegation policy
recommended_action: promote to PAT after the skill proves useful across project specialisations
expires_after: 2026-10-26
---

# Context Packets For Context-Window-Aware Delegation

## Proposal

Use a bounded `context_packet` for delegation, review and model handoff. The
packet should lead with objective, requested output, acceptance, non-goals,
facts, decisions, risks and source references. Include exact snippets only when
the receiver needs exact wording or code shape.

## Rationale

This gives generated-project users a reusable way to respect context windows
without losing semantic fidelity or cluttering specialised project READMEs.
