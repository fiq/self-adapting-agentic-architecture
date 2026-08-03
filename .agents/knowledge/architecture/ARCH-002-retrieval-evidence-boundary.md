---
id: ARCH-002
type: architecture
title: Retrieval evidence boundary
status: experimental
summary: Explicit retrieval orchestration compiles bounded graph, semantic and historical evidence before model invocation; retrieval cannot score or approve a candidate.
owners:
  - architect
relates_to:
  - ARCH-001
  - SYS-001
decisions:
  - ADR-0004
risks:
  - RISK-004
evidence:
  - docs/decisions/0004-local-hybrid-graphrag-experiment.md
  - specs/changes/CHG-005-local-hybrid-graphrag/design.md
review_after: 2026-10-31
---

# Retrieval Evidence Boundary

The experiment layer selects a retrieval treatment before model invocation.
Deterministic retrieval resolves exact seeds, vector ranks, bounded graph
neighbours, rank fusion, deduplication and budgets. A context compiler emits
Evidence Capsules with source, authority, relationship and selection reasons.

LangChain4j renders the resulting bundle and records prompt/response provenance.
It does not choose the mode. Retrieval output never enters deterministic fitness
as an approval, score or promotion instruction.

Neo4j is a bounded working graph, not the archive. Git-visible experiment
envelopes rebuild the efficient SQLite ledger and generated wiki projection;
versioned evolutionary policies inflate only a useful active set. One database
may hold repository-partitioned `SUBJECT` and `PROCESS` evidence. Each evaluation
context explicitly relates the implementation revision, SAAA process revision
and retrieval configuration so process architecture can evolve without being
confused with the code it is evaluating.
