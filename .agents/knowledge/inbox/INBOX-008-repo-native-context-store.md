---
id: INBOX-008
type: inbox
title: Repo-native context store
status: proposed
summary: Generated projects should preserve AI-relevant context as versioned repository artifacts and checks rather than adding an external memory store by default.
proposal_type: pattern
relates_to:
  - INBOX-002
  - INBOX-003
  - INBOX-004
  - INBOX-007
evidence:
  - https://www.infoq.com/articles/ai-speed-context-store-architecture/
  - docs/context-store.md
  - docs/validation.md
  - PROJECT_PROFILE.toon
  - .agentic-template/bin/check-repo-contract
created_during: apply context-store architecture takeaways
recommended_action: promote to PAT if generated projects benefit from explicit context-store layers and fitness-function handoffs
expires_after: 2026-10-26
---

# Repo-native Context Store

## Proposal

Treat the repository as the default context store for agents and humans. The
store consists of versioned files and deterministic checks:

- structure: operating contract, architecture notes and command surface;
- lineage: profile decisions, handoff state, ADRs and knowledge entries;
- behavior: specs, acceptance scenarios and tests;
- conformance: repo checks, CI and architecture fitness functions.

Generated projects should not add an external vector store, database or SaaS
memory layer by default. They should add one only when project evidence
justifies it and the decision is recorded in `PROJECT_PROFILE.toon`.

## Evidence

- The InfoQ article argues that AI-assisted delivery makes durable context the
  bottleneck, and recommends repo-versioned specs, tests, fitness functions and
  handoff metadata.
- This template already has most of the repository-native pieces: structured
  specs, `PROJECT_PROFILE.toon`, `HANDOFF.toon`, `.agents/knowledge/`, wiki
  docs and deterministic project checks.

## Curator Notes

- This complements `INBOX-003`; context packets are transport for bounded
  delegation, while the context store is the durable source of truth.
- Promote only after generated projects show the extra guidance improves
  startup, review or handoff quality without creating excessive ceremony.
