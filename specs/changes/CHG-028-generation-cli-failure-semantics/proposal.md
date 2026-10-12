# CHG-028: Generation CLI failure semantics

## Why

`--candidates` exposed four ways the CLI described or recorded failure
differently from the single-candidate path. A developer can read the console,
but an automation script needs the exit code, journal and error presentation to
mean the same thing.

## Intent

Make a completed generation with no evidence fail clearly, leave a durable
journal entry when a generation aborts, normalise runtime error presentation at
the top-level command line, and keep ranking columns readable for real ids.

## Non-goals

- adding a ledger row for an aborted generation;
- changing single-candidate failure semantics;
- adding verbose stack-trace controls.

## Related knowledge

`RISK-009`, `PAT-004`, `RISK-003`.
