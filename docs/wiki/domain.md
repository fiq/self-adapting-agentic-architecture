# Domain

The domain is agentic workflow evolution (`DOM-001`): a baseline workflow is
mutated, isolated as a Git candidate, evaluated with deterministic evidence and
then promoted or discarded.

The first living capability is
`specs/capabilities/CAP-001-mutation-fitness-loop.toon`.

## Known gaps

`RISK-002` records that a mutation contract declares hard gates the scorer does
not enforce, because `PhenotypeFitnessScorer` never receives the contract. The
same missing input means scoring reads the shared objective set rather than the
operator's. Both close together under task `T4b`; until then a promoted
candidate's evidence is weaker than its contract implies.

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
