# CHG-022 — Design

## The shape of the problem

Four of the five objectives are pinned at 1.0 for any candidate that promotes, and it is not an
accident of configuration. It follows from what the gates require.

```
gate: required_behavior_cases   every declared case PASSED
  ⇒ task_success = passedFraction(cases) = 1.0        0.40 pinned

gate: deterministic_checks      every check PASSED
  ⇒ nothing TIMED_OUT
  ⇒ reliability = allChecksRan  = 1.0                 0.20 pinned

undeclared ⇒ cost_latency_budget = 1.0                0.20 pinned by default
undeclared ⇒ behavioral_safety   = 1.0                0.10 pinned by default
                                                      ─────
                                                      0.90 floor, threshold 0.80
```

Everything eligible promotes, ranked only by `parsimony` at 0.10.

An objective that restates its own gate cannot discriminate, because the gate has already removed
every candidate that would have scored differently. Fixing it means finding evidence the gate does
not consume.

## Why repeated runs

Consistency is exactly that evidence. The gate consumes one result per case: pass or fail. It says
nothing about whether that result is stable. Running the same script again produces evidence the gate
never saw, and a candidate that passes eight of ten runs is meaningfully worse than one passing ten
while remaining eligible under an unchanged gate.

The mechanism already existed. CHG-021 built withholding so a safety probe could grade rather than
gate; repeated runs use the same seam. The canonical run gates, the repeats grade, and every run
stays in the record.

## The naming decision

A repeat carries the same command and a distinct name, `unit_tests_pass.run2`.

Same command, because the point is to run the same check again — a different command would measure
something else. Distinct name, because evidence is keyed by name and a repeat sharing the canonical
name would be merged into it by the fail-wins rule, losing exactly the per-run detail this change
exists to collect.

The consequence is that a declared case ending `.run<digits>` would collapse onto a different base
name when runs are grouped, so two distinct cases would score as one. Declaring such a name is
rejected rather than silently mis-scored.

## The mistake worth recording

The first implementation made `reliability` the pass fraction across runs unconditionally. At one run
per case that is precisely `task_success` — the pass fraction of the same behaviour cases — so the
objective became a second copy of another, double-weighting an outright failure while measuring
nothing new.

The golden corpus caught it: two entries changed score for a reason the change did not claim. At a
single run the objective now keeps its previous meaning, timeout rule included, and only starts
measuring consistency when there is more than one run to compare.

This is the same class of error the change is fixing. An objective that duplicates another is as
useless for ranking as one that restates a gate; both add weight without adding information.

## What is still pinned

`task_success` remains 1.0 for every promoted candidate, for the structural reason above. Fixing it
needs an evidence source broader than the gated cases — a held-out set the gate does not consume — and
that is a larger change than this one.

Undeclared objectives still contribute 1.0, which means a run that measures nothing scores well on
what it did not measure. Renormalising the sum over only the objectives a run actually measured is a
separate and worthwhile correction; it is not attempted here because it changes the meaning of every
recorded score.
