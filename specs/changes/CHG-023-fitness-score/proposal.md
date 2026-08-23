# CHG-023: Keep fitness magnitude and decision inseparable

## Why

CHG-021 retained a discarded candidate's useful near-miss magnitude, but left it
beside a separate decision field. A later consumer could therefore rank a
discarded `0.95` above a promoted `0.85` by selecting the number alone. Stored
magnitudes also lost precision before the archive sorted them.

## Intent

Represent a candidate's raw magnitude and promotion decision as one comparable
domain value. Store the raw magnitude; format it to two decimal places only in
the console report.

## Non-goals

- population selection or new promotion rules;
- migration or compatibility for local experimental SQLite data;
- changing the promotion threshold or its raw-value comparison.

## Related knowledge

CON-002, RISK-007 and PAT-004.
