---
id: INBOX-002
type: inbox
title: Enforce knowledge graph upkeep in handoff validation
status: proposed
summary: Meaningful work should update HANDOFF.toon with graph evidence, proposals or an explicit no-record reason so knowledge upkeep does not silently drift.
proposal_type: pattern
relates_to:
  - INBOX-001
evidence:
  - user report that prior use did not keep the knowledge graph updated
  - AGENTS.md knowledge graph rules
  - .agentic-template/bin/check-handoff
created_during: add structured data format policy
recommended_action: promote to PAT after the handoff gate proves useful in real project work
expires_after: 2026-10-25
---

# Enforce Knowledge Graph Upkeep In Handoff Validation

## Proposal

`HANDOFF.toon` should always include a compact `knowledge` section after
meaningful work:

- `consulted`: graph entries or paths used before planning or implementation
- `proposals`: new or updated knowledge entries
- `no_record`: reason when no durable graph update was warranted

## Rationale

This turns knowledge upkeep from an advisory workflow habit into a validated
handoff checkpoint without forcing every small edit into canonical knowledge.
