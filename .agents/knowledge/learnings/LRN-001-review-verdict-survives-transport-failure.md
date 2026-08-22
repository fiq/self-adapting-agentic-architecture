---
id: LRN-001
type: learning
title: A review actor's verdict can survive its transport failing
status: canonical
summary: CHG-011 was merged recording two independent reviews as incomplete because the OpenCode CLI event stream never surfaced their verdicts; both verdicts were retained in the persisted sessions the whole time and were recovered afterwards with opencode export.
owners:
  - lead
relates_to:
  - PAT-001
  - ARCH-001
  - CON-002
evidence:
  - https://github.com/fiq/self-adapting-agentic-architecture/pull/29
  - specs/changes/CHG-011-interactive-harness-runtime/change.toon
reviewed_at: 2026-08-22
review_after: 2026-11-30
---

# A Review Verdict Can Survive Its Transport Failing

On 2026-08-17 two NeuralWatt Flex reviews of CHG-011 were run through OpenCode.
Neither terminal verdict appeared in the CLI event stream, so PR #29 was opened
recording that the branch "still needs an independent human or tool review
before merge", and `HANDOFF.toon` carried the same claim. The PR was later
merged with that gap standing.

On 2026-08-22, `opencode export ses_ff314401bffefF9KLShxX0gBDw` returned the
session in full, including a complete `VERDICT: REQUEST_CHANGES` with five
findings. The review had completed. Only its delivery had failed.

Four of the five findings were still true against merged `main`: a design
state diagram citing `NEW` and `EVALUATING` states that
`HarnessSessionStatus` never had, a `status: proposed` spec with every task
completed, a requirement saying the session dispatches "only an approved
harness-workflow target" while scenario `S5` in the same file dispatches
`CODE`, and help output omitting the `close` alias the parser accepts.

## What to do differently

- A missing verdict is a **transport** hypothesis, not a completed review and
  not a failed one. Before recording a review as incomplete, export the session
  and look: `opencode export <sessionID>`, or `opencode session list` to find
  it.
- Prefer a retained session over an ephemeral one for any review whose result is
  load-bearing. An ephemeral run that loses its stream loses the work; a
  retained one does not. `--ephemeral` is right for throwaway passes only.
- Record the session ID in `HANDOFF.toon` when a review gates a merge. An
  unrecorded session ID is recoverable only by scanning `opencode session list`
  by title and date. CHG-012 (PR #30, not yet merged) codifies this as PAT-002;
  link this entry to it once that lands.

## What this does not mean

It does not mean a recovered verdict is automatically correct. Of the five
recovered findings, one was wrong: it asked the `CODE` acceptance test to assert
that `Example.java` in the operator's checkout contained the realized change.
Running that assertion proved the file still reads `return "old"`, which is the
promotion boundary working as designed — realization happens in the isolated
candidate worktree and promotion records `refs/heads/candidate/*` without
merging. The assertion was rewritten to check the candidate ref instead, and to
assert positively that the working tree is *not* modified.

Recovering a lost review restores the input. It does not replace verifying it.
