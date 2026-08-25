# CHG-024: Held-out behaviour cases so `task_success` can discriminate

## Why

Every promoted candidate scores exactly `1.0` on `task_success`, which carries
`0.40` of the weighted sum. The reason is structural: `task_success` is the pass
fraction of the declared behaviour cases, and the `required_behavior_cases` gate
requires every one of them to pass. Clearing the gate therefore awards full
marks by definition.

Among promoted candidates the rest of the weight is thin. `cost_latency_budget`
(0.20) has no evidence source on the CLI path, `behavioral_safety` (0.10) is 1.0
unless probes are declared, and `reliability` (0.20) is 1.0 for any promotion
with no timed-out check. So ranking two promoted candidates moves on parsimony's
`0.10` alone: the smaller diff wins, which is not a quality judgement.

This blocks the population slice. `ADR-0002` records the revisit trigger
"population slice ships but ranking is not measurably useful → the direction is
wrong", and shipping population onto this scorer walks into it by construction.

## Intent

Add held-out behaviour cases: cases that run, are recorded, must be executed,
but do not gate. They lower `task_success` when they fail without discarding the
candidate, so a promoted candidate can score below `1.0` and two promoted
candidates become comparable.

Carry a `ScoringContext` fingerprint on every result so scores produced under
different objective sets, weights or scoring configuration are never silently
compared.

## Why this is not the same as safety probes

Probes and reliability repeats are withheld from the `deterministic_checks` gate
by `nonGatingCheckNames`. That mechanism cannot serve held-out cases, because
there are two behaviour-judging gates reading different collections:
`deterministic_checks` reads `gatingChecks()`, which is filtered, and
`required_behavior_cases` reads `behaviorCases()`, which is not. `task_success`
is computed from that same unfiltered list.

Probes never collide with this because they are never in `behaviorCaseNames`.
Held-out cases are the one case where "withhold from the gate" and "feed the
objective" land on the same list, so the change needs a gating-only view of
behaviour cases rather than another entry in `nonGatingCheckNames`.

## Non-goals

- population evaluation or ranking, which is `CHG-025`;
- changing the promotion threshold, the weights, or which cases gate;
- migrating local experiment data — it is derived and is rebuilt.

## Scope change: renormalisation was a non-goal and is no longer

This change originally excluded renormalising the weighted sum, on the grounds
that it changes the meaning of every recorded score and therefore needed its own
change.

`ScoringContext`, added here, is what removed that objection. It fingerprints the
probe set and the budget map, so scores produced under different measurement
configurations are already refused for comparison rather than silently mixed —
the precise reason the work was deferred. Deferring it further would have meant
leaving a live defect, one this change's own documents kept citing as a
cautionary example, while shipping the mechanism that made it safe to fix.

It is folded in rather than split out because it shares this change's subject:
an objective's contribution must reflect what was measured.

## Related knowledge

`ADR-0002`, `CON-002`, `RISK-002`, `RISK-007`, `PAT-004`, `Q-004`.
