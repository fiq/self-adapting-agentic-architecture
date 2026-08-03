# ADR-0004: Local hybrid GraphRAG experiment

## Status

Accepted for bounded implementation by explicit owner direction on 2026-08-02.

## Context

SAAA deliberately deferred external/vector retrieval and containers while the
first local mutation loop had no semantic-search evidence or corpus large enough
to justify them. That decision is preserved in `PROJECT_PROFILE.toon`,
`CHG-001`, `CHG-002`, `docs/context-store.md` and the original README.

The recorded revisit condition has now been reached. The owner wants to measure
whether bounded structural, semantic and prior deterministic-evaluation evidence
improves mutation grounding, constraint preservation, convergence and accepted
fitness improvement per model/token/cost budget.

Repository-native artifacts remain canonical. An external projection is useful
only if it adds relationship traversal and semantic discovery while remaining
rebuildable, inspectable and optional for ordinary runs.

## Decision

Run an explicitly versioned local retrieval experiment:

- Neo4j Community is an on-demand local Compose service and a rebuildable,
  bounded working projection of repository facts and policy-selected observable
  evaluation outcomes. It is not the historical archive.
- Graph nodes preserve logical identity separately from revision/content hashes.
- Only relationships that answer a retrieval question are projected initially:
  containment/declaration, dependency, test coverage, governance/association and
  observable mutation-evaluation outcome links.
- Semantic discovery uses a provider-neutral embedding port and Neo4j vector
  indexes partitioned by repository identity, so global top-K search cannot let
  one repository starve another; LangChain4j embedding types remain inside
  `adapters/langchain4j`.
- `.saaa/retrieval.sqlite` is separate, derived persistence for model-id/content-hash
  embedding memoisation, versioned evidence capsules and retrieval provenance.
  It may be deleted independently.
- `.saaa/experiments.sqlite` is the current query-efficient experiment ledger.
  A separate `EvolutionaryMemoryStore` port/class/tables share that physical
  database with experiment metadata without widening `ExperimentMetadataStore`.
- Compact append-only `experiments/ledger/*.toon` envelopes are the Git-visible
  rebuild source for observable experiment memory. They record subject and SAAA
  process revisions, mutation strategy, retrieval and memory-policy versions,
  deterministic evidence and outcome metrics; they exclude prompts, raw model
  responses, embeddings and private reasoning.
- `docs/wiki/experiments.md` is generated from those envelopes as a human
  projection. It is explicitly non-authoritative: documenting a strategy neither
  promotes it nor increases its retrieval rank.
- Retrieval treatment is explicit before model invocation: `NONE`, `VECTOR`,
  `GRAPH` or `HYBRID`. The first hybrid ranker uses deterministic reciprocal-rank
  fusion, explicit relationship/depth/fan-out allow-lists, stable deduplication
  and evidence/context budgets. No LLM reranker is used.
- A deterministic context compiler flattens graph evidence into bounded Evidence
  Capsules. Raw graph paths are diagnostics, not model context.
- The transitional live `Mutation` path receives a `MutationProposalRequest`
  containing a retrieval query and bundle. The query references an optional
  `MutationContract`; it does not introduce a second mutation-contract model.
  This bridge retires as the live loop converges on `MutationContract`.
- Observable evaluation outcomes may be projected as bounded historical evidence.
  Historical weighting is visible, configurable and capped. A prior winner never
  bypasses re-evaluation.
- Outcome retention in the hot graph follows a versioned, overrideable
  evolutionary policy rather than age. `lineage-novelty-v1` preserves bounded
  champions and known ancestors, distinct failure fingerprints, behavioural
  novelty representatives and a deterministic exploration reservoir. Recency is
  only a tie-breaker.
- Historic projection is explicit: `saaa-reinflate` uses a JGit read-only
  snapshot for a requested Git commit (with a visible native-Git worktree
  fallback), projects that source revision, and selects only compatible archived
  outcomes under the recorded memory policy.
- One Neo4j database may contain many repository projections, partitioned by
  repository identity and `SUBJECT`/`PROCESS` role. Each evaluation creates an
  explicit context that `EVOLVES` the subject repository, `EXECUTES_WITH` the
  SAAA process repository/revision and `USES_CONFIG` for retrieval. This enables
  cross-project analysis without mixing replacement lifecycles.
- Configured `GRAPH`, `VECTOR` or `HYBRID` retrieval fails the run when its
  required boundary is unavailable. `NONE` remains dependency-free and preserves
  existing candidate semantics.

The authority invariant remains:

```text
retrieval biases search
fitness determines survival
```

## Consequences

Neo4j adds local operational and dependency cost. It earns that cost only if
graph traversal contributes evidence that semantic retrieval or repository
search misses. The ablation harness must therefore compare the same corpus under
all four modes and permit the hypothesis to fail.

Neo4j data can be deleted and rebuilt from Git repository facts plus the durable
experiment envelopes without loss of canonical project knowledge or recorded
outcomes. Retrieval SQLite is disposable. The evolutionary-memory tables in
experiment SQLite can be rehydrated from the Git envelopes; the pre-existing
candidate metadata tables retain their existing local lifecycle. Large model
audit payloads remain in candidate provenance and are intentionally not copied
into the experiment envelopes or wiki.

No application image, Kubernetes topology, production database or always-on
daemon is introduced. Compose starts and stops one local dependency explicitly.
The existing Nix flake supplies the Java/Gradle and Docker client/Compose tools;
it connects to the host Docker daemon. A second devenv orchestration layer is not
introduced because it would duplicate the flake without adding a distinct
lifecycle or reproducibility boundary.

The installed executable is named `saaa`; its externally visible command tokens
use the `saaa-` prefix (`saaa-index`, `saaa-retrieve`, `saaa-evolve`,
`saaa-ablate`, `saaa-reinflate`, `saaa-mcp`) so generic words do not collide in
shell, tool or log namespaces.

Read-only repository identity, dirty revision fingerprints and historic source
materialisation use pinned JGit `7.6.0.202603022253-r` as the zero-setup primary
API. The pinned release is newer than the fixed ranges for CVE-2025-4949 and
CVE-2023-4759 and adds only JavaEWAH, SLF4J and Commons Codec to the resolved
adapter subtree. Native Git is a visible fallback and remains the existing
linked candidate-worktree implementation because there is no comparable JGit
worktree-creation API.

Neo4j is pinned to the official Community UBI10 multi-architecture manifest
`5.26.28-community-ubi10@sha256:56cdf7d...cdc5b`. A 2026-08-02 Trivy 0.72 scan
reported no high/critical UBI packages. Ten high Java findings have published
upstream fixes but no released Neo4j image yet includes them. This local
experiment therefore disables HTTP/HTTPS, publishes authenticated Bolt only on
loopback and sets `no-new-privileges`. RISK-005 records the residual risk; a
patched vendor image must replace this pin when available.

## Revisit triggers

- `GRAPH` contributes no distinct useful evidence in ablation: remove Neo4j after
  owner review and retain the recorded result.
- Evidence capsules do not materially reduce context size/noise: simplify or
  remove the projection layer before adding retrieval complexity.
- Historical evidence dominates selection or narrows exploration: reduce/disable
  its cap and repeat the corpus.
- The default lineage/novelty policy loses a materially useful strategy: pin it
  in a reviewed policy override, add a new versioned policy and re-inflate; do
  not silently change `lineage-novelty-v1`.
- Retrieval cost exceeds accepted fitness improvement: keep `NONE` as the default
  and stop expanding the projection.
- The graph schema needs broad AST/LSP extraction or a background platform to
  remain useful: stop for a separate decision rather than widening this ADR.

## Evidence

- `PROJECT_PROFILE.toon` recorded semantic-search need as a revisit trigger.
- `specs/changes/CHG-005-local-hybrid-graphrag/`
- `.agents/knowledge/decisions/ADR-0004-local-hybrid-graphrag-experiment.md`
- `.agents/knowledge/architecture/ARCH-002-retrieval-evidence-boundary.md`
- `.agents/knowledge/risks/RISK-004-retrieval-bias-and-staleness.md`
