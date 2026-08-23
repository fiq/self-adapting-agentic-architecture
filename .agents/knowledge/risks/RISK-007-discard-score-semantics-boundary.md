---
id: RISK-007
type: risk
title: Discarded outcomes recorded before CHG-021 are not comparable with those recorded after
status: open
summary: Until CHG-021 a discarded candidate recorded an aggregate score of 0.0; afterwards it records its weighted magnitude. Rows, ledger envelopes and evolutionary-memory records written either side of that change carry the same field name with two different meanings, so any ordering or statistic computed across the boundary compares quantities that were never the same measurement.
owners:
  - architect
relates_to:
  - CON-002
  - RISK-002
decisions:
  - ADR-0002
evidence:
  - modules/deterministic/src/main/java/com/dreamthought/saaa/deterministic/PhenotypeFitnessScorer.java
  - modules/deterministic/src/main/java/com/dreamthought/saaa/deterministic/LineageNoveltyMemoryPolicy.java
  - specs/changes/CHG-021-discriminating-fitness/design.md
review_after: 2026-10-31
---

# Discard Score Semantics Boundary

## What is wrong

`aggregateScore` used to answer two questions at once. For a promoted candidate it
was the weighted sum; for a discarded one it was the constant `0.0`, which said
only "this failed". CHG-021 made it answer one question consistently — how good the
objectives were — and moved the pass/fail bit entirely into `decision`.

Everything persisted before that change still holds the old meaning. A `0.0` in an
older row means "discarded, magnitude unknown", not "discarded with nothing to show
for it". Nothing in the schema distinguishes the two.

## Why it is not blocking

The consumer that could do real damage has been fixed. `LineageNoveltyMemoryPolicy`
now orders by `decision` before score, so a high-scoring failure can no longer take
a champion slot. Because the two decisions are partitioned, an old and a new record
only ever compete inside the discarded group, where the effect is limited to which
failures are remembered rather than which candidate is treated as best.

## What would make it real

Any of these turns it back into a correctness problem:

- a statistic averaging or trending `aggregateScore` across discarded candidates;
- ablation reporting that compares discard magnitudes from different eras;
- a UI or export that ranks failures by score for a human to read.

## Options when it matters

1. Record a semantics version alongside the score and refuse to compare across it.
2. Backfill nothing and treat pre-CHG-021 discards as magnitude-absent, which is
   honest and needs a nullable score rather than a sentinel `0.0`.
3. Fold both into the `FitnessScore` value type CON-002 already contemplates, which
   carries score and semantics together and makes the ambiguity unrepresentable.

Option 3 is the destination; it needs a persistence migration and belongs with the
population slice, not here.

## The stored magnitude is quantised to two decimals

A related limit, found by a second reviewer on the same change. Promotion compares
the raw weighted sum, but what gets stored is `round(rawScore)` to two decimals,
and that stored value is what `LineageNoveltyMemoryPolicy` ranks by.

Two discarded candidates at raw `0.5949` and `0.5851` both store `0.59` and tie.
Decision-first ordering fixed ranking *across* the two decisions; within the
discarded partition the near-miss resolution this change exists to provide stops
at `0.01`. With a handful of candidates that is tolerable. With a population large
enough for collisions to be common, ranking degenerates toward the tie-break —
recency — which is not a fitness signal.

The `FitnessScore` type in option 3 above is where this is fixed too: store the
raw value and round only for display. Rounding at the point of storage throws away
information that cannot be recovered, and does so inside the field the archive
sorts on.
