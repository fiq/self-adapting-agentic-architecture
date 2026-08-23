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
          │  StructuralSummary (domain value)
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

**Distance** replaces diff hashing in `CHG-025`. Two realizations whose
structural distance is below a declared threshold are the same candidate, and the
generation records `duplicate_realization` on evidence rather than on text.

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

These are the interfaces to agree before implementation.

**C1 — `StructuralSummary` (domain).** A plain value: node counts by kind,
maximum nesting depth, fan-out, and a canonical structural hash. No parser types
leak into it. `domain` keeps its zero dependencies.

**C2 — `SourceStructureInspector` (deterministic port).** Takes a candidate and a
path set, returns `StructuralSummary` per file. The implementation lives in
`adapters`, because a parser is a provider-shaped dependency and
`deterministic` must stay free of them. This is the same boundary that keeps
LangChain4j out of the scoring layer.

**C3 — `StructuralDistance` (deterministic policy).** Pure function over two
`StructuralSummary` values returning `[0,1]`. Deterministic, total, and
independent of formatting. Tree edit distance is the intended implementation;
the port does not name it, so a cheaper approximation can be substituted under a
new policy id.

**C4 — versioned policy ids.** `structural-distance-v1`,
`convergence-posture-v1`. Changing semantics requires a new id, exactly as
`lineage-novelty-v1` and the `ScoringContext` fingerprint already do. A distance
computed under one policy is never compared with one computed under another.

**C5 — language support is explicit and degradable.** An unparseable or
unsupported file yields *no* summary rather than a zero-distance one. Absent
structure is not identical structure — the same rule as absent evidence not
being passing evidence. A generation whose candidates cannot be parsed falls
back to the existing behaviour and records that it did.

## The base case

The smallest slice that proves the idea and is useful on its own:

1. `StructuralSummary`, `SourceStructureInspector`, `StructuralDistance` with a
   Java implementation in `adapters`.
2. **Duplicate detection in `CHG-025` uses structural distance instead of a diff
   hash.** One consumer, immediately load-bearing, and directly testable: two
   candidates differing only in whitespace must be detected as duplicates, and
   two differing in a statement must not.
3. Nothing else. Convergence, blast radius and complexity land only once the
   distance measure is trusted.

That ordering matters. Distance is the primitive the other three are built from,
and shipping it alone means a defect in it surfaces in one place rather than
four.

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
