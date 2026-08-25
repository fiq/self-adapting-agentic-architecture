# 5. AST as deterministic measurement, never as a mutation operator

Date: 2026-08-23

## Status

Accepted, 2026-08-26.

Accepted after three independent review passes, each of which changed it:

- a research pass found `C1` **unimplementable** — it specified a lossy summary
  and then asked `C3` to compute tree edit distance from it, which needs the tree;
- a second pass found the base case **contradictory** (it parsed only the changed
  symbol while promising to detect edits outside it) and **aimed at the wrong
  consumer**, since the declared-locus gate has a live consumer today in
  `MutationContract.loci` and duplicate detection does not;
- a validation pass found two claims in the supporting prose that were simply
  false, including one asserting a safety net the realization gate does not
  provide.

The core decision — the AST measures, and never mutates — survived all three
unchanged. What changed was every contract around it, which is the reason to
trust the decision rather than a reason to doubt it.

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

## Unsupported is a work item, not a dead end

This is a self-adapting agentic system, and that changes what "we do not support
that language" should mean. In an ordinary tool, `UNSUPPORTED` is a shrug. Here it
is the most useful signal the system can emit, because there is an agent on the
other side of it who can implement the missing piece.

So `UNSUPPORTED` and `UNPARSEABLE` must **fail with instructions**, not with a
status:

```
   candidate in language L
          |
          v
   no inspector registered for L
          |
          v
   NOT: "unsupported, score 0"
   BUT: a work item naming
          - the port to implement (SourceStructureInspector)
          - the contract it must satisfy (C1's StructuralEvidence shape)
          - the completeness states it must distinguish (C5)
          - the acceptance tests it must pass
          - what is already implemented, as a worked example
```

That is the difference between a system that degrades and one that can be grown.
The failure is a specification for its own repair.

Three constraints keep it honest, and they matter more here than the idea does.

- **The instruction is generated from the contract, not written by a model.** The
  port signature, the evidence shape and the required completeness states are
  facts the deterministic layer already holds. A model may render them; it may
  not invent them.
- **Implementing the component does not promote anything.** A newly contributed
  inspector is a normal change: reviewed, tested, and landed through the usual
  gates. `ARCH-001` is unchanged — nothing here lets the loop widen its own
  capabilities without a human in the path.
- **An unsupported language never scores.** The temptation is to treat a missing
  inspector as a neutral result so runs keep flowing. That is the
  unmeasured-objective defect again, and it would reward a candidate for being in
  a language nothing can read. C5's rule stands: no evidence, no objective, no
  contribution.

This generalises past parsers. The same shape applies to a missing benchmark
harness, a check runner for an unfamiliar build system, or an adapter for an
architecture the loop has not met. The registry of what SAAA can measure is
itself a thing the loop can be asked to extend — which is the project's own
premise turned on its own tooling.

Scope note: nothing above is in the base case. It is recorded now because it
changes how `UNSUPPORTED` should be *shaped* when the first inspector lands, and
a status enum is much harder to turn into a work item afterwards.

## Parser choice, researched

The ADR said "tree-sitter now, code property graph later". Research found three
facts that make that wrong for the base case.

**1. The official Java binding needs a JDK this project does not run.**
`io.github.tree-sitter:jtreesitter` 0.26.1 requires **JDK 23+** and uses the FFM
(Panama) API. This was written while the project pinned JDK 21 and is **no longer a blocker**:
the toolchain moved to JDK 25 with Gradle 9. The remaining two objections below
stand on their own and are what actually decide the base case.

**2. It needs native grammar libraries.** tree-sitter and each grammar are
`.so`/`.dylib` artifacts present at build and run time. That is a real change to
a Nix-pinned, hermetic build, and it is per-language: multi-language support means
multi-native-artifact support.

**3. It does no semantic analysis at all.** tree-sitter parses; it does not
resolve symbols, track scopes or infer types. This confirms what the base case
already suspected — `changedSymbolIds` cannot come from tree-sitter. Symbol
identity would have to be built on top, and the declared-locus gate depends on it.

### Consequence: JavaParser for the base case, tree-sitter when multi-language is real

The base case is Java-only and its consumer is the declared-locus gate, which
needs exactly the symbol resolution tree-sitter lacks. **JavaParser** with
`javaparser-symbol-solver-core` is pure JVM, needs no native artifacts, no FFM and
no JDK bump, and `JavaSymbolSolver` resolves a name to the declaration it refers
to — which is what a locus gate has to decide. Its licensing is Apache-2.0 /
LGPL-3.0 and would need checking against this repository's constraints before
adoption.

tree-sitter remains the right answer for the multi-language step, and that step is
now visibly more expensive than the ADR implied: a JDK bump or a third-party
binding, plus native artifacts per grammar, plus symbol identity built by hand.
That cost belongs in the right-sizing revisit rather than hidden inside a
technology name.

**What I have not verified:** JavaParser's own JDK-21 compatibility and its
behaviour on incomplete or non-compiling sources, which matters directly to the
`RECOVERED_WITH_ERRORS` completeness state. tree-sitter's error recovery is one of
its genuine strengths, so this is the axis on which the choice could flip back.

## One abstraction, filled to different depths

The first draft had two: a `StructuralEvidence` record now, and a code property
graph "later". That is a mistake. They are the same thing at different
fidelities, and keeping both means every capability eventually branches on which
one it received — the combinatorial mess of capabilities times languages, wearing
a tidy name.

**There is one model. Frontends fill as much of it as they can. Capabilities
declare which parts they need.**

```
   ONE MODEL — layered, language-agnostic

   ┌─────────────────────────────────────────────┐
   │ syntax layer     nodes, kinds, nesting      │ ← every frontend fills this
   ├─────────────────────────────────────────────┤
   │ symbol layer     declarations, references   │ ← language tools fill this
   ├─────────────────────────────────────────────┤
   │ flow layer       control, data dependence   │ ← richer frontends, later
   └─────────────────────────────────────────────┘
            ▲              ▲              ▲
     tree-sitter      JavaParser      something else
     (~200 langs,     (Java, syntax   (whatever a user's
      syntax only)     + symbols)      language has)
```

A capability names the layers it needs, and a frontend declares the layers it
fills. The locus gate needs the symbol layer, so it is available for Java and
reports `UNSUPPORTED` elsewhere. Distance, complexity and convergence need the
syntax layer, so they work everywhere a grammar exists. **No capability ever asks
which parser produced its input.**

That is the difference between one abstraction and two: the variation lives in
*which layers are populated*, which is data, rather than in *which type you got*,
which is a branch in every consumer.

### Why this shape and not our own invention

This is what a code property graph already is: one attributed multigraph
combining syntax, control flow and data dependence, with a common query surface
across several language frontends. The earlier research reached that conclusion
and then filed it under "later", which was the error.

We adopt the **shape** without adopting the platform. Joern is an external
analysis stack well beyond a local CLI, and the research was right about that.
But defining our own minimal layered model, CPG-shaped, costs little now and
means the platform remains an option rather than a rewrite — and it is the same
model the earlier "one graph across the APIs a project knows about" idea needs.

### What this replaces in the contracts

`C1`'s `StructuralEvidence` becomes the syntax-layer projection of this model
rather than a separate type, and its `completeness` states describe **which
layers were filled**, not merely how well parsing went. `C2`'s inspector port
returns the model. `C3`'s relations declare the layers they consume.

The base case is unchanged: one frontend, syntax and symbol layers, Java, feeding
the declared-locus gate. What changes is that the second language does not need a
second abstraction — only a second frontend.

## The target language is the user's, not ours

A framing error ran through the first version of this ADR and needs correcting
before any of it is built.

SAAA is pointed at **whatever project a user wants to evolve**, and at their
agentic workflow alongside it — the whole development lifecycle, not only the
workflow files this repository happens to own. So "which parser" was never a
single choice. The target language is unknown at design time, and there is no
version of this where one parser is the answer.

That has three consequences.

**JavaParser is not "the base-case parser". It is the parser for Java targets.**
It earns its place in the first slice only because SAAA evolving SAAA is a Java
target, which makes it the cheapest honest end-to-end proof. Nothing about the
decision depends on Java, and the contracts must not either.

**Capabilities have different parser requirements, and this is the useful
distinction the first draft missed:**

| Capability | Needs | Off-the-shelf coverage |
|---|---|---|
| Duplicate detection | a tree | broad — tree-sitter has grammars for ~200 languages |
| Structural distance | a tree | broad |
| Complexity | a tree | broad |
| Declared-locus gate | **symbol resolution** | narrow — needs a language-specific tool |

Three of the four capabilities need only structure. One needs semantics. So the
honest architecture is not "one parser, chosen now" but **structure everywhere,
semantics where a language tool exists** — with the port shaped so the difference
is visible rather than hidden.

**Do not build parsers.** This project is not a compiler project and has no
business writing grammars. The rule is off-the-shelf first, and the port exists
to make wrapping something else easy. A thin port is not a stylistic preference
here; it is what determines whether adding a language is an afternoon or a
quarter.

## Adding a language should be work SAAA hands out, not work it absorbs

This is where the earlier "unsupported is a work item" section gets sharper. The
work item is **not** "implement a parser". It is:

```
   unsupported language L
          |
          v
   1. is there an existing parser for L?      ← almost always yes
   2. wrap it to SourceStructureInspector      ← the actual work
   3. declare which capabilities it supports   ← structure only, or symbols too
   4. pass the acceptance tests the port names
```

Step 1 is the one that keeps this tractable, and it is the step an agent is
genuinely good at: finding an existing library, reading its API, writing an
adapter against a contract that already has tests. Step 2 is bounded. Nobody
writes a grammar.

A wrapped parser that supplies structure but not symbols is a **first-class
outcome, not a degraded one**. It unlocks three of four capabilities for that
language. The declared-locus gate reports `UNSUPPORTED` for that language and
says so, rather than the language being refused outright.

## First task: spike JavaParser before committing to it

The base case names JavaParser for its Java target, and two things about it are
unverified. Both are
cheap to settle and both could change the answer, so they are settled first —
the same discipline that spiked Neo4j before any code was written against it,
and found the container ran its tests for real rather than skipping them.

- **Does it work on JDK 25?** The toolchain moved during this ADR's own review.
- **What does it do with source that does not compile?** This decides the
  `RECOVERED_WITH_ERRORS` state. Error recovery is one of tree-sitter's genuine
  strengths, so a poor answer here is the axis on which the parser choice flips
  back — at which point the JDK objection is already gone and only native
  artifacts and absent symbol resolution remain against it.
- **Licensing.** Apache-2.0 / LGPL-3.0 needs checking against this repository's
  constraints.

A spike that answers those three is the first deliverable. If it flips the
choice, the rest of this decision is unaffected: the AST still measures and
still never mutates, and `C1` through `C5` are parser-agnostic by construction.

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
