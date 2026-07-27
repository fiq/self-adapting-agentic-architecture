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
