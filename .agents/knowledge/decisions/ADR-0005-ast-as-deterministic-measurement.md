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

Ship structural distance alone, consumed by duplicate detection in the
population slice. Convergence, blast radius and complexity follow only once
distance is trusted, so a defect in the primitive surfaces in one place rather
than four.

## Explicitly not granted

Random or model-directed AST mutation, AST-aware realization, hunk application,
LSP integration, or replacing execution-based evidence.
