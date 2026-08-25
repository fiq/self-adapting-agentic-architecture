# 5. AST as deterministic measurement, never as a mutation operator

Date: 2026-08-23

## Status

Proposed.

## Context

Three problems in this repository are the same problem wearing different clothes.

**We cannot tell two candidates apart.** The intended population slice — a change
that does not exist yet; `CHG-025` is the name this repository's forward
references reserve for it — will have to detect when several candidates are
really the same candidate. The naive plan is to hash the committed diff, which
is wrong in both directions: two diffs differing only in whitespace hash
differently and are the same change, while two genuinely different edits can
produce equal line counts and look alike to `parsimony`.

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

**Distance** is what the future population change would use instead of diff
hashing: when `CHG-025` is proposed, two realizations with the same
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
convergence, only to measure structural spread. The analogy is one of purpose,
not mechanism: islands *prevent* premature convergence by partitioning, while
the trigger here *detects* it by measurement. Convergence therefore ships no
earlier than `STRUCTURAL_DISTANCE`: the mean pairwise distance it reads is that
relation's output, so both arrive together, and its per-generation cost is the
pairwise O(n²) number of comparisons on a generation of size n on top of the
per-candidate parse.

Crucially this is a **selection rule applied after eligibility**, never a
weighted objective. That distinction needs enforcing rather than asserting: a
tripwire test pins posture and trend out of the objective set, in the same spirit
as the existing tripwire asserting every operator shares the scorer's objective
set. The posture decision is owned by the population slice's planner — named here
so it does not become nobody's. `Q-006` and the existing handoff design input both record
that adding trend or posture to the weighted sum would double-count and make
candidates from different parents incomparable. Convergence changes what we ask
for next; it never changes what promotes now.

Two cautions on the trigger. A mean pairwise distance is blind to bimodality: two
tight clusters far apart read as spread while the search has actually collapsed
into two holes rather than one. And a threshold with no hysteresis will oscillate
between postures at the boundary. Both argue for deferring the trigger until
post-calibration telemetry exists, rather than picking a number now.

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

The source of the tree must be named, because `StructuralEvidence` carries a
hash, not a tree. Retention is per-evaluation, in memory, by the
`StructuralComparisonService` itself: the service that parsed the evidence keeps
the tree alive only as long as the comparisons it performs. Anything needing a
tree later re-parses the recorded source revision through the same pinned
parser and normalization policy; it never persists the tree, because a persisted
tree would bypass the C4 provenance check on its next read.

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
model, later any rewrite-law set or CPG schema version, **and the observation
envelope** wherever `OBSERVATIONALLY_EQUIVALENT` is claimed. Any mismatch yields
`INCOMPARABLE` rather than a silently numeric answer — the rule the
`ScoringContext` fingerprint already applies to fitness magnitudes, and which an
independent review showed is easy to under-specify: that fingerprint originally
omitted three inputs that changed what a magnitude meant.

The observation envelope is fingerprinted over case and benchmark **content or
pinned revision**, never over ids alone — two suites sharing a case name and
differing in what the case asserts are not the same envelope. Below a declared
minimum bar — no held-out cases, or no benchmark where a performance claim is
made — **no equivalence verdict issues at all** rather than a weak one.

**C5 — four completeness states, not two.** `COMPLETE` alone is eligible for a
blast-radius gate. `UNPARSEABLE` and `UNSUPPORTED` yield no evidence. If either
side of a comparison is not `COMPLETE`, the result is `INCOMPARABLE` and the run
records why.

`RECOVERED_WITH_ERRORS` may inform diagnostics and **must not become a value in
the weighted sum**. An earlier draft let partial-parse metrics enter the sum "as
any objective does", which would arithmetically compare a candidate that broke
the parser against one parsed completely, by magnitude, on a promotion channel.
An objective derived from non-`COMPLETE` evidence counts as **unmeasured**. That
matters more than it looks, because an unmeasured objective currently contributes
its full weight — so the two defects would have compounded into a candidate
scoring well for being unparseable.

## Behavioural equivalence is measurable — empirically, not statically

Rice's theorem forecloses *deciding* behavioural equivalence for arbitrary
programs by static analysis. It says nothing about **observing** it.

SAAA already owns the apparatus. Acceptance behaviour cases, held-out cases,
benchmarks and declared external contracts form an envelope, and two candidates
that agree across all of it are observationally equivalent *within that
envelope*. That is a real, deterministic, reproducible measurement — and unlike
the static relations above, it speaks to behaviour rather than shape.

So C3 gains a fourth relation, and it is the only one entitled to the word
behaviour:

| Relation | Kind | Speaks to |
|---|---|---|
| `EXACT_NORMALIZED_DUPLICATE` | static | shape |
| `STRUCTURAL_DISTANCE` | static | shape |
| `EQUIVALENT_UNDER_LAWS` | static, future | algebra under declared rewrites |
| `OBSERVATIONALLY_EQUIVALENT` | **empirical** | behaviour, within a declared envelope |

Three constraints keep it honest.

- **The envelope must be declared and versioned.** "Equivalent" means nothing
  without naming which cases, which benchmarks and which contracts were run.
  Widening or narrowing the envelope changes the claim, so it is part of the
  comparable identity exactly as the scoring fingerprint is.
- **Side effects are excluded, and that exclusion is a caveat rather than a
  detail.** Two candidates agreeing on every observed output may still differ in
  what they write, log, emit, retry or cost. The envelope observes what it
  observes; the claim is bounded by it.
- **Absence of a difference is not proof of sameness.** A passing envelope shows
  no difference was detected, which is weaker than equivalence and must be
  reported as such.

This reframes the whole ADR's ambition usefully. Static structure is the *cheap
proxy*: it runs without executing anything and is available for every candidate
in a generation. Observational equivalence is the *expensive truth*: it requires
running the envelope. The right use of the AST is to decide which candidates are
worth spending the expensive measurement on — exactly the role FunSearch gives
its evaluator, with structure as the filter in front of it.

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

Revised after a second review pass, which found the original both contradictory
and pointed at the wrong consumer.

> The original said "parse the changed symbol, **not** the whole file" and then
> required detecting "a modification outside the declared symbol". You cannot
> detect an out-of-symbol edit from a parse of only that symbol. Detecting it
> needs a whole-file parse or a symbol map.

**Blast radius is the first consumer, not duplicate detection.** It is the one
capability with a live consumer *today*: `MutationContract` already carries
`loci` and `MutationTarget(file, symbol)`, and nothing currently checks that the
realization respected them. Duplicate detection's consumer is the population
slice, which does not exist yet. Choosing a capability whose consumer is absent
would have delivered plumbing with three sensitivity proofs and no observable
change in system behaviour — against this repository's outside-in discipline,
where changes are normally driven outside-in. That norm is not universal —
`CHG-023` shipped with unit and integration coverage and no acceptance test — but
it was a change to a domain type with several live consumers, which is the
property that actually matters here. A capability whose only consumer does not
exist yet has neither.

So the slice is:

1. Parse the **changed file**, and locate the declared symbol within it. Produce
   `StructuralEvidence` including `changedSymbolIds`.
2. **A declared-locus gate**: a realization that modified anything outside the
   contract's declared loci is discarded. That is a gate with a live contract
   behind it, provable by acceptance test.
3. `EXACT_NORMALIZED_DUPLICATE` ships alongside as the cheapest thing exercising
   the same evidence, and becomes load-bearing when the population slice arrives.

**Stable symbol identity is unsolved design work and is called out rather than
assumed.** tree-sitter yields nodes, not identities that survive an edit.
`changedSymbolIds` needs a defined derivation — qualified name plus arity is the
obvious candidate — and a stated behaviour when a symbol is renamed, which under
the declared-locus gate should be treated as leaving the locus.

**Honest scope note:** whitespace and comment invariance does not strictly need
an AST; a token-stream normaliser over the extracted symbol gets most of it. The
AST earns its place through the locus gate and what follows it, not through the
duplicate rule alone.

## Normalization policy v1: what is erased

The duplicate rule's semantics *are* this table, so it is enumerated rather than
left to implementation.

| Erased | Preserved | Why |
|---|---|---|
| Whitespace, indentation, line breaks | — | Formatting is not behaviour |
| Comments | — | Not behaviour; but see the caveat below |
| Import order | Import set | Order is not behaviour; presence is |
| — | Identifier names | Renaming a variable is a real change to a reader, and the model proposing it meant something by it |
| — | Literal values | Changing a constant is the commonest real mutation |
| — | Statement order | Reordering can change behaviour, and proving otherwise is the job of `EQUIVALENT_UNDER_LAWS` |
| — | Generated code | Never normalised specially; if generated code is in scope it is scored like any other |

The comment caveat, corrected after checking it: erasing comments means a
candidate whose only change is a comment normalizes to a duplicate of its parent.
An earlier draft of this section claimed the non-empty-realization gate would
already have discarded such a candidate. **It would not.** That gate tests
`filesChanged > 0`, and a comment edit changes a file — the repository's own risk
register records the same thing about a one-character whitespace edit.

So a comment-only candidate passes every gate, occupies a worktree, and is a
duplicate. That is not a case the existing gates handle; it is precisely a case
the duplicate rule has to catch, which strengthens rather than weakens the reason
to erase comments. Prompt or policy text living in comments would be a reason to
revisit this row.

Anything not in this table is preserved. Changing a row requires a new
normalization policy id, because it changes what "the same candidate" means.

## Consequences

- The deterministic layer gains its first parsing dependency, behind a port. The
  architecture fitness function must be extended so a parser cannot be imported
  from `domain` or `deterministic` directly, the same way LangChain4j cannot.
- `PROJECT_PROFILE` records AST-aware work as a right-sizing revisit trigger.
  This decision revisits it deliberately and **narrows** it: measurement is in
  scope, AST-aware *realization* remains out of scope and still needs its own
  decision.
- Parsing cost is per candidate per generation. It is far cheaper than running
  checks, but it is not free and belongs in the cost accounting the population
  change will have to do when proposed.
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
