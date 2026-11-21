# Domain

The domain is agentic workflow evolution (`DOM-001`): a baseline workflow is
mutated, isolated as a Git candidate, evaluated with deterministic evidence and
then promoted or discarded.

The first living capability is
`specs/capabilities/CAP-001-mutation-fitness-loop.toon`.

## Known gaps

`RISK-002` records that a mutation contract declares hard gates the scorer does
not enforce. `CHG-014` gave `PhenotypeFitnessScorer` a contract-aware entry
point, and `CHG-019` put the accepted contract on the wired path. So a run where
the operator declares a contract now gates on its declared `required_evidence`
ids. The operator declares it, not the proposer, so the model does not choose
its own grading criteria.

What remains open: a run that declares no contract still reaches the contractless
entry point, which enforces only the structural gates. Model-emitted contract
envelopes are still future work.

`Q-007` records that canonical mutation IR preserves declared order for
set-like fields, so two contracts differing only in evidence order canonicalize
differently.

`RISK-003` (candidate worktree names colliding across runs) is obsolete.
`CHG-026` task `T4` gave each candidate a namespace of a run id plus its position
in the generation, so repeat runs and many live worktrees no longer collide. The
entry is kept to explain why names carry a run id.

`RISK-008` and `RISK-010` are open. `RISK-010`: a failing held-out case lowers
`task_success`, and promotion also needs the score to reach 0.80, so one
held-out failure can discard a candidate that passed every gate. It awaits a
decision.

Gates fail closed. A declared behaviour case with no check evidence is scored as
failed rather than dropped, and a candidate whose realization changed no file is
rejected outright rather than being rewarded with parsimony 1.0 for the empty
diff (`CHG-003` task `T10`).
