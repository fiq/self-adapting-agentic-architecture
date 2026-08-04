# Fitness taxonomy and steered evolution

Date: 2026-08-04
Status: **draft for review.** Nothing here is implemented. No scoring code,
weight or threshold has been changed.

Captured from a design conversation following the README evidence pass
(`bd93d3a`), which found that four of five weighted objectives are constant for
any candidate clearing the hard gates.

**Scope warning.** This is a programme, not a single change. The eight numbered
items under Sequencing are separately shippable and several are independently
valuable; step 1 changes no behaviour at all. Each needs its own `CHG-` spec and
implementation plan. Do not attempt this as one branch.

## Problem

Three distinct concepts share two names, and one concept has no name at all.

| Concept | Current name | Where |
|---|---|---|
| scoring a candidate | "fitness function" | `PhenotypeFitnessScorer` |
| conformance of SAAA itself | "fitness function" | `check-architecture-boundaries` |
| a JMH timing measurement | "benchmark" | `JmhBenchmarkRunner` |
| an evaluation suite | "benchmark" in the GP literature | `GoldenCorpus`, ablation corpus |
| what a round is trying to achieve | *none* | nothing |

`HANDOFF.toon`'s `fitness_functions:` key already mixes candidate scoring,
architecture conformance and CI configuration under one heading, so the
collision has leaked into state.

`docs/wiki/glossary.md` defines eleven terms and neither of the two most easily
misread.

## Decisions taken

### 1. Naming: semantic prefixes on two axes

Reuse the vocabulary the ledger envelope already carries
(`subject_repository_id` / `process_repository_id`, `RepositoryRole`) rather
than inventing a parallel scheme.

```
subject.invariant.behaviour_cases         must hold on the candidate
subject.invariant.non_empty_realization
subject.objective.parsimony               compounds on the candidate
process.invariant.layer_boundaries        must hold on SAAA itself
```

The prefix encodes *whose* property and *which force*. This is the Apps
Hungarian intent, encoding kind rather than type. A naming check in
`check-architecture-boundaries` rejects an id that does not parse, so the
collision cannot recur silently.

"Benchmark" is reserved for the measurement instrument. Evaluation suites are
"corpora", which is what the repository already calls them.

### 2. Aggregation: Deb feasibility rules, not a penalty function

Deb (2000), *An efficient constraint handling method for genetic algorithms*.
Two aggregates, never summed together, joined by a comparator.

```
violation(c) = Σ magnitude of each unsatisfied invariant   # meaningful only when > 0
quality(c)   = compounded graded objectives                # meaningful only when violation = 0

compare(a, b):
  both feasible   -> higher quality wins
  one feasible    -> the feasible one wins, always
  both infeasible -> lower violation wins
```

There is no penalty coefficient, therefore no exchange rate between "broke a
layer boundary" and "scored well on parsimony". The violation aggregate exists
to rank *infeasible* candidates against each other, which gives a total order
over the whole population without letting violation offset quality.

That ordering is what makes near-miss candidates useful as exemplars. Today
every gate failure collapses to `0.00 / DISCARD` and the information is lost.

**Cost:** invariants currently report booleans. Ranking needs *magnitude* — 3 of
5 behaviour cases failed, 12 lines over budget, 2 boundary violations. That is a
change to gate evidence, not only to scoring.

### 3. Graded objectives: specify the set now, stage the algorithm

Weighted sums are known to be poor (they cannot reach concave regions of a
Pareto front and the weights are arbitrary). The literature answer is
non-dominated sorting with crowding distance (NSGA-II, Deb et al. 2002), or
Chebyshev scalarisation to keep a single number.

Specify the measure set and the selection algorithm **together**, because
selection constrains measurement: Deb's rules need violation magnitudes, NSGA-II
needs comparable per-objective values. The four constant objectives are not an
implementation slip; nobody decided what they should measure. Patching them
individually would produce five ad-hoc measures that were never designed as a
set, which is how the current state arose.

Stage the *implementation* so the loop stays runnable, but do not stage the
*specification*.

### 4. Weights: LLM-proposed and frozen now, calibrated later

Determinism of a comparison survives LLM-authored weights if and only if the
weight vector is fixed before any candidate exists and is identical across every
candidate being compared. Scoring stays a pure function of evidence; only the
authorship of the function changed. A proposed weight set with recorded
rationale is more auditable than the current unjustified constants.

Weights that move per-candidate, or are adjusted after seeing results, are
self-approval and are excluded.

```
scoring_policy:
  id: scoring-v3                            # hashed into every ledger envelope
  base:       { task_success: 0.40, ... }   # the prior
  adjustable: { parsimony: [0.05, 0.20] }   # declared envelope for proposals
  floors:     all objectives >= 0.05        # nothing can be zeroed
  frozen_at:  experiment start
```

A fitness number is comparable to another only under the same
`scoring_policy_id`, the discipline already applied to
`retrieval_configuration_id` and `memory_policy_id`.

**Staging.** Weights are a calibration problem, not a reasoning problem. The
LLM's real value is proposing *what to measure*. Fit weights against labelled
outcomes once enough exist; the ledger accumulates them as a side effect of
running experiments.

**Blocker, recorded rather than assumed away:** `GoldenCorpus.java` cannot serve
as the calibration set. Its nine entries have expected verdicts derived from the
current weights (`promoteAtExactThreshold`, `discardJustBelowThreshold` are
constructed against 0.80), so fitting to it is circular. Calibration needs
independent labels: candidates a human judged better or worse without seeing the
score. That corpus does not exist and building it is real work.

### 5. Gaps drive the next round: an elite archive

What the conversation described is MAP-Elites (Mouret & Clune, 2015), which
`lineage-novelty-v1` has already half-invented — "distinct failure fingerprints,
evidence-novel representatives and a deterministic exploration reservoir" is an
archive of elites binned by behaviour. A gap is an empty or weak cell.

Three separable knobs, which fail differently and should not be conflated:

```
what to improve   <- gap/cell selection    from the archive
how to attempt it <- operator selection    bandit over the closed operator enum
what to build on  <- exemplar sampling     which elites enter the context
```

Operator selection is adaptive operator selection credited by which operator
recently produced accepted candidates. Exemplar sampling is what FunSearch
(Romera-Paredes et al., *Nature* 2024) does: sample the archive, sort, put the
best few in the prompt.

**Bin by behaviour, not by score.** Cells defined by score cause steering to
optimise the scoreboard. Cells defined by what candidates do differently give
diversity that resists Goodhart.

**Steering increases determinism's share.** Today the proposer receives a vague
`--task` string and free rein. Under this design deterministic code selects the
target, the operator and the exemplars from recorded evidence, and the model only
realises the brief it is handed. It chooses neither its assignment nor its grade.

**Dependency:** gaps cannot be detected on axes that do not vary. With four of
five objectives constant, every eligible candidate looks identical on four
dimensions. Steering is downstream of the measure work in sections 2 and 3.

### 6. A requirement is a new type

| Role | Says | Lives in |
|---|---|---|
| Requirement | what must get better | **nothing — no such type** |
| Measure | whether it did | `PhenotypeFitnessScorer` |
| Exemplar | how others moved that way | `ParentTrait` |

Two of three are built. The closest thing to a requirement is the free-text
`--task` string, typed by a human with no connection to any observed gap. The
loop measures and demonstrates but never states what it is trying to achieve in
a form anything can check.

**A requirement is a predicate over the fitness result the candidate has not
produced yet.** For example: `subject.objective.parsimony` must exceed 0.6 with
no invariant regressing. Deterministic to derive from the archive, deterministic
to check afterwards, and unforgeable by the proposer because it is fixed before
the proposal exists. This belongs in the S-expression policy-predicate slot
`AGENTS.md` already reserves for "fitness gates and deterministic policy
predicates".

### 7. Exemplars are not crossover

`ConceptualCrossoverPolicy`'s own docstring says it "recombines lessons, not
diffs", and `ParentTrait` is prose anchored to an evidence id. That is few-shot
demonstration, not crossover in Holland's sense. The nearest literature is
estimation-of-distribution algorithms: build a model of what good solutions look
like and sample from it, which is what an LLM given exemplars does implicitly.

The name matters because it changes the design questions. "Crossover" prompts
thinking about combining two genomes. "Exemplar" prompts the right questions:
how many, how diverse, and are they representative of the intended direction.

Rename deferred to implementation; recorded here so the decision is not lost.

### 8. Guidance kinds differ by authority, and the mix is a retrieval policy

Requirements, exemplars, lessons and strategy framing are all context handed to
the proposer. They are not exclusive and the blend should vary per round.

| Kind | Derived by | Authority | Licenses |
|---|---|---|---|
| Requirement | deterministic, from gaps | binding | must satisfy |
| Measured comparison | deterministic, from evidence | `OBSERVED` | fact, cite it |
| Lesson | inductive leap over comparisons | `PROPOSED` | should consider |
| Strategy framing | policy, from stalled progress | advisory | try differently |

A lesson can come from any evidence source, not only from parent candidates:
tests, benchmarks and scores all produce them. Worked example: `_mean` is faster
at revision B than revision A, and neither validates tainted input. No exemplar
carries that finding, because the answer is in neither parent. It has to be
stated.

The inference boundary: deterministic code emits the *comparison* with citations
(`cost_latency_budget` 0.71 at B vs 0.44 at A, `taint-check` absent in both).
Generalising from that is an inductive step and earns lower authority than the
numbers beneath it, which is what `EvidenceAuthority` already models and what the
proposer prompt already asserts: *"Canonical evidence outranks proposed or stale
knowledge."*

**No new subsystem is needed for the mix.** Guidance kinds are capsule kinds
(`EvidenceSubject.kind` is open), and the blend is a context-assembly policy,
which is what `EvidenceCapsuleCompiler` and `RetrievalBundle` already are. The
blend is versioned by `retrieval_configuration_id`, so a result stays
attributable to the guidance that produced it.

## What this depends on

`RISK-002` / task `T4b`. `PhenotypeFitnessScorer` never receives the
`MutationContract`, so a contract can declare a gate that is never enforced, and
`MutationContractValidator` only accepts objectives equal to the defaults. The
declarative structure exists in the domain (`MutationContract.hardGates`,
`.objectives`, `.requiredEvidence`) and is rendered to S-expression IR by
`MutationContractCanonicalizer`, but is not wired. Most of "make the fitness
specification declarative" is finishing this, not inventing it.

## Sequencing

1. Taxonomy: glossary entries, prefix scheme, naming check. No behaviour change.
2. Wire the contract through to the scorer, closing `RISK-002`/`T4b`.
3. Specify the full measure set and selection algorithm together.
4. Invariants report magnitude; adopt the Deb comparator.
5. Requirement type and predicate evaluation.
6. Archive with behavioural cells; gap selection.
7. Guidance capsule kinds and the blend policy.
8. Population, then NSGA-II when there is a front to sort.

## Open questions

- What is the violation magnitude for each invariant, and is it comparable
  across invariants without reintroducing weights by the back door?
- What are the behavioural descriptors for archive cells? They must be
  observable without being score-derived.
- Who builds the labelled corpus for weight calibration, and from what?
- Does the requirement predicate live on `MutationContract`, or in a new type
  that precedes it? A contract is authored by the proposer; a requirement must
  not be.
- Does `process.invariant.*` gate a candidate, or only SAAA's own CI? A
  candidate evolving a file in this repository can break a layer boundary, and
  today nothing stops it unless the operator declared a behaviour case that runs
  `project lint`.
