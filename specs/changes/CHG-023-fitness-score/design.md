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

The scorer still calculates the weighted sum in `double` because the objective
functions are doubles, and turns that finite result into `BigDecimal` once when
forming `FitnessScore`. The gate and `0.80` threshold remain exactly as before.
`ConsoleReporter` is the presentation boundary that renders the raw magnitude
to two decimal places.
