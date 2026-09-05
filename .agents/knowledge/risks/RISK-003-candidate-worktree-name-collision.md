---
id: RISK-003
type: risk
title: Candidate worktree names collide across runs so evolve cannot repeat on one folder
status: obsolete
summary: Candidate ids and worktree paths derive only from the workflow id and mutation id, so a second run producing the same mutation id fails with "candidate worktree already exists" instead of evaluating a new candidate.
owners:
  - architect
relates_to:
  - SYS-001
  - ARCH-001
evidence:
  - modules/adapters/src/main/java/com/dreamthought/saaa/adapters/git/GitCandidateWorkspace.java
  - modules/cli/src/main/java/com/dreamthought/saaa/cli/EvolveCommand.java
review_after: 2026-10-30
---

# Candidate Worktree Names Collide Across Runs

`GitCandidateWorkspace.createCommittedCandidate` derives the candidate id as
`candidate-<mutation-id>` and the worktree path as
`<worktreesRoot>/candidate-<workflow-id>-<mutation-id>`, then fails outright if
that path already exists. Nothing in the name distinguishes one evaluation of a
mutation from another.

The fixture proposer is deterministic and always emits mutation id
`MUT-<workflow>-fixture`, so this surfaces immediately: running
`saaa evolve <folder> --behaviour-case <name>` twice against the same folder
fails the second time with
`IllegalStateException: candidate worktree already exists`, and the user must
delete `.worktrees/candidate-*` by hand between runs.

Observed 2026-07-30 while validating the `CHG-003` branch: run one promoted,
run two aborted before proposing a candidate.

This is a usability and repeatability limitation rather than a safety one. The
failure is loud, the existing candidate is never overwritten, and the worktree
isolation guarantee is preserved by the very check that fails. AGENTS.md also
forbids removing a dirty worktree, so failing is preferable to reusing or
deleting the path automatically.

It became blocking at the population slice, which evaluates several candidates
per generation and therefore needs many live candidate worktrees at once.

## Resolved 2026-09-05 by CHG-026 T4

Status is `obsolete` because the knowledge vocabulary has no `resolved` or
`mitigated`, and the collision this entry describes no longer occurs. The entry
is kept rather than deleted: it records why the naming carries a run id, which is
not obvious from the code alone.

The first of the two options was taken: a run discriminator in the candidate
name, so lineage stays readable and every evaluation gets its own path. The
second option was not taken, and deliberately: reusing or retiring a prior
worktree would have put a deletion policy in the path of every run, and AGENTS.md
forbids removing a dirty worktree for good reasons.

`CandidateNamespace` supplies two parts. The run id separates one run from the
next and defaults to a millisecond timestamp, so nothing has to be passed for
repeat runs to work. The candidate position separates candidates within one
generation. `EvolveRunner` applies it on the evolve path, which is where the
defect was live, and `--run-id` names a run explicitly when predictable worktree
paths are wanted.

Evidence rather than assertion: an acceptance test runs `saaa-evolve` twice on
one folder and asserts both evaluate and each leaves its own candidate branch.
Reverting the wiring makes that test fail with the original error, which is how
the fix was confirmed to be the thing doing the work. A real-Git integration test
covers two candidates of one generation coexisting.

The manual cleanup the README documented is no longer needed between runs.
Worktrees still accumulate under `.worktrees/`, and no automatic cleanup policy
exists — that remains deliberate and unaddressed.
