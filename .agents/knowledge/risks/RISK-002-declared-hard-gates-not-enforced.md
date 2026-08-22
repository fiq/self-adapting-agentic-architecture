---
id: RISK-002
type: risk
title: Declared contract hard gates are not enforced by the scorer
status: canonical
summary: A declared required_evidence id now gates a live promotion when an operator declares a contract. A run that declares none still reaches the contractless entry point, which enforces only the structural gates and weights against the shared objective set.
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
reviewed_at: 2026-08-23
review_after: 2026-10-26
---

# Declared Hard Gates Are Not Enforced by the Scorer

Historical statement of the problem, kept for context. It described the state
before CHG-019; see the current position below.

`MutationContractValidator` requires every contract to declare the operator's
hard gates (`deterministic_checks_pass`, `required_evidence_present`) and the
operator's `required_evidence` items. `PhenotypeFitnessScorer` takes only a
candidate and phenotype evidence. It enforces deterministic checks, required
behavior cases and required objective scores, but it has no contract input and
no evidence channel that maps a declared `required_evidence` id to an observed
result.

Consequence: a `repair` contract declaring `failing_case_reproduced` and
`regression_case_added` can be realized with neither, and still promote on one
passing behavior case plus good objective scores. The declared gate was descriptive, not
enforced.

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

**The consequence this entry described is closed as of 2026-08-23, by CHG-019.**
The entry stays canonical because a narrower residue remains, described below. An operator declares the contract for a run with
`--operator`, the loop carries it, and the `FitnessScorer` port takes it, so a
declared `required_evidence` id gates a live promotion. Each id names a check that
must exist and pass; absent evidence is not passing evidence.

The consequence this entry described no longer holds. A `repair` contract
declaring `failing_case_reproduced` and `regression_case_added` cannot now be
realized with neither and still promote — `EvolveContractAcceptanceTest` drives
the real CLI to show it, and mutating the resolver so absent evidence passes makes
that test fail end to end.

What remains, recorded rather than closed with it: a run that declares no contract
still reaches the contractless entry point with the existing structural gates, and
that path still weights against the shared `DEFAULT_OBJECTIVES`. The
`everyOperatorSharesTheObjectiveSetTheScorerAssumes` tripwire therefore stays. Its
retirement condition is that the contractless entry point no longer exists, not
that the wired path became contract-aware, which is how CHG-014 first recorded it.

Contracts are operator-declared. Model-emitted TOON envelopes remain future work
under `CHG-002` `T3d`, which is unaffected.


`T4b` and `T4c` in `specs/changes/CHG-002-live-loop-policy/change.toon` are both
delivered. What remains is not a migration but a scope question: contractless runs
are the default, and nothing declares evidence for them.
