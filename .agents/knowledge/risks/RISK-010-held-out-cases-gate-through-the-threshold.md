---
id: RISK-010
type: risk
title: A held-out case gates nothing directly but can still decide promotion through the threshold
status: open
summary: A failing held-out case lowers task_success, and promotion also requires the score to reach PROMOTION_THRESHOLD (0.80). With few behaviour cases one held-out failure is enough to DISCARD a candidate that passed every gate, so "gates nothing" in the --held-out-case help is true of the gates and false of the decision.
owners:
  - architect
  - tech-lead
relates_to:
  - PAT-004
  - RISK-008
decisions:
  - ADR-0002
evidence:
  - modules/deterministic/src/main/java/com/dreamthought/saaa/deterministic/PhenotypeFitnessScorer.java
  - modules/deterministic/src/main/java/com/dreamthought/saaa/deterministic/PhenotypeBridgeScorer.java
  - fixtures/toy-workflow/publish-gated.sh
review_after: 2026-12-31
---

# Held-out cases gate through the threshold

## What was observed

On 2026-11-07, running the README example against the hardened toy fixture with
one extra flag:

    saaa-evolve toy --behaviour-case workflow-check --held-out-case publish-gated --max-lines 80

the fixture mutation passed `workflow-check`, failed the held-out
`publish-gated`, scored 0.71 and was DISCARDed ("Scored 0.7107 against a
threshold of 0.80"). Without the held-out case the same mutation scores 1.00 and
promotes.

## Why

`PhenotypeFitnessScorer` decides PROMOTE only when the gates pass **and** the
raw score reaches `PROMOTION_THRESHOLD` (0.80). `task_success` carries weight
0.40 and counts held-out cases. With one required case and one held-out case, a
held-out failure halves `task_success` and costs 0.20, which puts almost any
candidate under the threshold.

## Where the documentation disagrees

- `--held-out-case` help and the README options table: "runs and scores but
  gates nothing".
- README held-out section: a candidate "is still eligible when a held-out case
  fails — it simply scores lower for it". Eligible is true; that scoring lower
  can flip the decision is not said.

## What would answer it

One decision: is a held-out case allowed to change PROMOTE to DISCARD?

- If **no**, the threshold should be applied to a score computed without
  held-out cases, and held-out cases only order candidates.
- If **yes**, the help text and README should say so, and the number of
  held-out failures that crosses the threshold for a given case count should be
  visible to the person choosing them.

Not fixed in the change that found it, which only hardens a test fixture.
