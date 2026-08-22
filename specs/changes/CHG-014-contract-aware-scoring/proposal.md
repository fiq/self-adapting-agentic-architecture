# CHG-014: Enforce the declared contract in the scorer

## Why

`MutationContractValidator` requires every contract to declare its operator's
hard gates and `required_evidence` items. `PhenotypeFitnessScorer.score` takes
only a `Candidate` and `PhenotypeEvidence`. It never receives the contract.

RISK-002 records the consequence: a `repair` contract declaring
`failing_case_reproduced` and `regression_case_added` can be realized with
neither and still promote on one passing behaviour case plus good objective
scores. The declared gate is descriptive, not enforced.

The same missing input causes a narrower gap. The scorer gates and weights
against the shared `MutationOperatorPolicy.DEFAULT_OBJECTIVES` because it cannot
know the operator. That is sound only while every operator shares one objective
set, which `PhenotypeFitnessScorerTest.everyOperatorSharesTheObjectiveSetTheScorerAssumes`
asserts — so giving any operator its own objectives currently fails the build
rather than silently producing a wrong weighted score.

## What

Two things, exactly as RISK-002 names them:

- a typed evidence channel keyed by the contract's `required_evidence` ids, so a
  declared id maps to an observed result rather than to nothing;
- a scorer entry point that receives the accepted `MutationContract` alongside
  the phenotype evidence, which also closes the objective-set gap.

## Why now

This is the prerequisite for making `behavioral_safety` a real signal. That
objective is the literal `1.0` today, and the intended fix keys a deterministic
safety suite off the already-declared `behavioral_safety_cases_pass` evidence id
(`MutationOperatorPolicy:45`). A declaration the scorer cannot see cannot gate
or score anything, so the safety suite cannot be built before this lands.

It is also the prerequisite for ranking. `ADR-0002` names the population slice —
several candidates per generation with ranking between them — as the missing
Layer-1 foundation. Ranking requires objectives that discriminate between
eligible candidates, and per-operator objectives require the scorer to know the
operator.

## Not this change

- making `behavioral_safety` variable; that is the next slice and depends on this
  one;
- wiring `:benchmarks` so `cost_latency_budget` has an evidence source;
- evaluating several candidates per generation, or ranking them;
- any LLM-produced judgement reaching a score, a gate or a promotion decision.

## Relates to

RISK-002, `CHG-002` task `T4b`, ARCH-001, CON-002, and the living capability
`specs/capabilities/CAP-001-mutation-fitness-loop.toon`, whose R1 requires that the
model never approves its own result.
