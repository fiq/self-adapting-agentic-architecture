---
name: knowledge-capture
description: Extract durable learnings and questions after meaningful work.
---

# Knowledge Capture

## Purpose

Separate active task state from durable knowledge proposals.

## Extract

- decisions made
- assumptions introduced
- assumptions disproved
- new domain relationships
- undocumented behaviours
- integration quirks
- failure modes
- reusable patterns
- contract changes
- operational risks
- unanswered questions
- contradictions with existing guidance

## Destination Decision

```text
active resume state       -> HANDOFF.toon
unanswered material issue -> knowledge/questions
evidence-backed discovery -> knowledge/learnings or inbox
durable decision          -> decision proposal
reusable practice         -> pattern proposal
failure mode              -> risk proposal
trivial implementation    -> no durable record
```

## Rules

- Do not write directly to canonical knowledge unless reviewed evidence already
  exists or the user explicitly directs promotion.
- Prefer `.agents/knowledge/inbox/` for unreviewed proposals.
- Link proposals to existing IDs when possible.
- Avoid recording trivial implementation details.
- Capture contradictions with existing guidance.
- Update `HANDOFF.toon.knowledge` on every meaningful task: record consulted
  IDs/paths, proposals created, or a concrete `no_record` reason.

## Output

```yaml
handoff_updates: []
knowledge_proposals:
  - path: .agents/knowledge/inbox/INBOX-001-example.md
    reason: possible reusable pattern
questions_created: []
no_record:
  - reason: local-only mechanical change
```
