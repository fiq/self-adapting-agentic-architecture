# CHG-033: A fixture with more than one good answer

## Why

The toy fixture has one correct edit. Every passing candidate scores the same,
so a generation's spread is zero by construction and cannot show whether
ranking helps (ADR-0002, First measurement).

## Intent

`fixtures/multi-answer-workflow`: several distinct edits pass every required
case, and held-out cases reward different improvements, so good candidates
score differently. Enough cases that one held-out failure still promotes
(RISK-010).

## Non-goals

- A live run (a measurement after merge).
- Scorer or RISK-010 changes.

## Related knowledge

`ADR-0002`, `RISK-010`, `PAT-004`.
