# CHG-014: Make the scorer capable of enforcing a declared contract

## Why

`MutationContractValidator` requires every contract to declare its operator's
hard gates and `required_evidence` items. `PhenotypeFitnessScorer.score` takes
only a `Candidate` and `PhenotypeEvidence`. It never receives the contract.

RISK-002 records the consequence: a `repair` contract declaring
`failing_case_reproduced` and `regression_case_added` can be realized with
neither and still promote on one passing behaviour case plus good objective
scores. The declared gate is descriptive, not enforced.

## What this change actually delivers

The scorer gains an entry point that receives a `MutationContract` and a typed
channel of observed results keyed by its declared `required_evidence` ids, and
enforces those ids **in addition to** the structural fixed gates it already
applies.

## What this change does not deliver, and why RISK-002 stays open

The wired promotion path does not use it. `MutationEvaluationLoop` proposes and
validates a `Mutation`, not a `MutationContract` (`MutationEvaluationLoop:160`),
and calls `fitnessScorer.score(candidate, evidence)` (`:206`) through
`PhenotypeBridgeScorer`, which invokes the two-argument
`PhenotypeFitnessScorer.score`. `MutationContractValidator.validate` is reached
only from `ConceptualCrossoverPolicy` — never from the loop. There is no accepted
`MutationContract` in the live path to pass to a contract-aware scorer.

Migrating the loop from the transitional `Mutation` / `MutationValidator` /
`FitnessScorer` stack onto `MutationContract` is a separate, already-recorded
piece of work. Until it lands, **RISK-002 remains open** and promoted candidates
still need human inspection of the evidence record, exactly as that risk says.

Scenario S9 exists so this gap is asserted by a test rather than assumed, and
task T7 explicitly forbids closing RISK-002 in this change.

An earlier draft of this proposal was titled "Enforce the declared contract in
the scorer" and claimed to deliver "two things, exactly as RISK-002 names them".
An independent review established that both mechanisms would exist at the
component level while neither reached the path RISK-002's consequence describes.
The title and intent are corrected rather than the scope widened, because
widening would put the whole stack migration into one change.

## The objective-set gap is bounded here

The same missing input means the scorer weights against the shared
`MutationOperatorPolicy.DEFAULT_OBJECTIVES` because it cannot know the operator
(`PhenotypeFitnessScorer:73,84`). The contract-aware path can weight against the
contract's own objectives.

That is reachable only in scorer-level tests today.
`MutationContractValidator.requireDeterministicObjectives` rejects any contract
whose objectives differ from its operator's defaults, and every operator shares
`DEFAULT_OBJECTIVES`, so an *accepted* contract can never declare a different
set. Relaxing that rule is a recorded non-goal here and needs its own decision.

For the same reason
`PhenotypeFitnessScorerTest.everyOperatorSharesTheObjectiveSetTheScorerAssumes`
is **kept**. An earlier draft retired it as a tripwire. It is load-bearing while
the wired path still weights against `DEFAULT_OBJECTIVES`: it is the only guard
that catches an operator being given its own objectives, which on the wired path
would silently produce a wrong weighted score. It can be retired when the wired
path becomes contract-aware, not before.

## Why sequenced first

`behavioral_safety` is the literal `1.0`. The intended fix keys a deterministic
safety suite off the already-declared `behavioral_safety_cases_pass` evidence id
(`MutationOperatorPolicy:45`). A declaration the scorer cannot see cannot gate or
score anything, so the safety suite cannot be built before the channel exists.

## Relates to

RISK-002, `CHG-002` task `T4b`, ARCH-001, CON-002, and the living capability
`specs/capabilities/CAP-001-mutation-fitness-loop.toon`, whose R1 requires that
the model never approves its own result.
