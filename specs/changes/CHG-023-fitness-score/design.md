# CHG-023 design

`FitnessScore` owns two facts that must travel together: a raw `BigDecimal`
magnitude and the `PROMOTE`/`DISCARD` decision. Its natural ordering is worst to
best: decision first, then magnitude. Archive selection reverses that one
ordering; it cannot construct a primitive score comparator by accident.

`FitnessResult` and `EvolutionaryMemoryRecord` now carry `FitnessScore`, not a
number and decision as separate components. SQLite and the reviewable experiment
envelope rename their magnitude columns/field to `raw_magnitude` and write the
unrounded value. The local ledger is disposable, so old columns and envelope
versions are replaced rather than migrated or read through a shim.

Superseded by CHG-024: the decision is now taken from the exact decimal
magnitude, because renormalising made a separately accumulated double disagree
with it at the threshold. As at CHG-023 the scorer calculated the weighted sum in
`double` because the objective
functions are doubles, and turns that finite result into `BigDecimal` once when
forming `FitnessScore`. The gate and `0.80` threshold remain exactly as before.
`ConsoleReporter` is the presentation boundary that renders the raw magnitude
to two decimal places.

## What the type does not do

An earlier draft of this change claimed the unsafe comparison had been made
unrepresentable. That was wrong, and a review found it written in this very branch:
`BenchmarkCommand` splits a `FitnessScore` back into a bare `double` and a `boolean`,
and `RetrievalAblationRunner` then took the maximum magnitude across all attempts,
including rejected ones — reporting a retrieval mode's best fitness as a number
belonging to a candidate it had discarded.

`rawMagnitude()` has to be public, because arithmetic on the magnitude is legitimate:
a delta against a baseline, an average across attempts, a benchmark summary. A type
that forbade reading the number would forbid those too.

So the honest claim is narrower. The type makes the correct ordering the default and
the incorrect one visible: `Comparator.comparing(x::rawMagnitude)` now reads as a
deliberate act at the call site rather than as the only thing available. Ordering that
ignores the decision stays a review concern.

The ablation summary is fixed to rank an accepted attempt above every rejected one,
and `RISK-007` keeps the general problem open.
