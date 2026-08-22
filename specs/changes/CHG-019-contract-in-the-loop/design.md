# CHG-019 design

## The gap this closes

```text
before:  loop --> FitnessScorer.score(Candidate, EvaluationEvidence)
                      |                       no contract can reach here
                      +-- PhenotypeBridgeScorer --> 2-arg PhenotypeFitnessScorer

after:   operator declares contract
             |
             +-- MutationContractValidator rejects it, or
             |
         loop carries it --> FitnessScorer.score(Candidate, EvaluationEvidence, Optional<MutationContract>)
                                 |
                             PhenotypeBridgeScorer --> 4-arg PhenotypeFitnessScorer
```

## Declared evidence is a named check

A `required_evidence` id names a check that must exist and pass. The mapping is
deliberately boring: the loop already collects `CheckEvidence` by name, so a
declared id becomes a `RequiredEvidenceResult` by looking for a check of that
name. Absent produces a failing result rather than no result, because CHG-014
already treats a declared id with no observed result as a discard and the two
paths should agree.

This means a declared gate is enforced against evidence the run already produces,
rather than requiring a new pipeline. It also means the contract's vocabulary and
the operator's script names must match, which is a real coupling recorded as a
risk. A mismatch discards rather than silently passing, which is the safe
direction.

## The port widens

`FitnessScorer.score` gains an `Optional<MutationContract>`. That is the seam
CHG-014's `theWiredBridgeStillUsesTheContractlessEntryPoint` asserts cannot carry
a contract, so that test is not deleted but rewritten: it asserted the gap, and
the gap is what this change removes. What replaces it asserts the opposite
property — that a contract supplied by the loop reaches the scorer unchanged.

Deleting it silently would remove the only thing that made RISK-002's remaining
gap visible, at exactly the moment the gap changes shape.

## Why the operator declares it

`ARCH-001` says a model may propose or repair but may not validate, score or
promote. A model that declares its own `required_evidence` chooses the criteria
it will be judged against, which is the same failure one level up. The operator
declaring bounds and required evidence, and the model producing a realization
within them, keeps the declaration outside the thing being judged.

The contract still bounds only the model. An operator can declare something
trivially satisfiable, and nothing here prevents that; the guarantee is that
whatever was declared is enforced, not that the declaration was demanding.
