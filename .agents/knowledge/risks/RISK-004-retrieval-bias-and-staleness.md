---
id: RISK-004
type: risk
title: Retrieval staleness and self-reinforcing outcome bias
status: experimental
summary: Cached or historical evidence can refer to the wrong repository revision or repeatedly amplify prior winners, contaminating retrieval experiments and narrowing search.
owners:
  - architect
relates_to:
  - SYS-001
  - ARCH-002
decisions:
  - ADR-0004
risks:
  - RISK-001
evidence:
  - specs/changes/CHG-005-local-hybrid-graphrag/design.md
review_after: 2026-09-30
---

# Retrieval Staleness and Outcome Bias

Embedding keys include model id plus content hash. Capsule keys include logical
subject, subject revision and projection version. Any complete-result cache must
also include repository revision, mode and graph/ranking/projection versions.
Configured retrieval fails before evidence selection unless the query revision,
current working-tree fingerprint and Neo4j projection revision match exactly;
operators must run `saaa-index update` after source changes. `NONE` remains
independent of this projection check.

Historical outcome evidence is advisory, separately identified, capped and
visible in diagnostics. Promoted evidence is never an instruction and every new
candidate is evaluated by the unchanged deterministic fitness path.

The durable Git envelope archive and the active graph are deliberately
different. `lineage-novelty-v1` selects a bounded hot set by champions, known
ancestry, distinct failure fingerprints, novelty and a deterministic exploration
reservoir. Recency is only a tie-breaker. Every policy ID, slot override,
subject/process revision and retrieval configuration is recorded. All strategies
remain documentable in the envelope/wiki archive without gaining permanent
ranking influence. Policy changes require a new ID and ablation.
