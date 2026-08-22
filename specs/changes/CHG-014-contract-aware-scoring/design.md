# CHG-014 design

## What is wired today

```text
MutationEvaluationLoop
  proposes/validates  Mutation            (not MutationContract)
  calls               fitnessScorer.score(candidate, evidence)
                          |
                      PhenotypeBridgeScorer  implements FitnessScorer
                          |
                      PhenotypeFitnessScorer.score(Candidate, PhenotypeEvidence)
                          +-- structural fixed gates
                          +-- weights against DEFAULT_OBJECTIVES

MutationContractValidator.validate  <-- reached only from ConceptualCrossoverPolicy
```

There is no accepted `MutationContract` anywhere on that path, so this change
cannot thread one through it. That is the whole reason RISK-002 stays open.

## What this change adds

```text
PhenotypeFitnessScorer.score(Candidate, PhenotypeEvidence, MutationContract,
                             RequiredEvidenceResults)
    +-- structural fixed gates            (unchanged, still applied)
    +-- declared required_evidence gate   (new)
    +-- weights against the contract's declared objectives
```

The two-argument entry point stays exactly as it is. S8 pins its behaviour so it
cannot drift while it remains the wired path, and S9 asserts that it *is* still
the wired path, so the remaining gap fails a test if someone believes otherwise.

## The evidence channel

`PhenotypeEvidence.objectiveScores` is a `Map<String, Double>` — a measurement
channel. A declared evidence id needs an outcome and a diagnostic, not a number,
so it gets its own typed channel rather than being encoded as a score.

Rules, each pinned by a scenario:

- declared and absent from the channel is a discard (S1);
- declared and failing is a discard (S2);
- two results for one declared id, either failing, is a discard — fail wins, so a
  passing entry can never mask a failing one (S3). This mirrors how
  `PhenotypeBridgeScorer` already merges behaviour-case checks;
- undeclared results are recorded as inert non-gate entries in the existing
  `FitnessResult.objectives` map and cannot satisfy or weaken a declared gate
  (S5). `FitnessResult` has no separate audit field, and this change does not add
  one;
- an evidence id whose canonical key collides with a structural gate key is
  rejected rather than merged (S10). This is what preserves CON-002's rule that a
  gate result cannot be overwritten by evidence content. Write order does not
  preserve it: `FitnessResult` copies its map with `Map.copyOf`, which does not
  retain insertion order, so ordering is not something a caller can observe or a
  test can pin. What holds is the final value — structural gates are written
  after the caller's measured scores, so a caller-forged structural key is
  overwritten by the real gate outcome, and evidence ids can never reach those
  keys at all;
- a failing outcome records `0.0` against its id. Its diagnostic does **not**
  reach the result: `FitnessResult` carries only `Map<String, Double>`. The field
  is required at the input boundary so a caller supplying evidence must state what
  it observed, and task T8 tracks giving the discard reason an output carrier;
- declared-evidence gates are **additional to** the structural gates, never a
  replacement (S6). A candidate passing every declared id but producing an empty
  realization is still discarded.

S6 exists because an earlier draft's requirement said the scorer "decides against
what that contract declared, never against a fixed assumption about it", which
can be read as licence to drop the structural gates. That reading would let a
candidate with no checks, no behaviour cases and an empty realization promote.

## Characterisation tests come first

S8 and S9 are written before the contract-aware path exists, and are green on
write. S8 pins the contractless entry point's current behaviour; S9 pins that
`PhenotypeBridgeScorer` still calls it. Writing them at the end would mean a
regression introduced while adding the new path is caught after the fact, or
not at all.

S9's limits are worth stating precisely, because it is easy to claim more than it
proves. It pins that the `FitnessScorer` port carries no contract parameter, so
nothing outside the bridge can supply one, and it drives the bridge to confirm no
declared-evidence key reaches the audit map.

It does **not** prove which overload the bridge calls. A bridge that constructed
an empty `MutationContract` internally and called the four-argument overload
would behave identically and pass — but an empty contract declares no required
evidence and therefore enforces nothing, so that case is irrelevant to RISK-002.
The guarantee that matters is the port's: the loop cannot pass a contract.

It also would not catch a rewire swapping a different `FitnessScorer` in at
`EvolveRunner`. That belongs to the migration's own component coverage.

## Canonical emission

CON-002 requires fitness identifiers to carry the `subject.invariant.` /
`subject.objective.` scheme, and classifies "produced no evidence" as an
integrity violation that voids rather than ranks. Declared `required_evidence`
ids are bare strings today (`failing_case_reproduced`, `unit_tests_pass`). S7
pins that a declared-evidence gate is emitted through `FitnessSignalId` as a
canonical subject invariant, so the new gate joins the existing naming scheme
instead of introducing a second one.

Integrity is expressed as **voiding**, not as a severity field. CON-002 records
that its severity classes are not enforced anywhere yet, and `FitnessResult` has
no severity field, so a failing declared-evidence gate produces `DISCARD` rather
than a reduced weighted score. Saying "classified as an integrity outcome"
without this would imply a severity engine that does not exist.

## The objective set

The contract-aware path weights against the contract's declared objectives. This
is unreachable through an accepted contract, because
`MutationContractValidator.requireDeterministicObjectives` forces every accepted
contract onto its operator's defaults and every operator shares
`DEFAULT_OBJECTIVES`. Relaxing that is a recorded non-goal.

`PhenotypeFitnessScorerTest.everyOperatorSharesTheObjectiveSetTheScorerAssumes`
is kept for the same reason: while the wired path weights against
`DEFAULT_OBJECTIVES`, that test is the only thing standing between a per-operator
objective set and a silently wrong weighted score.

## What this deliberately does not decide

How `behavioral_safety` becomes variable, and how critical and non-critical
safety probes are split, is the next change. This one only makes a declared
evidence id capable of gating at all.
