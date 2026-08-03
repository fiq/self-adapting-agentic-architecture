# Local Hybrid GraphRAG Design

## Reconciliation with the live loop

The repository has a richer `MutationContract`, but the shipped loop still uses
the transitional `Mutation`/whole-file realization path. CHG-005 does not create
a parallel mutation vocabulary. `RetrievalQuery` carries an optional
`MutationContract` plus a transitional human task and exact source identifiers.
`MutationProposalRequest` combines the baseline, query and exact
`RetrievalBundle`. When the live loop accepts `MutationContract`, the task-only
bridge can be removed without changing retrieval contracts.

```text
EvolveRunner chooses mode/config
        -> MutationEvaluationLoop asks EvidenceRetriever
        -> MutationProposalRequest(baseline, query, exact bundle)
        -> LangChain4j renders capsules and records provenance
        -> Mutation
        -> existing validation/worktree/check/scoring/decision path
```

Retrieval is upstream evidence. Nothing below candidate creation consumes a
retrieval score as fitness.

## Storage and lifecycle

The repository is canonical. Neo4j stores a rebuildable bounded working
projection with stable logical IDs plus repository revision/content hash. A full
`saaa-index build` replaces one repository-owned projection transactionally, which makes indexing
idempotent and removes stale facts. Evaluation outcome nodes have a distinct
projection source and are replaced from the durable ledger through a versioned
evolutionary-memory policy.

`.saaa/retrieval.sqlite` is a separate derived database. It owns:

- `embedding_cache(model_id, content_hash, dimensions, vector)`;
- `evidence_capsules` plus normalised source/link/outcome rows, keyed by logical
  subject, subject revision and projection version;
- `retrieval_attempts` and selected-evidence rows;

`.saaa/experiments.sqlite` remains the efficient local experiment ledger. A
separate `SqliteEvolutionaryMemoryStore` port/class/table family shares that
physical database without widening `ExperimentMetadataStore`. Compact
`experiments/ledger/*.toon` envelopes are Git-visible append-only rebuild input
for the evolutionary-memory tables and graph projection;
they contain observable deterministic facts, changed paths, subject/process
revisions and strategy/configuration IDs, but not prompts, raw model responses,
embeddings or private reasoning. `docs/wiki/experiments.md` is generated from all envelopes as
a human, explicitly non-authoritative projection.

## Evolution contexts and bounded memory

One Neo4j database can host multiple repository projections. Every projected
fact carries a repository identity and `SUBJECT`, `PROCESS` or combined role;
replacement is scoped to that identity. An evaluated candidate creates an
`EvolutionContext` that `EVOLVES` the subject repository/revision,
`EXECUTES_WITH` the SAAA process repository/revision and `USES_CONFIG` for the
retrieval strategy. A subject ledger therefore stays portable while analysis
can relate implementation outcomes to the agentic workflow that produced them.
Vector indexes also use deterministic repository-specific labels and names;
filtering only after Neo4j global top-K selection would otherwise allow another
repository to starve the requested partition.

`lineage-novelty-v1` selects the hot outcome graph by evolutionary value, not
age: bounded champions, their known ancestors, distinct failure fingerprints,
evidence-novel representatives and a deterministic exploration reservoir.
Individuals may override the slot bounds and policy ID through configuration;
changing semantics requires a new ID. Retention never changes fitness or gives
retained strategies uncapped rank. `saaa-reinflate --revision <commit>` uses a
JGit-materialised read-only snapshot and outcomes compatible with that baseline.
Native Git is a visible compatibility fallback if JGit cannot read the local
repository shape.

## Initial graph questions and schema

Only facts answering a retrieval question ship:

| Nodes/edge | Retrieval question |
|---|---|
| Module `CONTAINS` SourceFile | Which nearby implementation surface owns this target? |
| SourceFile `DECLARES` Type/Test | Which stable symbol or test does an exact path identify? |
| Type `DEPENDS_ON` Type | Which directly used types constrain this locus? |
| Test `TESTS` Type | Which structurally connected test may be semantically distant? |
| Capability/Change/ADR/Knowledge `RELATES_TO` evidence | Which repository constraint or decision is explicitly linked? |
| Candidate `REALIZES` Mutation and `RETRIEVED` Evidence | What evidence accompanied a realized prior attempt? |
| Candidate `SCORED` Evaluation; Evaluation `RAN` Check/Benchmark; Candidate `FAILED` Check; Evaluation `DECISION` Decision | Which observable failures, measurements and outcomes recur here? |

Method call graphs, inferred architecture edges and broad chunking are deferred.

## Retrieval

Exact repository IDs, paths and symbols are resolved first. Vector results are
ranked independently. Exact and vector seeds feed graph expansion over an
explicit relationship allow-list with depth and fan-out bounds. Reciprocal-rank
fusion combines rank positions, not incomparable raw score scales. Stable IDs
deduplicate results. Evidence-count and estimated-token budgets compile the final
capsules. No LLM reranker is present.

Historical evidence has a small decision-neutral bonus based on bounded outcome
count, capped by configuration. Promoted and discarded outcomes receive no
different automatic weight. Diagnostics expose the cap and selection reasons
show the observed count and applied bonus.

## Failure semantics

`NONE` constructs no graph/embedding adapter. Configured `GRAPH` or `HYBRID`
fails clearly if Neo4j is unavailable. `VECTOR` and `HYBRID` also fail if the
embedding model/index is unavailable. Silent fallback would label one ablation
treatment as another and contaminate results. Before retrieval, non-`NONE`
modes require an exact match between the request revision, current working-tree
fingerprint and Neo4j projection revision. Missing or stale projections fail
with the explicit `saaa-index update` remediation before any model invocation.

## Dependency decisions

The official Neo4j Java driver is required to use Neo4j as an explicit graph and
vector boundary. It is confined to `adapters/neo4j`, tested through fakes at unit
level and a small on-demand Compose integration test, and can be removed with
that adapter if ablation does not justify Neo4j. Existing LangChain4j OpenAI
support already provides an embedding model; it remains behind a provider-neutral
port and adds no second model SDK.

JGit is needed for repository identity, dirty revision fingerprints and safe
read-only historic materialisation without requiring subprocess setup. It is
confined to `adapters/git`, integration-tested against real repositories and
removable with that adapter. Version `7.6.0.202603022253-r` is pinned: it is the
March 2026 Eclipse release available from Maven Central, is newer than the fixed
versions for CVE-2025-4949 and CVE-2023-4759, and resolves only JavaEWAH, SLF4J
and Commons Codec transitively. Native Git remains a visible fallback and the
established linked-candidate-worktree implementation where no comparable JGit
creation API exists.

The CLI adds the tiny version-aligned `slf4j-nop` runtime provider so JGit and
the Neo4j driver have an explicit quiet logging policy instead of emitting
missing-provider warnings on every first run. It owns no domain behavior and can
be removed if a user-facing logging adapter is later selected.

The adapters constrain the already-transitive Jackson 3 databind component to
`3.1.5`. Flyway/MCP otherwise resolved `3.1.4`, which the installed-distribution
scan identified under GHSA-5gvw-p9qm-jgwh. The constraint introduces no new API
surface, is verified by dependency insight plus the distribution scan, and can
be removed when all upstream dependency BOMs select an equal or newer fixed
version.

The local database uses the official Neo4j `5.26.28-community-ubi10`
multi-architecture manifest pinned by digest. Compared with the current Debian
image, the recorded Trivy scan removes all high/critical operating-system
findings. Because no released Neo4j image yet includes fixed versions for ten
reported high Java findings, HTTP/HTTPS is disabled, authenticated Bolt is
published only on loopback and `no-new-privileges` is set. RISK-005 requires a
vendor-image upgrade and graph integration rerun when a patched release exists;
individual database jars are not replaced out of support.

## Ablation

The harness runs identical task definitions under all four modes and records
attempt/acceptance, fitness, gate/regression, change, token/cost/duration,
retrieval graph/evidence/cache and stable configuration metrics. Reports include
accepted fitness improvement per total mutation cost, acceptance per attempt and
context tokens per accepted candidate. A report with no runs makes no improvement
claim.

## Lead challenge after implementation

1. Graph traversal adds explicit test, dependency, governance and prior-outcome
   connections that plain lexical repository search does not expose as one
   bounded result.
2. Neo4j is used for allow-listed relationships, bounded neighbourhoods and
   repository-partitioned vector indexes, not only key lookup.
3. Vector search discovers semantic seeds; graph search contributes connected
   but semantically distant evidence. The acceptance fixture proves this
   distinction, while real benefit remains unmeasured.
4. Capsules materially reduce graph paths to compact subject/purpose/link/
   constraint/history/source context with a token estimate; the live GRAPH
   probe selected eight capsules after considering twelve nodes.
5. SQLite could implement exact/vector subsets, but would make relationship
   traversal recursive relational machinery. Whether that distinction earns
   Neo4j's footprint is intentionally left to ablation.
6. Neo4j and retrieval SQLite are disposable; Git sources, specs, knowledge and
   compact outcome envelopes rebuild them. Existing candidate metadata has its
   separate local lifecycle and is not overstated as fully archived.
7. No model or historical outcome decides selection: the existing deterministic
   validation, hard gates, fitness and promotion path remains authoritative.
8. `NONE` constructs no Neo4j or embedding adapter and preserves prior behavior.
9. Repository/query/graph revisions plus graph, projection, ranking, embedding,
   memory-policy and retrieval-configuration IDs make treatments reproducible;
   stale revision combinations fail closed.
10. Every capsule retains stable identity, selection reasons, authority/status,
    relationship links and exact sources; proposer/candidate provenance retains
    the selected bundle/configuration.
11. Historical contribution is decision-neutral, outcome-count-bounded and
    capped once after fusion. Retention keeps novelty/failure/exploration slots,
    so promoted lineages cannot consume the graph forever.
12. LangChain4j types remain confined to adapter packages; provider-neutral
    domain/deterministic ports own retrieval orchestration.
13. The footprint is one explicit local container, one named volume and two
    separated SQLite responsibilities, with no daemon/platform/background
    service. Archive selection and retained-memory replay still need scale
    profiling before optimisation.
14. The ablation harness applies identical corpus rows to NONE/VECTOR/GRAPH/
    HYBRID and reports deterministic outcomes plus model/retrieval cost, so it
    can falsify rather than merely demonstrate the hypothesis.
