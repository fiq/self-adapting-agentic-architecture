# CHG-014 design

## The gap

```text
today:   MutationContractValidator --accepts--> MutationContract
                                                      |
                                                      X  (never passed on)
                                                      |
         PhenotypeFitnessScorer.score(Candidate, PhenotypeEvidence)
                 |
                 +-- enforces its own fixed gates
                 +-- weights against MutationOperatorPolicy.DEFAULT_OBJECTIVES

after:   MutationContractValidator --accepts--> MutationContract ----+
                                                                     |
         PhenotypeFitnessScorer.score(Candidate, PhenotypeEvidence, MutationContract)
                 |
                 +-- enforces the contract's declared required_evidence ids
                 +-- weights against the contract's own objective set
```

## The evidence channel

`PhenotypeEvidence` carries `objectiveScores` as a `Map<String, Double>`, which
is a measurement channel, not an outcome channel. A declared evidence id needs
an outcome and a diagnostic, not a number, so it gets its own typed channel
rather than being encoded as a score.

An id declared by the contract and absent from the channel is a discard. An id
present in the channel but not declared is recorded and ignored for gating —
`CON-002` already establishes that gate outcomes win in the audit record, and
the same rule applies here: observed evidence cannot invent a gate the contract
did not declare, and cannot satisfy one it did.

## Why the objective-set fix comes with it

`PhenotypeFitnessScorerTest.everyOperatorSharesTheObjectiveSetTheScorerAssumes`
currently asserts that every operator shares `DEFAULT_OBJECTIVES`. That test is
a tripwire, not a requirement: it exists so that giving an operator its own
objectives fails the build rather than silently producing a wrong weighted
score. Once the scorer receives the contract it can weight against the
contract's own objectives, and the tripwire stops being load-bearing.

Retiring it is part of this change rather than a follow-up, because leaving it
in place would keep asserting a constraint the change exists to remove.

## Transitional entry point

The existing two-argument `score` stays until every caller supplies a contract.
That is a deliberate, recorded compromise: the parallel `Mutation` /
`MutationValidator` / `FitnessScorer` stack is still the wired one, and this
change does not migrate it. S6 pins the old behaviour so the transitional path
cannot drift while it exists, and the risk list records that a caller left
unmigrated keeps the weaker guarantee.

## What this deliberately does not decide

Whether `behavioral_safety` is scored by a deterministic safety suite, and how
critical and non-critical safety probes are split, is the next change. This one
only makes a declared evidence id capable of gating at all.
