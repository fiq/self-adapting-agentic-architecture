# Multi-Answer Workflow Fixture

A target folder where several different edits are all good, and good in different
ways the scorer can see. The toy fixture has one correct edit, so every passing
candidate scores the same and a generation's spread is zero by construction.
This one gives a generation something to rank (CHG-033, ADR-0002).

| File | Role |
|---|---|
| `workflow.txt` | the artifact being evolved: five independent knobs, with the allowed values listed in `#` comments so a model can see them |
| `knob.sh` | shared parser the checks source; not a case itself |
| `has-a-gate.sh` | required; fails on the baseline, passes when review is required or tests run |
| `keeps-every-knob.sh` | required; all five knobs stay, each with a value, so deleting lines is not an answer |
| `one-value-per-knob.sh` | required; each knob is set at most once, so a later line cannot contradict an earlier one |
| `recovery-exists.sh` | required; recovery stays `manual` or `automatic` (allow-listed, so `off`, `false` or `skip` fail) |
| `reviewed.sh` | held-out; passes when review is required |
| `tested.sh` | held-out; passes when tests run |
| `staged-rollout.sh` | held-out; passes when rollout is staged, canary, phased or gradual |
| `.saaa/fixture-mutation.txt` | the canned mutation (`review: required`), summary on line one |
| `answers/*.txt` | reference answers in the same format: summary on line one, whole `workflow.txt` after it |
| `journal.md` | written by a run; not committed |

## How the checks read the file

The checks read `workflow.txt` the way a person would, so a live model is judged
on what it meant rather than its spelling:

- knob names and values are case-insensitive;
- spaces around `:` and at the ends of a line are ignored;
- `#` lines are ignored;
- a few synonyms count: `require`, `mandatory` and `enforce(d)` for review;
  `required`, `always` and `enforce(d)` for tests; `phased` and `gradual` for rollout.

A value outside those lists fails. That is a wording miss, not a judgement, so
the canonical values are listed at the top of `workflow.txt`.

## Run it

Copy the folder into a Git repository first, because checks run in a worktree
made from `HEAD`.

```
saaa-evolve <copy> --profile fixture \
  --behaviour-case has-a-gate --behaviour-case keeps-every-knob \
  --behaviour-case one-value-per-knob --behaviour-case recovery-exists \
  --held-out-case reviewed --held-out-case tested --held-out-case staged-rollout
```

That runs the canned mutation. To score a reference answer instead, copy it over
the canned mutation before committing the copy:

```
cp <copy>/answers/review-and-staged-rollout.txt <copy>/.saaa/fixture-mutation.txt
```

Comparing the three answers is three runs. The fixture profile cannot produce
them in one generation: its `--candidates` variants are the canned edit plus
padding, so they differ by diff size, not by answer.

## Why the reference answers differ

The baseline fails `has-a-gate` only. Two independent edits clear it, and each
held-out case rewards a different knob, so no small edit passes all three.

| Answer | Required | Held-out failed | Score |
|---|---|---|---|
| `review-tests-and-canary` | all pass | none | 0.989 |
| `review-and-staged-rollout` | all pass | `tested` | 0.911 |
| `tests-only` | all pass | `reviewed`, `staged-rollout` | 0.833 |
| the canned mutation | all pass | `tested`, `staged-rollout` | 0.833 |

## The promotion arithmetic

A candidate promotes when every required case passes **and** its score reaches
`PhenotypeFitnessScorer.PROMOTION_THRESHOLD`, which is `0.80`.

With no probes or benchmarks declared, three objectives are measured:

| Objective | Weight | Here |
|---|---|---|
| `task_success` | 0.40 | cases passed out of 7, held-out included |
| reliability | 0.20 | 1.0 for one clean run |
| parsimony | 0.10 | 0.925 to 0.975, by diff size against `--max-lines` 80 |

The score is their weighted sum divided by `0.70`, the weight actually measured.
So one held-out failure costs `0.40 / 7 / 0.70`, about `0.082`, and two cost
about `0.163`.

- **One failure promotes:** `review-and-staged-rollout` scores 0.911.
- **Two failures still promote, but only just:** `tests-only` and the canned
  mutation score 0.833, a margin of about 0.033.
- **The margin is fragile.** Adding a held-out case they fail would drop them
  below 0.80. So would a much larger diff.
- **Few cases is the trap.** With one required and one held-out case, a single
  held-out failure costs about 0.286 and discards. That is RISK-010.

The scores above follow from this arithmetic. The acceptance test checks the
decisions and the order of the scores, not the exact values.

Each case is verified by the script named after it, so adding
`--held-out-case notified` means adding `notified.sh` here.

## Why the knob is called `recovery`

It is not called `rollback` on purpose. The mutation validator
(`AuthorityLanguage`) rejects any mutation text containing approve, approval,
promote, promotion, discard or rollback. It also rejects score, scoring and
fitness, but only near candidate, mutation or result. A `rollback:` line would
therefore fail every run.
