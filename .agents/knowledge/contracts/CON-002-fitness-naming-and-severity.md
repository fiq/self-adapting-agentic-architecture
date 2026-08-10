---
id: CON-002
type: contract
title: Fitness identifier naming and severity classes
status: canonical
summary: Fitness identifiers carry a subject/process and invariant/objective prefix, and invariant violations are partitioned into integrity, safety, correctness and shape.
owners:
  - architect
relates_to:
  - ARCH-001
  - CON-001
risks:
  - RISK-002
evidence:
  - docs/superpowers/specs/2026-08-04-fitness-taxonomy-and-steered-evolution-design.md
  - docs/wiki/glossary.md
review_after: 2027-02-04
reviewed_at: 2026-08-10
---

# Fitness Identifier Naming and Severity Classes

## Naming

A fitness identifier names whose property it is and which force it carries:

```
subject.invariant.<name>     must hold on the candidate
subject.objective.<name>     compounds into the candidate score
process.invariant.<name>     must hold on SAAA itself
```

`subject` and `process` reuse the vocabulary already carried by
`RepositoryRole` and by the `subject_repository_id` and
`process_repository_revision` fields of the experiment envelope, rather than
introducing a parallel scheme.

The prefix encodes kind, not type. It exists because "fitness function" had
come to mean both candidate scoring and architecture conformance, and
`HANDOFF.toon` had begun mixing both under one `fitness_functions` key.

The scheme is in force. `FitnessSignalId` in `modules/domain` is the type, and
`PhenotypeFitnessScorer`, `MutationOperatorPolicy` and `PhenotypeBridgeScorer`
build every identifier through it. The S-expression IR renders scope and name
as nested nodes, so a signal is a gate because of where it sits rather than
what it is called, and `EvolveMcpResponseSerializer` partitions on force rather
than on a name prefix. Legacy unscoped objective keys and `hard_gate_*` keys are
accepted on read and re-emitted canonically, so existing result maps can cross
the representation boundary without silently changing their role.

The severity classes below are not yet enforced anywhere.

## Severity classes

Invariant violations are partitioned by what the violation costs and whether it
can be undone. The same partition decides who may set a threshold, because the
less reversible the consequence the more human authority it warrants.

| Class | Test | Orders | Threshold set by |
|---|---|---|---|
| integrity | can we trust this measurement at all? | voids, does not rank | human only |
| safety | could this harm something outside the experiment? | 1st | human only |
| correctness | does it do the job? | 2nd | human, or quorum ratified |
| shape | is it well-formed? | 3rd | quorum, persisted, modifiable |

Integrity voids a run rather than ranking it. A candidate that rewrote the
script grading it, produced no evidence, realised nothing, or returned missing
or out-of-range objective scores has not made a statement about fitness; it has
reported that the measurement cannot be trusted. Such runs stay out of the
evolutionary archive and never become exemplars, whereas a measured failure is a
useful near miss and belongs there.

`process.invariant.layer_boundaries` is correctness rather than shape. The
module direction is Gradle-enforced and a violation genuinely fails
compilation; the package-level provider confinement is enforced by
`check-architecture-boundaries` under `project lint` rather than by the
compiler, because merging the adapter modules put the providers on one
classpath.

Comparison is lexicographic: worst class violated decides first, magnitude
within the class breaks ties. Magnitudes are never summed across classes, so no
exchange rate between incommensurable violations is ever needed.
