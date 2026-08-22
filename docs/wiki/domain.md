# Domain

The domain is agentic workflow evolution (`DOM-001`): a baseline workflow is
mutated, isolated as a Git candidate, evaluated with deterministic evidence and
then promoted or discarded.

The first living capability is
`specs/capabilities/CAP-001-mutation-fitness-loop.toon`.

## Known gaps

`RISK-002` records that a mutation contract declares hard gates the scorer does
not enforce. `PhenotypeFitnessScorer` now has a contract-aware entry point that
does enforce them: declared `required_evidence` ids gate as canonical
`subject.invariant.<id>` integrity outcomes, in addition to the structural gates,
and weighting reads the contract's own objective set. That is `CHG-014`, and it
closes task `T4b`.

The wired path does not use it. `MutationEvaluationLoop` still proposes and
validates a `Mutation`, and the `FitnessScorer` port takes
`(Candidate, EvaluationEvidence)` and cannot carry a contract at all. So a
promoted candidate's evidence is still weaker than its contract implies, and
`RISK-002` stays open. The remaining migration is task `T4c`.

`Q-007` records that canonical mutation IR preserves declared order for
set-like fields, so two contracts differing only in evidence order canonicalize
differently.

`RISK-003` records that candidate worktree names derive only from the workflow
and mutation ids, so a repeat run of the same mutation fails instead of
evaluating a new candidate. It becomes blocking at the population slice, which
needs many live candidate worktrees at once.

Gates fail closed. A declared behaviour case with no check evidence is scored as
failed rather than dropped, and a candidate whose realization changed no file is
rejected outright rather than being rewarded with parsimony 1.0 for the empty
diff (`CHG-003` task `T10`).
