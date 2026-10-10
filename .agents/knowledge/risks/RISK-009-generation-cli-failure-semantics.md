---
id: RISK-009
type: risk
title: A generation and a single-candidate run fail differently, and only one is documented
status: open
summary: The same flag chooses between two paths with opposite failure semantics. A rerun with a used --run-id fails loudly on the single-candidate path and exits zero having evaluated nothing on the generation path, while --run-id's help text documents only the first. A script checking the exit code cannot tell the difference, and the README's exit-code contract points the wrong way.
owners:
  - tech-lead
relates_to:
  - PAT-004
  - RISK-003
decisions:
  - ADR-0002
evidence:
  - modules/cli/src/main/java/com/dreamthought/saaa/cli/EvolveCommand.java
  - modules/cli/src/main/java/com/dreamthought/saaa/cli/MutationLoopCli.java
  - modules/deterministic/src/main/java/com/dreamthought/saaa/deterministic/GenerationEvaluationLoop.java
  - modules/adapters/src/main/java/com/dreamthought/saaa/adapters/evolve/EvolveRunner.java
review_after: 2026-12-31
---

# Generation CLI Failure Semantics

## What prompted it

The user-experience lens from `CHG-027`, applied to the `--candidates` flag
`CHG-026` merged. Four findings, all in code that had already passed three
independent review passes and CI. None was a correctness defect, which is why
none of the earlier passes looked for them.

This entry is therefore evidence for the lens as much as a record of the defects.

## The four

**The exit code contradicts the help text and the README.** `--run-id`'s help
says "expect a rerun with the same id to fail on the worktree its first run left
behind". True on the single-candidate path, where `GitCandidateWorkspace` throws
and nothing catches it, so the run exits non-zero. False under `--candidates N`
above one: `GenerationEvaluationLoop` catches the collision per candidate,
records `no evidence attempt-1-of-N`, and the run completes and exits zero having
evaluated nothing. `README.md` states "a non-zero exit means the run itself
failed". A person reading the console output can act on it; a script checking the
exit code is told the opposite of the truth. The scenario is the one a stable run
id exists for — name a run, have it fail, retry it.

**A mid-generation abort leaves nothing durable.** When the proposer repeats a
mutation id the loop throws, which is intended. But `recordGeneration` runs only
after `evaluate` returns, and `generationRanked` never fires, so there is no
ledger row, no journal entry and no journal-path line. The only record that the
generation aborted, and why, is a stack trace on a console that has already
scrolled past the per-candidate output.

**Runtime flag errors render as stack traces while parse errors render cleanly.**
`MutationLoopCli` registers no execution-exception handler, so picocli's default
applies. `--candidates abc` fails at parse time: one clean line, usage, exit 2.
`--candidates 0` passes parsing and throws `IllegalArgumentException` from
`EvolveCommand`: a `CommandLine$ExecutionException` stack trace, exit 1, with the
good message buried at its head. Two bad values for one flag, two presentations.
The pattern predates `--candidates`, which is only its newest instance.

**Ranking columns misalign on real candidate ids.** `ConsoleReporter` pads with
`%-40s`, which never truncates. A default timestamped run id already produces ids
past 40 characters, so scores and decisions drift right on every line.
`ConsoleReporterTest` uses 18-character ids, so its alignment assertion cannot
see it. Cosmetic, and it is also a small `PAT-004` instance: the fixture is
narrower than the real input.

## Why it is not fixed here

Found while reviewing `CHG-027`, which changes documentation and a contract check
and touches no CLI code. Fixing them inside that change would have made a
documentation change into a behaviour change with its own review surface.

## What would answer it

The first two are the ones that matter, and they are one decision rather than
two: **what should a generation that produced no evidence at all exit with, and
what should it leave behind?** Deciding that settles the exit code, the help
text, the README contract and the durable record together. The third is a
one-line picocli handler and should be done for every command at once rather than
for this flag. The fourth is a formatting choice plus a wider fixture.
