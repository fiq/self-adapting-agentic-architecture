# Multi-Answer Workflow Fixture

A target folder where several different edits are all good, and good in different
ways the scorer can see. The toy fixture has one correct edit, so every passing
candidate scores the same and a generation's spread is zero by construction.
This one gives a generation something to rank (CHG-033, ADR-0002).

| File | Role |
|---|---|
| `workflow.txt` | the artifact being evolved: five independent knobs |
| `has-a-gate.sh` | required case; fails on the baseline, passes when `review: required` or `tests: run` |
| `keeps-every-knob.sh` | required case; all five knobs must stay, so deleting lines is not an answer |
| `recovery-exists.sh` | required case; recovery must not be turned off |
| `reviewed.sh` | held-out case; passes only on `review: required` |
| `tested.sh` | held-out case; passes only on `tests: run` |
| `staged-rollout.sh` | held-out case; passes on `rollout: staged` or `canary` |
| `.saaa/fixture-mutation.txt` | the canned mutation, summary on line one |
| `answers/*.txt` | reference answers in mutation-file format: summary on line one, whole `workflow.txt` after it |
| `journal.md` | written by a run; not committed |

Run it (copy it into a Git repo first, because checks run in a worktree made from `HEAD`):

```
saaa-evolve <copy> --profile fixture --candidates 3 \
  --behaviour-case has-a-gate --behaviour-case keeps-every-knob --behaviour-case recovery-exists \
  --held-out-case reviewed --held-out-case tested --held-out-case staged-rollout
```

## Why the reference answers differ

The baseline fails `has-a-gate` only. Two independent edits clear it, and each
held-out case rewards a different knob, so no small edit passes all three.

| Answer | Required | Held-out failed | Why it differs |
|---|---|---|---|
| `review-tests-and-canary` | all pass | none | three edits, the most improvement, the biggest diff |
| `review-and-staged-rollout` | all pass | `tested` | two edits |
| `tests-only` | all pass | `reviewed`, `staged-rollout` | one edit, the least improvement |

## The promotion arithmetic

`PhenotypeFitnessScorer.PROMOTION_THRESHOLD` is `0.80`. `task_success` weighs `0.40`
and counts every declared case, held-out included. Here that is six cases, so one
failing held-out case costs `0.40 / 6`, about `0.067`, and two cost about `0.133`.
A clean promotion sits near `0.90` or above, so a candidate failing one held-out
case still promotes. This is the RISK-010 guard: with one required and one
held-out case a single held-out failure costs `0.20` and discards.

Scores also differ by parsimony, because the answers change different numbers of
lines, but the held-out results are what separate them.

Each case is verified by the script named after it, so adding
`--held-out-case notified` means adding `notified.sh` here.

The knob is called `recovery`, not `rollback`, on purpose: the mutation validator
rejects any mutation text containing the words approval, scoring, promotion,
discard or rollback, so a fixture answer cannot use them.
