# Fitness taxonomy and steered evolution

Date: 2026-08-04
Status: **draft for review.** Nothing here is implemented. No scoring code,
weight or threshold has been changed.

Captured from a design conversation following the README evidence pass
(`bd93d3a`), which found that four of five weighted objectives are constant for
any candidate clearing the hard gates.

**Scope warning.** This is a programme, not a single change. The nine numbered
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
5 behaviour cases failed, 2 methods over the complexity threshold. That is a
change to gate evidence, not only to scoring.

Pick magnitudes that are counts of the same kind of thing wherever possible.
"Lines over budget" is a poor magnitude for the reasons in section 3a, and
mixing incommensurable magnitudes reintroduces weights by the back door.

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

### 3a. Parsimony is actively harmful, not merely weak

`parsimony = 1 - linesChanged/maxLines` rewards the smallest edit that clears
the gates. The smallest edit that makes a check pass is frequently the hack: a
special case is fewer lines than fixing the abstraction, an early return is
fewer lines than handling the case. Parsimony is a gradient pointing at kludges.

It is also the **only objective that currently varies**, so the single axis on
which SAAA can distinguish two passing candidates is the one that prefers the
bodge. The audit finding is therefore worse than "four objectives are inert":
the one that works pulls the wrong way.

Two further defects:

**It measures the delta, not the artifact.** Long method is a smell because it
signals several responsibilities in one place, not because lines are
intrinsically costly. Counting changed lines measures the shadow of the symptom.

**It is representation-relative.** The same semantic change costs very different
line counts in Perl, Java and `workflow.txt`. This is Kolmogorov complexity's
invariance theorem in practice: description length is defined only up to a
constant depending on the description language, and for short programs that
constant dominates. Worse, terse and parsimonious come apart hardest in
expressive languages — a chained-regex one-liner is shorter *and* packs more
responsibilities into one place — so the metric's bias, not merely its scale,
varies by language.

**Resolution.** Split it along the shape/behaviour line rather than deleting or
blending:

- **Quality becomes an invariant.** Threshold-crossing, not any-increase: "no
  method crosses cyclomatic 15", not "complexity must never rise", because the
  correct fix sometimes adds branches and input validation must not be blocked.
  Binary, non-tradeable, in the violation aggregate.
- **Size survives as a graded objective**, and is now safe, because the quality
  gate has already refused the golf hack that size alone would have rewarded.

Do **not** blend size and quality into one score. `f(size, quality)` needs a
weight between them, which is the penalty-function exchange rate again: sprawl
buys tidiness, or brevity buys ugliness.

**Bounds do the constraining, and the layer already exists.**
`--max-lines` survives as a bound, not as a score denominator;
`DiffLineBudgetMutationValidator` uses it pre-realisation to cap blast radius.
Bounds need to be conservative, not fair across languages. Structure the bounds,
free the content.

`MutationBounds` is richer than a size cap. It is a capability envelope —
`maxFilesChanged`, `maxLinesChanged`, `publicApiChange`, `persistenceChange`,
`productionConfigChange` — and `MutationOperatorPolicy` already varies it per
operator: `EXPLORATORY_LEAP` gets 4 files and 160 lines against
`TARGETED_BEHAVIOR_CHANGE`'s 2 and 80, and `MODEL_ROUTING_CHANGE` 2 and 60. The
three permission flags are `false` for every operator today and are the natural
place to express blast radius that has nothing to do with size.

**Defect this exposes.** Parsimony penalises `EXPLORATORY_LEAP` for being
exploratory. The operator exists to produce bolder variation and its bounds
grant double the budget, but `PhenotypeBridgeScorer` divides by
`ScoringConfig.maxLinesChanged`, sourced from the CLI `--max-lines` (default 80)
with no reference to the operator's own bounds. A leap that spends its granted
160 lines scores near zero on the only objective that varies, so the fitness
function actively fights the search-posture design. Two disconnected notions of
"too big" that never consult each other.

This is a further argument for retiring size as a scored quantity: the bounds
layer already answers "how much change is appropriate for this kind of move",
per operator and declaratively. Parsimony was duplicating that answer badly and
contradicting it at the edges.

**Implementation caveat.** "Score the git diff" is not computable as stated;
cyclomatic complexity of a hunk is undefined because analysers work on whole
compilation units. What works is metric-on-file-before minus
metric-on-file-after, with findings attributed to changed lines. That is how
new-code analysis is done in practice, it avoids blaming a candidate for
pre-existing mess, and the attribution closes a gaming route — without it a
candidate improves its score by touching unrelated code to drag an average down.

### 3b. Shape is an invariant, behaviour is an objective

The principle that falls out of 3a. Tidiness, cohesion, complexity and boundary
conformance are gates. Whether the candidate does the job better is what gets
graded. Attempting to score shape reintroduces exchange rates between things
that should not be exchangeable, and cohesion metrics are noisy enough (LCOM
variants disagree with each other and with human judgement) that the number
would be perpetually argued while the boolean would not.

Corollary: if some part of "well-scoped" is irreducibly subjective, it can be a
lesson at `PROPOSED` authority. It cannot be a score.

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

### 9. Measures depend on the target kind

This conclusion arrived three separate times tonight and is probably the real
structural finding.

- Static analysis needs code. The L1 fixture evolves `workflow.txt`, which has
  no methods and no analyser.
- Line budgets mean different things in Perl, Java and a text file.
- Complexity thresholds are language-specific, and the tooling is too: PMD,
  Checkstyle and Error Prone give this for Java and nothing for a prompt file.

So the objective and invariant sets cannot be one global list. A workflow file,
a prompt and a Java method need different measures. `MutationContract` already
carries per-contract `objectives`, `hardGates` and `requiredEvidence`; what
blocks it is `MutationContractValidator` requiring equality with
`MutationOperatorPolicy.DEFAULT_OBJECTIVES`. Wiring the contract through
(section "What this depends on") is therefore a prerequisite for almost
everything else here, not an independent nicety.

### 10. What the structure costs

Recorded because a design note that only lists gains is not honest. Structure is
a prior, and a prior prunes the search space to what was already imagined.

**Expressiveness.** `MutationOperatorType` is closed at ten values and
`MutationContractValidator` rejects anything outside it, so a genuinely novel
change fitting no operator is unrepresentable. Evolution's power comes from
variation nobody anticipated; a schema is a written statement of what was
anticipated. This cost lands hardest exactly where the design wants to trust the
model's randomness.

**Distribution narrowing.** Requiring `(mutation (operator hill-climb) …)` turns
a generative model into a form-filler. Every structural slot narrows what it
samples from.

**Evaluation cost.** GP worked through many cheap evaluations. Violation
magnitudes, behavioural descriptors and predicate checks each add per-candidate
cost. Ten times dearer per generation buys a tenth of the search, and a thousand
crude candidates may beat a hundred careful ones.

**A new failure class.** A bad mutation scores 0.00 and is informative. A
malformed contract throws; `BenchmarkCommand` already special-cases
`mutation validation failed:` so it does not poison ablation runs. Structural
failures carry no fitness information but consume a generation.

**Map-territory drift.** The IR models the change; the commit is the change.
`MutationContract` already runs parallel to the wired `Mutation`, and
`Mutation.patch` is marked transitional with no migration scheduled, so two
representations that can disagree already exist.

**Ossification.** `schema_version: saaa-experiment-envelope-v2` exists because
schemas are migrations owed later.

**Mitigation.** Structure the bounds and the claims, not the content.
`(bounds (max-files 2) (max-lines 80))` constrains without prescribing and costs
no expressiveness. `(operator hill-climb)` prescribes a kind, and that is where
the tax falls. Structure the grading side hard, because determinism is the point
there; keep the generating side as loose as tolerable. This is the existing
principle read carefully: structure the approval, free the proposal.
`AGENTS.md` currently puts mutation IR and fitness predicates under one
S-expression rule; those two deserve splitting.

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
2. Wire the contract through to the scorer, closing `RISK-002`/`T4b`. This is a
   prerequisite for per-target-kind measures, so it gates most of what follows.
3. Specify the full measure set and selection algorithm together, per target
   kind.
4. Replace line-count parsimony: static-analysis quality gate as an invariant,
   size demoted to a tiebreak, `--max-lines` retained as a bound only.
5. Invariants report magnitude; adopt the Deb comparator.
6. Requirement type and predicate evaluation.
7. Archive with behavioural cells; gap selection.
8. Guidance capsule kinds and the blend policy.
9. Population, then NSGA-II when there is a front to sort.

## Open questions

- What is the violation magnitude for each invariant, and is it comparable
  across invariants without reintroducing weights by the back door? Counts of
  the same kind of thing compose; "3 failed cases" against "2 methods over
  threshold" may not.
- Which complexity thresholds, and who sets them? A threshold is a magic number
  of exactly the kind section 4 objects to, so it needs the same treatment:
  declared, versioned, frozen per experiment.
- Is size still worth keeping as a graded objective once the quality gate exists,
  or does the gate make it redundant? Section 3a keeps it as a tiebreak, but that
  is a judgement, not a finding, and the `EXPLORATORY_LEAP` defect argues
  against keeping it at all. If it is kept, it must normalise against the
  operator's own `MutationBounds`, not against a single CLI flag.
- Should the three `MutationBounds` permission flags (`publicApiChange`,
  `persistenceChange`, `productionConfigChange`) become active? They are `false`
  for every operator today, so they express no policy, and they are the obvious
  home for blast radius that is not about size.
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
