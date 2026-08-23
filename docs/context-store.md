# Context Store

A context store is the material an agent or a new human reads to recover
context: what the project is, why it is shaped that way, how it should behave
and whether it still conforms. This template makes the repository itself that
store — a versioned set of files and checks.

Default rule: "Do not add an external vector store, database or SaaS memory
layer by default."

Current exception (ADR-0004):

- The earlier default remains sound, but its recorded revisit condition has
  now been reached for the bounded experiment in ADR-0004.
- SAAA therefore has one deliberate exception: an optional local Neo4j
  Community projection plus a separate derived retrieval SQLite database.
- This is an experiment, not a new canonical context store or production
  infrastructure commitment.

## Layers

The store has four layers. Each layer answers one question and lives in
specific repository sources:

| Layer | Repository sources | What it answers |
|---|---|---|
| Structure | `AGENTS.md`, `README.md`, `PROJECT_PROFILE.toon.architecture`, `docs/wiki/architecture.md` | What exists, where boundaries are, and how commands are shaped |
| Lineage | `PROJECT_PROFILE.toon.decisions`, `PROJECT_PROFILE.toon.rejected_options`, `HANDOFF.toon`, `docs/decisions/`, `.agents/knowledge/` | Why choices were made, what changed, and what remains unresolved |
| Behavior | `specs/capabilities/`, `specs/changes/`, tests selected from `docs/validation.md` | What the system promises and how those promises are verified |
| Conformance | `.agentic-template/bin/project check`, `project ready`, CI, specialised architecture fitness functions | Whether current code still respects important constraints |

## Retrieval Projection

"Retrieval projection" is the ADR-0004 exception above: a set of derived
stores that retrieval treatments read. The repository remains canonical; the
stores in this section are derived from it.

The stores at a glance:

```
CANONICAL

  the repository
    └─ experiments/ledger/*.toon —— compact, append-only, Git-visible
                                    outcome envelopes

DERIVED

  .saaa/experiments.sqlite —— the efficient local experiment ledger; the
                              envelopes are the Git-visible rebuild source
                              for observable outcomes
  Neo4j projection         —— the identities, relationships and outcomes
                              GRAPH and HYBRID treatments traverse; rebuilt
                              by `saaa saaa-index build` from repository
                              facts plus a policy-selected working set
                              replayed from the experiment ledger
  .saaa/retrieval.sqlite   —— a separate derived cache (embeddings,
                              capsules, retrieval provenance); may be
                              removed independently

GENERATED FOR HUMANS (not authority, no ranking weight)

  docs/wiki/experiments.md —— generated from every ledger envelope
```

### Neo4j projection

- Neo4j projects stable repository identities, explicit relationships and
  observable deterministic evaluation outcomes so GRAPH and HYBRID treatments
  can traverse them.
- Deleting the Neo4j volume loses no canonical project knowledge or
  experiment memory: `saaa saaa-index build` reconstructs repository facts
  and replays a policy-selected working set from the experiment ledger.
- Neo4j deliberately does not retain every historical path. The versioned
  `lineage-novelty-v1` policy inflates a bounded working set using champions,
  known ancestors, failure fingerprints, novelty niches and a deterministic
  exploration reservoir.
- Policy thresholds are overrideable; semantics require a new policy ID.
- `saaa-reinflate` explicitly reconstructs an historic Git revision and
  compatible outcomes without modifying the active checkout.
- The same Neo4j database may contain both the project being built
  (`SUBJECT`) and the SAAA agentic workflow (`PROCESS`). Repository-scoped
  identities prevent one projection rebuild from deleting another.
- Each evaluation context links both repository revisions and the retrieval
  configuration, allowing process changes to be compared against
  implementation outcomes.

### `.saaa/retrieval.sqlite`

A derived retrieval cache, intentionally separate from experiment metadata:

- it memoises embeddings by model ID plus content hash;
- it materialises capsules by logical subject plus revision plus projection
  version;
- it records retrieval provenance;
- cached capsules contain repository-static context; current bounded
  historical outcomes are attached after the cache read, so memory updates
  cannot return stale outcome context;
- it is derived and may be removed independently.

### `.saaa/experiments.sqlite` and the ledger envelopes

- `.saaa/experiments.sqlite` is the efficient local experiment ledger. Its
  metadata and evolutionary-memory concerns use separate
  ports/classes/tables.
- Compact append-only `experiments/ledger/*.toon` envelopes are the
  Git-visible rebuild source for observable outcomes and the
  evolutionary-memory graph input.
- The envelopes do not reconstruct every field in the pre-existing candidate
  metadata tables, and they exclude large/model-private audit payloads.
- `docs/wiki/experiments.md` is generated from every envelope for human
  navigation and prominently declares that it is not authority or ranking
  weight.

### Evidence Capsules

- The context compiler turns graph evidence into Evidence Capsules before
  model invocation.
- Capsules retain subject, revision, authority/status, sources, selection
  reasons and bounded historical outcomes while avoiding raw graph-path
  dumps.
- Retrieval treatment is explicit and advisory; deterministic fitness is
  unchanged and authoritative.

## Startup Query

The startup query is the fixed first-read sequence for recovering context.
For non-trivial work:

1. Run `.agentic-template/bin/project startup`.
2. Read `HANDOFF.toon`, `PROJECT_PROFILE.toon`, this file and
   `.agents/knowledge/index.md`.
3. Use `.agentic-template/bin/project docs` if the next artifact is unclear.
4. Read relevant specs and knowledge entries before planning or
   implementation.

## Change Handoff

A change handoff is what a non-trivial change leaves behind so the next human
or agent can continue without reconstructing intent from code alone:

- spec reference, or a clear no-spec rationale for trivial/mechanical work;
- tests added or changed, plus validation results;
- fitness-function delta, or a no-change rationale;
- decisions, unknowns, risks and rejected options updated where material;
- knowledge proposal, learning, question, risk or no-record rationale.

## Fitness Functions

An architecture fitness function is a cheap, deterministic check that
protects a project-specific architecture characteristic. Generated projects
should identify the top 1-3 architecture risks and encode checks when
practical.

Good candidates include:

- dependency direction and forbidden imports;
- public API, event or file schema drift;
- provider-specific code leaking across clean boundaries;
- migration drift for persistence-backed systems;
- performance, accessibility, security or deployability budgets;
- required container health checks and health-aware Compose dependencies.

Wiring and fallback:

- wire fitness functions into `.agentic-template/bin/project check` or
  `.agentic-template/bin/project ready`;
- if the best check is still manual, record the command or inspection path in
  `docs/validation.md` and the current result in `HANDOFF.toon.tests_run`.

## Further Reading

- InfoQ, "Comprehension at AI Speed: Building a Context Store for
  Evolutionary Architecture" (2026-07-14):
  https://www.infoq.com/articles/ai-speed-context-store-architecture/

The useful takeaway is the repo-native operating model:

- specs feed intent forward;
- tests and fitness functions feed conformance back;
- handoff and knowledge artifacts preserve lineage.

ADR-0004 adds a rebuildable query projection only to test whether that
canonical context can be compiled into better bounded model evidence.
