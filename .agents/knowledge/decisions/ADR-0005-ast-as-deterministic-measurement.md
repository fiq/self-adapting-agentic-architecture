---
id: ADR-0005
type: decision
title: AST as deterministic measurement, never as a mutation operator
status: proposed
reviewed_at: 2026-08-23
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

## Base case

Ship the normalized syntax hash of the changed symbol, consumed by duplicate
detection in the population slice. Convergence, blast radius and complexity
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

## Category theory is not load-bearing

Catamorphisms are useful implementation discipline — every measurement here is a
compositional fold — but add no information and decide nothing. "Compiling to
categories" needs a restricted typed functional source language. Categorical
graph rewriting would matter only if SAAA adopted verified transformation rules,
which this decision explicitly does not. Rice's theorem forecloses deciding
behavioural equivalence for arbitrary programs, so every relation offered here is
equivalence under a declared syntactic or algebraic policy and must never be
described as "the same functional behaviour".

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
