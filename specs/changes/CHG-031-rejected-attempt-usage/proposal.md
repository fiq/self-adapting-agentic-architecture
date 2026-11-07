# CHG-031: A generation records what a rejected attempt cost

## Why

In each of the first two live generations (2026-11-07), one of three asks was
answered by the model and then rejected by validation. Its tokens were spent,
but `candidate_usage` is keyed by a candidate that never existed, so nothing
recorded them. Generation cost, which ADR-0006 depends on, is undercounted.

## Intent

Record the attempt's usage on its `generation_unevaluated` row, show it on the
no-evidence line, and keep "no usage known" distinct from zero.

## Non-goals

- usage for a failed single-candidate run;
- a price schedule;
- changing why attempts are rejected (CHG-030).

## Related knowledge

`ADR-0006`, `CHG-029`, `PAT-004`.
