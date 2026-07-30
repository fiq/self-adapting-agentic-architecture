---
id: RISK-003
type: risk
title: Candidate worktree names collide across runs so evolve cannot repeat on one folder
status: proposed
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

It becomes blocking at the population slice, which evaluates several candidates
per generation and therefore needs many live candidate worktrees at once. Two
options, neither chosen yet:

- include a generation or run discriminator in the candidate id, so lineage
  stays readable and every evaluation gets its own path;
- keep the derived name but have the loop reuse or retire a prior candidate
  worktree under an explicit, recorded policy.

Until then the README documents the manual cleanup, and repeat runs in the same
folder are not supported.
