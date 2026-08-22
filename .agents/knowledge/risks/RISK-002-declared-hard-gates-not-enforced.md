---
id: RISK-002
type: risk
title: Declared contract hard gates are not enforced by the scorer
status: proposed
summary: A mutation contract declares hard gates such as required_evidence_present, but PhenotypeFitnessScorer enforces its own fixed gates and never sees the contract, so a declared gate can pass without the evidence it names.
owners:
  - architect
relates_to:
  - SYS-001
  - CON-001
  - Q-004
risks:
  - RISK-001
evidence:
  - modules/deterministic/src/main/java/com/dreamthought/saaa/deterministic/MutationContractValidator.java
  - modules/deterministic/src/main/java/com/dreamthought/saaa/deterministic/PhenotypeFitnessScorer.java
  - specs/changes/CHG-002-live-loop-policy/change.toon
review_after: 2026-10-26
---

# Declared Hard Gates Are Not Enforced by the Scorer

`MutationContractValidator` requires every contract to declare the operator's
hard gates (`deterministic_checks_pass`, `required_evidence_present`) and the
operator's `required_evidence` items. `PhenotypeFitnessScorer` takes only a
candidate and phenotype evidence. It enforces deterministic checks, required
behavior cases and required objective scores, but it has no contract input and
no evidence channel that maps a declared `required_evidence` id to an observed
result.

Consequence: a `repair` contract declaring `failing_case_reproduced` and
`regression_case_added` can be realized with neither, and still promote on one
passing behavior case plus good objective scores. The declared gate is
currently descriptive, not enforced.

The same missing contract input causes a second, narrower gap. `MutationContractValidator`
checks a contract's fitness objectives against its operator's defaults, but the
scorer gates and weights against the shared `DEFAULT_OBJECTIVES` constant because
it cannot know the operator. That is sound only while every operator shares one
objective set. `PhenotypeFitnessScorerTest.everyOperatorSharesTheObjectiveSetTheScorerAssumes`
asserts exactly that, so giving any operator its own objectives fails the build
rather than silently producing a wrong weighted score and a wrong promotion
decision.

Closing this needed two things that were outside the first policy slice:

- a typed evidence channel keyed by the contract's `required_evidence` ids;
- a scorer entry point that receives the accepted `MutationContract` alongside
  the phenotype evidence, which also closes the objective-set gap above.

**Both now exist.** CHG-014 added `RequiredEvidenceResult` and a four-argument
`PhenotypeFitnessScorer.score` that enforces declared `required_evidence` ids as
canonical `subject.invariant.<id>` integrity gates, in addition to the structural
gates, and weights against the contract's own objective set.

**This risk stays open regardless**, because the wired promotion path does not
use that entry point. `MutationEvaluationLoop` proposes and validates a
`Mutation`, not a `MutationContract`, and scores through the `FitnessScorer`
port, whose single method takes `(Candidate, EvaluationEvidence)` and therefore
cannot carry a contract at all. `MutationContractValidator` is reached only from
`ConceptualCrossoverPolicy`.

So the consequence this entry describes is unchanged: a `repair` contract
declaring `failing_case_reproduced` and `regression_case_added` can still be
realized with neither and still promote. Promotion evidence remains weaker than
the contract implies, and promoted candidates still need human inspection of the
evidence record.

`PhenotypeBridgeScorerTest.theWiredBridgeStillUsesTheContractlessEntryPoint`
asserts that gap, so it fails a test rather than being assumed.

Closing requires the remaining work: migrating `MutationEvaluationLoop` from
`Mutation` onto `MutationContract` and threading the accepted contract through
`FitnessScorer` and `PhenotypeBridgeScorer`. That is task `T4c` in
`specs/changes/CHG-002-live-loop-policy/change.toon`; `T4b` is the scorer side
and is delivered by CHG-014.
