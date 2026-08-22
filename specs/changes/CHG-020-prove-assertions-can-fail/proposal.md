# CHG-020: Require a new assertion to be proven capable of failing

## Why

On 2026-08-22, five assertions in this repository could not fail. Four were
written by the lead, and one of those was written after several hours spent
finding exactly that defect in other people's work.

Every one was caught by mutation. None was caught by reading — including by
reviewers explicitly briefed to attack weak tests, and including by the author
immediately after writing them.

`AGENTS.md` requires boundary-in, ATDD-aligned design and says nothing about
establishing that a test can fail. So the practice was happening only because one
actor had been burned, which is not a property of the repository.

## What

One paragraph on the existing testing rule, naming the failure shapes that recur,
guarded by `check-repo-contract`. `PAT-004` records the five incidents.

Not a new section: CHG-017 was withdrawn for adding guidance beside guidance that
already existed.

## Not this change

- mutating every test, or any tooling to automate it;
- a coverage threshold, which measures something else entirely;
- replacing review. Reviewers found defects mutation missed and mutation found
  defects three independent reviewers missed.

## Relates to

PAT-004, PAT-003, LRN-001.
