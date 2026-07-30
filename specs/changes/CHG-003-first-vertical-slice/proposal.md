# CHG-003 First Runnable Vertical Slice

## Why

The repository has eight ports, six real adapters with integration tests, a
reviewed mutation-contract policy stack and a hard-gated fitness scorer. Nothing
in main source constructs `MutationEvaluationLoop`. The CLI prints one sentence
and exits. Every capability so far has been built as a horizontal layer, and no
person can reach any of it.

This change assembles what exists into one command, and stops there.

## The defect this exposes

`GitCandidateWorkspace` creates a worktree at `HEAD`, writes a TOON document
describing the mutation, and commits that document. `Mutation.patch` is used in
exactly three places: written into that description, length-checked by the
validator, and scanned for authority language. It is never applied to a file.

So every candidate worktree is byte-identical to `HEAD` apart from a bookkeeping
file. Every check sees the same code. Every candidate scores the same. The loop
is a very well-audited way to measure one thing repeatedly.

Realization is therefore the first task in this change, not an afterthought.

## What ships

One command:

```sh
saaa evolve <folder> [--profile fixture] [--generations 1]
```

It proposes a mutation, validates it, creates an isolated candidate worktree,
realizes the mutation into that worktree, commits it, runs the target folder's
checks, scores the result deterministically, decides promote or discard, and
appends a readable entry to `journal.md` in the target folder.

No model credentials. Runs in CI. Deterministic end to end.

## What deliberately does not ship

Live providers, HTML reports, model-authored narration and journal compaction
are all wanted, and all follow. This slice earns the right to build them by
proving the pipe first, and by putting the reporter seam in place so they are
additive rather than retrofits.

Promotion to a Git ref stays `CHG-002` task `T5`. This slice records the
decision; it does not act on it.

## Sequencing

The order below is driven by what actually distinguishes this project from a
capable agent with regression tests, static analysis and design guidance. That
stack already proposes, validates and iterates, with a human in the selection
seat. It wins on cost and speed for any problem with a correct answer.

What it structurally cannot do is compare a population of candidates on measured
tradeoffs, or treat model nondeterminism as a sampling process rather than a
defect. Tests assert; they do not score. So the differentiator is population and
distribution, not vendor breadth.

| Slice | Ships | Why here |
|---|---|---|
| CHG-003 | runnable pipe, fixture profile, journal | prerequisite for everything; on its own it does not beat agent-plus-tests |
| next | N candidates per generation, ranked, with the losers recorded | the actual thesis; first point the project earns its complexity |
| next | repeated sampling and distribution comparison | makes a result trustworthy rather than lucky |
| next | provider profiles as a population dimension | same contract across five models, compared |
| next | HTML report and graphs | needs a population worth plotting |
| next | narration and compaction | needs a journal worth narrating |

Provider profiles moved down deliberately. Supporting more vendors is breadth.
Supporting the same contract evaluated across several models, scored
comparably, is a population dimension, and that is the stronger reason to build
it.

## Honest limits of this slice

A single candidate scored against a threshold is close to what a good agent with
tests and a rubric already gives you. This change is scaffolding, and it should
be judged as scaffolding. The machinery only starts paying for itself at the
population slice.

The sharpest risk is not technical. A fitness function that is wrong is worse
than no fitness function, because it launders judgement through a number that
looks objective. `parsimony` rewards small diffs, so optimising hard on it
produces cramped code that scores well. Every objective added needs that
scrutiny before it is trusted.

## Alternatives rejected

**A simple scorer instead of bridging to `PhenotypeFitnessScorer`.** Faster to
green, but the reviewed scorer would stay unwired and the two-stack duplication
would persist another slice.

**Full migration onto `MutationContract` in this slice.** Ends the duplication,
but touches all eight ports, both adapters and the acceptance test. Too large to
be a first vertical slice, and it would repeat the mistake this change corrects.

**Fixture proposer only, no profile seam.** Smallest slice, but `--profile`
plumbing and proposer construction would be retrofitted later, touching the same
wiring twice.
