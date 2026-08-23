# 5. AST as deterministic measurement, never as a mutation operator

Date: 2026-08-23

## Status

Proposed.

## Context

Three problems in this repository are the same problem wearing different clothes.

**We cannot tell two candidates apart.** The population slice (`CHG-025`) has to
detect when several candidates are really the same candidate. The plan of record
was to hash the committed diff, which is wrong in both directions: two diffs
differing only in whitespace hash differently and are the same change, while two
genuinely different edits can produce equal line counts and look alike to
`parsimony`.

**We cannot tell when the search has stalled.** `CON-001` defines two search
postures, `hill-climb` and `exploratory-leap`, and `Q-006` records the intent
that exploitation and exploration be deliberate. Nothing decides *when* to switch.
A loop that only hill-climbs settles into a local optimum and keeps reporting
success, because every candidate it produces clears the same gates as the last.

**Our objectives barely vary.** `CHG-024` freed `task_success`, but
`cost_latency_budget` needs benchmarks nobody declares, `behavioral_safety` needs
probes nobody declares, and `parsimony` measures lines changed — a proxy so
crude that a whitespace edit scores well.

All three want the same missing thing: **a deterministic measurement of code
structure**.

### What the research supports

Two independent lines of work bear on this directly.

Tree edit distance over ASTs is an established similarity measure for generated
code. **TSED** (Tree Similarity of Edit Distance) normalises the minimum cost of
insert, delete and rename operations transforming one AST into another into a
`[0,1]` score, and correlates well with execution-based measures while being far
cheaper than running anything. It is deterministic, language-parametric, and
immune to the formatting noise that defeats textual diffing.

**FunSearch** pairs a pretrained LLM with a *systematic evaluator* and evolves
programs, and is explicit that the LLM proposes while the evaluator decides —
the same boundary `ARCH-001` draws here. Its relevant mechanism is the **islands
model**: populations are held in subpopulations specifically to *mitigate
premature convergence*. That is a named, published treatment for the second
problem above, and it does not require random mutation to work.

### What we are not doing

Neither result argues for random AST mutation, and `AGENTS.md` forbids it without
a recorded decision. Random structural mutation of a syntax tree produces mostly
invalid or meaningless programs and shifts the burden onto the evaluator to
reject them, which is the opposite of loop engineering. The model remains the
proposer because it proposes *plausible* variants; the AST is how we
deterministically understand what it proposed.

## Decision

**Adopt the AST as a deterministic measurement surface. Do not adopt it as a
mutation operator.**

Concretely, the AST answers four questions, all of them in the deterministic
layer, none of them involving a model:

```
   candidate realization
          │
          ▼
   ┌──────────────┐
   │  AST parse   │  adapter — the parser is a dependency
   └──────┬───────┘
          │  StructuralEvidence (domain value)
          ▼
   ┌───────────────────────────────────────────────┐
   │ 1. DISTANCE   how different are two candidates │ → duplicate detection,
   │               really? (TSED-style, [0,1])      │   novelty slots
   │                                                │
   │ 2. CONVERGENCE is the generation collapsing     │ → hill-climb ⇄
   │               toward one shape?                 │   exploratory-leap
   │                                                │
   │ 3. BLAST RADIUS did the edit stay inside the    │ → gate: contract
   │               loci the contract declared?       │   compliance
   │                                                │
   │ 4. COMPLEXITY  did it get structurally worse?   │ → objective that
   │               (nesting, fan-out, cyclomatic)    │   actually varies
   └───────────────────────────────────────────────┘
```

### Why each one earns its place

**Distance** replaces diff hashing in `CHG-025`. Two realizations with the same
normalized syntax hash for the changed symbol are the same candidate, and the
generation records `duplicate_realization` on evidence rather than on text. A
thresholded distance is deliberately *not* the duplicate rule: a threshold needs
calibrating against labelled examples before anyone can defend the number, so
distance stays a novelty and convergence signal until it has been.

**Convergence** is the deterministic trigger `SearchPosture` never had. When the
mean pairwise distance across a generation falls below a declared floor, the
population has converged and the next generation's slots shift from `hill-climb`
to `exploratory-leap`. This is FunSearch's islands insight applied without
adopting islands: we do not need subpopulations to *detect* premature
convergence, only to measure structural spread.

Crucially this is a **selection rule applied after eligibility**, never a
weighted objective. `Q-006` and the existing handoff design input both record
that adding trend or posture to the weighted sum would double-count and make
candidates from different parents incomparable. Convergence changes what we ask
for next; it never changes what promotes now.

**Blast radius** closes the gap `RISK-002` describes for a different gate. A
mutation contract declares its loci; today nothing checks the realization
respected them. An AST makes "the edit touched only the declared method" a
decidable question rather than a hope.

**Complexity** gives a real objective. Unlike `parsimony`, structural complexity
cannot be gamed by reformatting, and unlike the three currently-pinned
objectives it varies between candidates that all pass.

## Contracts

These were revised after an independent research pass. The first version was
wrong in a way worth recording.

> **C1 as originally written could not support C3.** It specified a
> `StructuralSummary` of node counts, maximum nesting depth, fan-out and a hash,
> then asked C3 to compute tree edit distance from it. Tree edit distance needs
> the tree. Summary statistics cannot reconstruct one, so the distance policy
> could never have been implemented against the value the domain carried.

**C1 — `StructuralEvidence` (domain).** Auditable evidence, not a bag of
statistics:

```
StructuralEvidence
  schemaId, languageId, parserId, normalizationPolicyId
  sourceContentHash
  normalizedSyntaxHash          <- exact-duplicate decisions use this
  changedSymbolIds              <- stable enclosing symbols
  changedNodeFingerprints       <- data, never parser types
  metrics { kindCounts, maxDepth, fanOut, ... }   <- complexity/convergence only
  completeness { COMPLETE | RECOVERED_WITH_ERRORS | UNPARSEABLE | UNSUPPORTED }
```

`metrics` informs complexity and convergence. It must never decide identity.

**C2 — inspection and comparison are separate ports.**
`SourceStructureInspector` returns `StructuralEvidence` including the exact
parser, grammar and normalization policy ids. A separate adapter-side
`StructuralComparisonService` retains or reconstructs the normalized tree to
compute a distance. Splitting them stops the domain claiming to hold enough
information for a comparison it cannot perform. Parser objects stay in
`adapters`; `changedNodeFingerprints` cross the boundary as data.

**C3 — three relations, not one distance.** Collapsing these into a single
`[0,1]` number is what invites the false claim that structure implies behaviour:

| Relation | Meaning | Cost |
|---|---|---|
| `EXACT_NORMALIZED_DUPLICATE` | canonical normalized hash equality | cheap, exact |
| `STRUCTURAL_DISTANCE` | bounded tree distance, syntax-relative | moderate |
| `EQUIVALENT_UNDER_LAWS` | `PROVEN / NOT_PROVEN / UNSUPPORTED / BUDGET_EXCEEDED` plus a rewrite-law-set id | future, expensive |

**None of these may be described as "the same functional behaviour."** Rice's
theorem forecloses deciding that for arbitrary programs; every relation here is
equivalence under an explicitly declared syntactic or algebraic policy.

**C4 — version the whole measurement provenance.** Policy ids alone are
insufficient. Comparable identity includes language and grammar version,
normalized-schema version, normalization policy, distance algorithm and cost
model, and later any rewrite-law set or CPG schema version. Any mismatch yields
`INCOMPARABLE` rather than a silently numeric answer — the rule the
`ScoringContext` fingerprint already applies to fitness magnitudes.

**C5 — four completeness states, not two.** `COMPLETE` alone is eligible for a
blast-radius gate. `RECOVERED_WITH_ERRORS` may feed metrics or advisory
comparison and may never gate. `UNPARSEABLE` and `UNSUPPORTED` yield no
evidence. If either side of a comparison is not `COMPLETE`, the result is
`INCOMPARABLE` and the run records why.

## Does category theory carry weight here?

**Not as a dependency, and not as a decision boundary.** The research separated
the genuinely applied results from the categorically-flavoured ones.

- **Catamorphisms and recursion schemes are useful as implementation
  discipline.** An AST is a recursive algebraic data type and every measurement
  above is a compositional fold over it. That improves testability and keeps one
  traversal honest; it adds no information and decides nothing.
- **"Compiling to categories"** is real but needs a restricted typed functional
  source language. It does not span arbitrary Java, Python or JavaScript methods.
- **Categorical graph rewriting (double-pushout)** is real, with genuine
  confluence theory, and would matter if SAAA ever adopted *verified*
  transformation rules with a preserved interface. ADR-0005 deliberately does not
  mutate, so it is the wrong abstraction at the wrong time.
- **Operads, props and sheaf-cohomological analysis** are research-stage. No
  primary source supports a mature, cheap, multi-language equivalence tool.

Honest verdict: **use categorical language for exposition and folds for
implementation; do not put category theory in a contract.** The load-bearing
mechanisms are a normalized representation plus graph analysis.

## Cross-language, and the one graph across APIs

A Java-specific tree is wrong long-term, and the answer is staged rather than a
single technology.

```
   NOW           tree-sitter concrete syntax trees
                 -> normalized per language, robust, error-recovering
                 -> gives EXACT_NORMALIZED_DUPLICATE and changed-symbol evidence
                      |
   LATER         code property graph: AST + CFG + PDG in one attributed
                 multigraph, the Joern/CPG schema shape
                 -> dependency cycles, interface preservation, reachability,
                    and the cross-API questions
                      |
   EXCEPTIONAL   e-graphs under an audited rewrite-law set, for a small pure
                 typed expression subset only
```

The CPG layer is what **a single graph spanning code and the APIs the project
knows about** would actually be built from: a directed, labelled, attributed
multigraph with a common query surface across language front ends. That is the
right shape for "did this edit introduce a dependency cycle", "did it preserve
the exported interface", "does this reach a public API".

Two things make it cheaper here than it sounds. `ADR-0004` already runs a local
Neo4j with repository-partitioned `SUBJECT` and `PROCESS` projections linked by
evolution contexts, so a structural projection extends an experiment that exists
rather than adding infrastructure. And the projection stays *derived* — the
repository remains canonical, as `docs/context-store.md` requires.

What must not be smuggled in with it: CPG front ends differ in resolution
precision across dynamic dispatch, reflection, generated code and incomplete
projects. A CPG query is an analysis result, not a language-independent proof,
and adopting the full Joern stack is well beyond the local-CLI base case.

Learned embeddings such as GraphCodeBERT or UniXcoder are excluded from C1–C5.
They give probabilistic ranking, not reproducible symbolic identity, and
`ARCH-001` does not permit a model-version-dependent number to gate a promotion.
They stay legitimate as advisory retrieval, where `ARCH-002` already puts that
class of evidence.

## The base case

The smallest slice that proves the idea and is useful alone:

1. Parse the **changed symbol** — not the whole file — and produce
   `StructuralEvidence` with a normalized syntax hash. Java first, via
   tree-sitter if multi-language is wanted immediately.
2. **Duplicate detection in `CHG-025` uses `EXACT_NORMALIZED_DUPLICATE` on the
   changed symbol**, replacing the diff hash. Not a thresholded whole-file
   metric, which would need calibration before the threshold could be trusted.
3. Prove exactly three things: a whitespace- or comment-only change normalizes
   identically; a changed statement does not; a modification outside the declared
   symbol is detected.

`STRUCTURAL_DISTANCE` stays a novelty and convergence signal until calibrated
against labelled examples. Nothing claims behavioural equivalence.

## Consequences

- The deterministic layer gains its first parsing dependency, behind a port. The
  architecture fitness function must be extended so a parser cannot be imported
  from `domain` or `deterministic` directly, the same way LangChain4j cannot.
- `PROJECT_PROFILE` records AST-aware work as a right-sizing revisit trigger.
  This decision revisits it deliberately and **narrows** it: measurement is in
  scope, AST-aware *realization* remains out of scope and still needs its own
  decision.
- Parsing cost is per candidate per generation. It is far cheaper than running
  checks, but it is not free and belongs in the cost accounting `CHG-025`
  already has to do.
- If structural distance turns out not to discriminate usefully on real
  candidates, the honest outcome is to reject it and return to diff hashing. The
  base case is deliberately small enough that this is cheap.

## Revisit triggers

- Structural distance fails to separate candidates a human judges different.
- Parsing proves too slow or too fragile across the languages we target.
- A Layer-3 target needs AST-aware *realization*, which this decision does not
  grant and which requires a superseding ADR.

## Non-goals

- Random or model-directed AST mutation.
- AST-aware realization, hunk application, or refactoring transforms.
- LSP integration.
- Replacing execution-based evidence. Structure is a cheap proxy that
  complements behaviour checks; it never decides promotion on its own.

## References

- [Revisiting Code Similarity Evaluation with Abstract Syntax Tree Edit Distance](https://aclanthology.org/2024.acl-short.3/) — TSED, ACL 2024.
- [Mathematical discoveries from program search with large language models](https://pubmed.ncbi.nlm.nih.gov/38096900/) — FunSearch, Nature.
- [FunSearch overview](https://en.wikipedia.org/wiki/FunSearch) — islands model and premature convergence.
