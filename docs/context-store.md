# Context Store

The template treats the repository itself as the context store: a versioned set
of files and checks that explain what the project is, why it is shaped that way,
how it should behave and whether it still conforms.

Do not add an external vector store, database or SaaS memory layer by default.
The earlier default remains sound, but its recorded revisit condition has now
been reached for the bounded experiment in ADR-0004. SAAA therefore has one
deliberate exception: an optional local Neo4j Community projection plus a
separate derived retrieval SQLite database. This is an experiment, not a new
canonical context store or production infrastructure commitment.

## Retrieval Projection

The repository remains canonical. Neo4j projects stable repository identities,
explicit relationships and observable deterministic evaluation outcomes so
GRAPH and HYBRID treatments can traverse them. Deleting the Neo4j volume loses
no canonical project knowledge or experiment memory; `saaa saaa-index build`
reconstructs repository facts and replays a policy-selected working set from the
experiment ledger.

`.saaa/retrieval.sqlite` is intentionally separate from experiment metadata. It
memoises embeddings by model ID plus content hash, materialises capsules by
logical subject plus revision plus projection version, and records retrieval
provenance. Cached capsules contain repository-static context; current bounded
historical outcomes are attached after the cache read so memory updates cannot
return stale outcome context. It is derived and may be removed independently.

`.saaa/experiments.sqlite` is the efficient local experiment ledger. Its
metadata and evolutionary-memory concerns use separate ports/classes/tables.
Compact append-only `experiments/ledger/*.toon` envelopes are the Git-visible
rebuild source for observable outcomes and the evolutionary-memory graph input;
they do not reconstruct every field in the pre-existing candidate metadata
tables. They exclude large/model-private audit
payloads. `docs/wiki/experiments.md` is generated from every envelope for human
navigation and prominently declares that it is not authority or ranking weight.

Neo4j deliberately does not retain every historical path. The versioned
`lineage-novelty-v1` policy inflates a bounded working set using champions,
known ancestors, failure fingerprints, novelty niches and a deterministic
exploration reservoir. Policy thresholds are overrideable; semantics require a
new policy ID. `saaa-reinflate` explicitly reconstructs an historic Git revision
and compatible outcomes without modifying the active checkout.

The same Neo4j database may contain both the project being built (`SUBJECT`) and
the SAAA agentic workflow (`PROCESS`). Repository-scoped identities prevent one
projection rebuild from deleting another. Each evaluation context links both
repository revisions and the retrieval configuration, allowing process changes
to be compared against implementation outcomes.

The context compiler turns graph evidence into Evidence Capsules before model
invocation. Capsules retain subject, revision, authority/status, sources,
selection reasons and bounded historical outcomes while avoiding raw graph-path
dumps. Retrieval treatment is explicit and advisory; deterministic fitness is
unchanged and authoritative.

## Layers

| Layer | Repository sources | What it answers |
|---|---|---|
| Structure | `AGENTS.md`, `README.md`, `PROJECT_PROFILE.toon.architecture`, `docs/wiki/architecture.md` | What exists, where boundaries are, and how commands are shaped |
| Lineage | `PROJECT_PROFILE.toon.decisions`, `PROJECT_PROFILE.toon.rejected_options`, `HANDOFF.toon`, `docs/decisions/`, `.agents/knowledge/` | Why choices were made, what changed, and what remains unresolved |
| Behavior | `specs/capabilities/`, `specs/changes/`, tests selected from `docs/validation.md` | What the system promises and how those promises are verified |
| Conformance | `.agentic-template/bin/project check`, `project ready`, CI, specialised architecture fitness functions | Whether current code still respects important constraints |

## Startup Query

For non-trivial work:

1. Run `.agentic-template/bin/project startup`.
2. Read `HANDOFF.toon`, `PROJECT_PROFILE.toon`, this file and
   `.agents/knowledge/index.md`.
3. Use `.agentic-template/bin/project docs` if the next artifact is unclear.
4. Read relevant specs and knowledge entries before planning or implementation.

## Change Handoff

Every non-trivial change should leave enough context for the next human or
agent to continue without reconstructing intent from code alone:

- spec reference, or a clear no-spec rationale for trivial/mechanical work;
- tests added or changed, plus validation results;
- fitness-function delta, or a no-change rationale;
- decisions, unknowns, risks and rejected options updated where material;
- knowledge proposal, learning, question, risk or no-record rationale.

## Fitness Functions

Architecture fitness functions are cheap, deterministic checks that protect
project-specific characteristics. Generated projects should identify the top
1-3 architecture risks and encode checks when practical.

Good candidates include:

- dependency direction and forbidden imports;
- public API, event or file schema drift;
- provider-specific code leaking across clean boundaries;
- migration drift for persistence-backed systems;
- performance, accessibility, security or deployability budgets;
- required container health checks and health-aware Compose dependencies.

Wire fitness functions into `.agentic-template/bin/project check` or
`.agentic-template/bin/project ready`. If the best check is still manual,
record the command or inspection path in `docs/validation.md` and the current
result in `HANDOFF.toon.tests_run`.

## Further Reading

- InfoQ, "Comprehension at AI Speed: Building a Context Store for Evolutionary
  Architecture" (2026-07-14): https://www.infoq.com/articles/ai-speed-context-store-architecture/

The useful takeaway is the repo-native operating model:
specs feed intent forward, tests and fitness functions feed conformance back,
and handoff/knowledge artifacts preserve lineage. ADR-0004 adds a rebuildable
query projection only to test whether that canonical context can be compiled
into better bounded model evidence.
