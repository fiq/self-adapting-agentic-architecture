---
id: ADR-0004
type: decision
title: Local hybrid GraphRAG experiment
status: canonical
summary: SAAA intentionally revisits its previous retrieval deferral with an optional, rebuildable local Neo4j projection, versioned evidence capsules and explicit ablation treatments while deterministic fitness remains authoritative.
owners:
  - architect
relates_to:
  - ADR-0002
  - ARCH-001
  - ARCH-002
  - SYS-001
risks:
  - RISK-001
  - RISK-004
  - RISK-005
evidence:
  - docs/decisions/0004-local-hybrid-graphrag-experiment.md
  - specs/changes/CHG-005-local-hybrid-graphrag/
  - PROJECT_PROFILE.toon
reviewed_at: 2026-08-02
review_after: 2026-10-31
---

# Local Hybrid GraphRAG Experiment

The previous external/vector retrieval deferral was correct for the first loop.
Its semantic-search revisit trigger has now been reached by explicit owner
direction. ADR-0004 authorises a bounded local experiment, not a production
infrastructure commitment.

Repository artifacts and compact Git experiment envelopes are durable; Neo4j
and retrieval SQLite are derived and rebuildable. Experiment SQLite is the
current efficient ledger; its evolutionary-memory tables can be rehydrated from
the envelopes. A generated
wiki page preserves a human view without becoming authority or ranking weight.

Neo4j is a bounded working graph selected by the versioned
`lineage-novelty-v1` evolutionary-memory policy, not a complete historical
archive. Explicit historic reinflation uses a Git revision plus compatible
envelopes. One graph may relate a `SUBJECT` implementation repository to the
versioned SAAA `PROCESS` that evolved it; replacement remains repository-scoped.

The executable and public command tokens use the `saaa`/`saaa-` namespace to
avoid generic tool names such as `index`.

The existing Nix flake remains the sole development-environment contract and
supplies Java/Gradle plus Docker client/Compose tooling for this experiment. It
uses the host Docker daemon; devenv is intentionally not added as a duplicate
orchestration layer.

The official Neo4j 5.26.28 Community UBI10 manifest is pinned by digest. Its
base has no high/critical findings in the recorded Trivy scan; residual Java
findings are contained by disabling HTTP/HTTPS and publishing authenticated
Bolt only on loopback, and remain tracked by RISK-005 until an upstream patched
image is available.
