---
id: LRN-001
type: learning
title: A review actor's verdict can survive its transport failing
status: proposed
summary: Two independent CHG-011 reviews were treated as unfinished because their verdicts never reached the OpenCode CLI event stream. Both verdicts were retained in the persisted sessions and were recovered afterwards with opencode export; between them they had already found two defects that were rediscovered and fixed five days later.
owners:
  - lead
relates_to:
  - PAT-001
  - PAT-003
evidence:
  - https://github.com/fiq/self-adapting-agentic-architecture/pull/29
  - specs/changes/CHG-011-interactive-harness-runtime/change.toon
reviewed_at: 2026-08-22
review_after: 2026-11-30
---

# A Review Verdict Can Survive Its Transport Failing

On 2026-08-17 two NeuralWatt Flex reviews of CHG-011 were run through OpenCode.
Neither terminal verdict appeared in the CLI event stream, so PR #29 was opened
stating that the branch "still needs an independent human or tool review before
merge". The PR was later merged with that gap standing.

The claim lived in the PR #29 body. The merged `HANDOFF.toon` at `bee472b`
contains no record of these reviews at all, which is its own gap: a review that
gates a merge should be recorded in the handoff, not only in a PR description
that no longer surfaces once the PR is closed.

On 2026-08-22 both sessions were exported and both contained a complete
`VERDICT: REQUEST_CHANGES`:

| session | findings |
|---|---|
| `ses_ff314401bffefF9KLShxX0gBDw` | 5, four still true against merged `main` |
| `ses_ff321d3c8ffercIVJQf6NoJJT6` | 5, a different set |

## The cost was not hypothetical

Three findings from the second session were rediscovered independently on
2026-08-22, after the merge, and two of them were then fixed as new work:

- `handle()` catching only `IllegalArgumentException` and `IllegalStateException`
  while `EvolveRunner.readString` raises `UncheckedIOException`. The 08-17 review
  additionally noted this contradicts `design.md`'s own statement that "command
  errors leave an active session active", so the fix was restoring a documented
  contract rather than adding behaviour. That connection was lost with the
  verdict and had to be found again.
- `evolve` folding `case1 case2` into a single behaviour-case name.
- `evolve` hardcoding retrieval treatment and task text with no equivalent of
  `saaa-evolve`'s `--retrieval` and `--task`, undocumented as a limitation.

Five days of duplicated review effort, and a merge that shipped defects an
already-completed review had named.

## What to do differently

- A missing verdict is a **transport** hypothesis. It is not a completed review
  and it is not a failed one. Before recording a review as unfinished, export the
  session: `opencode export <sessionID>`, or `opencode session list` to find it.
- Prefer a retained session to an ephemeral one when a review's result is
  load-bearing. Retention is what made recovery possible here; it is not a
  guarantee that any given failure is recoverable.
- Record the session ID in `HANDOFF.toon` when a review gates a merge. An
  unrecorded ID is recoverable only by scanning `opencode session list` by title
  and date, and a PR body stops being a discoverable record once the PR closes.
- An OpenCode session is bound to the directory it was created in. Deleting that
  directory leaves the session listed and exportable but makes `opencode run -s
  <id>` fail with an opaque server error. Recreating the path restores it.
- A supervising harness can report success for a run that exited non-zero after
  doing nothing. Check the process exit code and the event stream, not the
  wrapper's completion notice. This is the same class of failure as the lost
  verdict: a wrapper reporting something the underlying process never said.

## What this does not mean

A recovered verdict is not automatically correct. Of the five findings in the
first session, one was wrong: it asked the `CODE` acceptance test to assert that
`Example.java` in the operator's checkout contained the realized change. Running
that assertion proved the file still reads `return "old"`, which is the promotion
boundary working as designed — realization happens in the isolated candidate
worktree and promotion records `refs/heads/candidate/*` without merging. The
assertion was rewritten to check the candidate ref, and to assert positively that
the operator's copy is untouched.

Recovering a lost review restores the input. It does not replace verifying it.
