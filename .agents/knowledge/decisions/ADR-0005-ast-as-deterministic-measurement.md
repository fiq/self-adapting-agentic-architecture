---
id: ADR-0005
type: decision
title: AST as deterministic measurement, never as a mutation operator
status: canonical
reviewed_at: 2026-08-26
summary: Adopt the abstract syntax tree as a deterministic measurement surface for structural distance, convergence detection, blast-radius checking and complexity, all inside the deterministic layer with the parser behind an adapter port. Random or model-directed AST mutation stays forbidden, and AST-aware realization remains out of scope pending its own decision.
owners:
  - architect
relates_to:
  - ARCH-001
  - CON-001
  - SYS-001
  - Q-006
  - RISK-002
decisions:
  - ADR-0002
evidence:
  - docs/decisions/0005-ast-as-deterministic-measurement.md
  - PROJECT_PROFILE.toon
review_after: 2027-02-28
---

# ADR-0005: AST as Deterministic Measurement

Accepted 2026-08-26 after three independent review passes. The core decision was
unchanged by all three; every contract around it was rewritten by them.

Details live in `docs/decisions/0005-ast-as-deterministic-measurement.md`. This
node exists so specs, wiki pages and other knowledge entries can link the
decision by id.

## Summary

Three open problems are one problem: the population slice cannot tell two
candidates apart, `SearchPosture` has no deterministic trigger for switching
between `hill-climb` and `exploratory-leap`, and the weighted objectives barely
vary between candidates that pass. All three want a deterministic measurement of
code structure.

The AST supplies it, and answers four questions without a model anywhere near
them: how different two candidates really are, whether a generation is
converging, whether an edit stayed inside its declared loci, and whether
structural complexity got worse.

## Load-bearing rule

The model proposes because it proposes *plausible* variants. The AST is how the
deterministic layer understands what was proposed. Random structural mutation
produces mostly invalid programs and pushes the burden onto the evaluator, which
inverts loop engineering. `ARCH-001` is unchanged: measurement never approves.

## Convergence is a selection rule, not an objective

When structural spread across a generation collapses, the *next* generation asks
for exploration instead of exploitation. It never changes what promotes now.
Adding posture or trend to the weighted sum would double-count objectives and
make candidates from different parents incomparable, which `Q-006` and the
recorded design input both warn against.

## Boundary

The parser is a provider-shaped dependency and lives in `adapters` behind
`SourceStructureInspector`, exactly as LangChain4j lives behind its own boundary.
The architecture fitness function must be extended to enforce it.

## Unsupported is a work item, not a dead end

In a self-adapting system, "no inspector for this language" is the most useful
signal available, because an agent can implement the missing piece. UNSUPPORTED
and UNPARSEABLE therefore fail with instructions - the port to implement, the
evidence shape it must produce, the completeness states it must distinguish, the
acceptance tests it must pass - rather than with a status.

Three constraints hold. The instruction is generated from the contract rather
than authored by a model. Implementing the component is a normal reviewed change,
so ARCH-001 still holds and the loop cannot widen its own capabilities unattended.
And an unsupported language never scores, because treating a missing inspector as
neutral would reward a candidate for being unreadable - the unmeasured-objective
defect again.

Generalises past parsers to any missing measurement capability: a benchmark
harness, a check runner for an unfamiliar build system, an adapter for an
unmet architecture.

## Conformance tests are the contract

Find-a-parser-and-wrap-it is only safe if wrapped correctly is decidable. The port
therefore ships a language-agnostic conformance suite, and passing it is what
supported means. The suite is written once against the port; a frontend supplies
fixtures in its own language and declares which layers it fills, and is tested on
exactly that declaration. It is also what the unsupported-language work item
points at: not implement the port, but make this suite pass with your language's
fixtures - finishable, checkable, and verifiable by a human who cannot read the
adapter's language. That is what keeps ARCH-001 honest for code nobody here wrote.

## Spike result, 2026-08-26

JavaParser runs on JDK 25 and resolves symbols - name.trim() to
java.lang.String.trim() with only a ReflectionTypeSolver - which is what the
declared-locus gate needs and what tree-sitter cannot supply at any grammar count.
Its error recovery is weak: on broken source it returns a partial result whose
tree contained no types and no methods. The Java frontend must therefore report
UNPARSEABLE rather than RECOVERED_WITH_ERRORS when a partial tree yields no
declarations, because partial success over an empty tree is absence dressed as
evidence. Licensing is still unchecked.

## One abstraction, filled to different depths

The first draft had two - a StructuralEvidence record now and a code property
graph later - which are the same thing at different fidelities. Keeping both means
every capability eventually branches on which one it received.

There is one layered model. Every frontend fills the syntax layer; language tools
fill the symbol layer; richer frontends fill flow later. A capability names the
layers it needs and a frontend declares the layers it fills, so the locus gate is
available where symbols are and reports UNSUPPORTED elsewhere, while distance,
complexity and convergence work anywhere a grammar exists. No capability asks
which parser produced its input.

The variation lives in which layers are populated, which is data, rather than in
which type you got, which is a branch in every consumer.

The shape is a code property graph's - one attributed multigraph over syntax,
control flow and data dependence with a common surface across frontends. We adopt
the shape, not the platform: Joern is an external analysis stack beyond a local
CLI, but a minimal CPG-shaped model costs little now and keeps the platform an
option rather than a rewrite. It is also the model the cross-API graph needs.

C1's StructuralEvidence becomes the syntax-layer projection rather than a separate
type, and its completeness states describe which layers were filled. The base case
is unchanged; what changes is that the second language needs a second frontend
rather than a second abstraction.

## The target language is the user's, not ours

SAAA is pointed at whatever project a user wants to evolve, and at their agentic
workflow alongside it. The target language is unknown at design time, so "which
parser" was never one choice. JavaParser is the parser for Java targets, and
earns the first slice only because SAAA evolving SAAA is a Java target.

Capabilities differ in what they need, which is the useful distinction. Duplicate
detection, structural distance and complexity need only a tree, and off-the-shelf
grammars cover roughly 200 languages. The declared-locus gate needs symbol
resolution, which needs a language-specific tool. So the architecture is
structure everywhere, semantics where a language tool exists.

Do not build parsers. This is not a compiler project. Off-the-shelf first, and
the port exists to make wrapping something else easy - a thin port decides whether
adding a language is an afternoon or a quarter.

Adding a language is therefore work SAAA hands out rather than absorbs: find an
existing parser, wrap it to the port, declare which capabilities it supports,
pass the acceptance tests the port names. Nobody writes a grammar. A wrapper
supplying structure but not symbols is a first-class outcome that unlocks three
of the four capabilities, not a degraded one.

## Parser choice

Researched rather than assumed. The official tree-sitter Java binding requires
JDK 23+ and the FFM API while this project pins JDK 21; it needs native grammar
artifacts per language in a Nix-pinned build; and it performs no semantic
analysis, so it cannot supply the symbol identity the declared-locus gate needs.
JavaParser with its symbol solver is pure JVM, needs no native artifacts and no
JDK bump, and resolves names to declarations, so it fits the Java-only base case.
tree-sitter stays the multi-language answer, and that step is now visibly more
expensive than a technology name implied.

## Base case

Ship the declared-locus gate as the first consumer: `MutationContract` already
carries `loci` and a `MutationTarget`, and nothing checks that a realization
respected them, so it is the one capability with a live consumer today.
Duplicate detection ships alongside and becomes load-bearing when the population
slice exists.

A second review found the original base case both contradictory - it parsed only
the changed symbol while promising to detect edits outside it - and pointed at
the wrong consumer, choosing the capability whose consumer does not exist yet.
Stable symbol identity across edits is called out as unsolved rather than
assumed: tree-sitter yields nodes, not identities that survive an edit. Convergence, blast radius and complexity
follow only once that primitive is trusted, so a defect surfaces in one place
rather than four. A thresholded distance is not the duplicate rule until the
threshold has been calibrated against labelled examples.

## Corrected after research

The first draft specified a lossy `StructuralSummary` — node counts, depth,
fan-out, a hash — and then asked the distance policy to compute tree edit
distance from it. Tree edit distance needs the tree, so that contract could
never have been implemented. C1 now carries auditable `StructuralEvidence`, and
inspection is separated from comparison so the domain cannot claim to hold
information it does not.

## Normalization is the duplicate rule

Policy v1 enumerates what is erased - whitespace, comments, import order - and
what is preserved - identifier names, literal values, statement order, generated
code. The table is the semantics, so changing a row requires a new policy id.

## Category theory is not load-bearing

Catamorphisms are useful implementation discipline — every measurement here is a
compositional fold — but add no information and decide nothing. "Compiling to
categories" needs a restricted typed functional source language. Categorical
graph rewriting would matter only if SAAA adopted verified transformation rules,
which this decision explicitly does not. Rice's theorem forecloses deciding
behavioural equivalence for arbitrary programs, so every relation offered here is
equivalence under a declared syntactic or algebraic policy and must never be
described as "the same functional behaviour".

## Behavioural equivalence is empirical, not static

Rice's theorem forecloses deciding behavioural equivalence statically; it says
nothing about observing it. Acceptance cases, held-out cases, benchmarks and
declared external contracts form an envelope, and two candidates agreeing across
it are observationally equivalent within that envelope. That is the only relation
here entitled to the word behaviour, and it is bounded three ways: the envelope
must be declared and versioned as part of comparable identity, side effects are
excluded, and no detected difference is weaker than sameness.

Structure is therefore the cheap proxy that decides which candidates are worth
the expensive measurement, not a substitute for it.

## One graph across APIs

A code property graph — AST, control flow and program dependence in one
attributed multigraph — is the right later shape for dependency cycles,
interface preservation and reachability across the APIs a project knows about.
ADR-0004's Neo4j already holds partitioned SUBJECT and PROCESS projections, so
this extends a running experiment rather than adding infrastructure, and the
projection stays derived. Learned embeddings are excluded from these contracts:
they rank probabilistically and cannot gate a promotion under ARCH-001.

## Explicitly not granted

Random or model-directed AST mutation, AST-aware realization, hunk application,
LSP integration, or replacing execution-based evidence.
