---
id: PAT-003
type: pattern
title: Independent review fans out, consolidation does not
status: proposed
summary: Reviews are read-only and can run in parallel on one diff because they share no mutable state, though their conclusions may contradict; implementation agents cannot share a file. The work is consolidating findings — deduplicate, adjudicate contradictions, verify every finding against the code, then apply fixes serially.
owners:
  - lead
relates_to:
  - PAT-001
  - PAT-002
  - ARCH-001
  - LRN-001
review_after: 2026-11-30
---

# Independent Review Fans Out, Consolidation Does Not

Two different things get called "running an agent", and they have opposite
safety properties.

**Reviews are read-only.** Several reviewers on one diff share no mutable state,
so their findings combine additively. This is the one place fan-out reliably
pays.

Their *conclusions* can still contradict each other, and often do — incident 2
below is exactly that. Contradiction is a reason to run more than one reviewer,
not an argument against it, and resolving it is step 2 of consolidation. Do not
read "cannot conflict" into this: only the writes cannot conflict.

Read-only is an invariant to enforce, not a fact to assume. Nothing here
mechanically prevents a reviewer being handed write tools; if that happens it
mutates the diff the others are reading and additivity quietly stops holding.

**Implementation agents write.** Two of them on one file buys merge conflicts,
not speed. In this repository `HANDOFF.toon` and `PhenotypeFitnessScorer` are the
recurring conflict surfaces.

## Prefer different briefs over more reviewers

Give reviewers the same diff and different briefs — architecture and security,
spec fidelity, test strength, documentation accuracy. Identical briefs mostly
return the same findings twice and add deduplication cost. PR #15 used two
differing briefs and they found disjoint problems.

## Consolidation is the work

1. **Deduplicate.** Two Flex sessions on CHG-011 both raised the state diagram.
2. **Adjudicate contradictions rather than averaging them.** One reviewer called
   the CHG-012 contract guard a blocker; another correctly downgraded it to a nit
   because `design.md` already disclosed the guard as presence-only. Averaging
   would have been wrong in both directions.
3. **Verify every finding against the code before acting on it.** The same
   reviewer that was wrong about that guard was right that an evidence id could
   overwrite a structural gate's audit key. Only checking distinguished them. A
   reviewer's confidence is not evidence.
4. **Apply fixes serially, in one place.** This step must not be parallel.

## Do not treat one clean review as strong evidence

On 2026-08-22 every independent pass found something the previous reviewer and
the lead had both missed, twice in the lead's own work — an acceptance test that
could not fail, and an audit write that could report a passing gate for a
candidate whose checks failed. Both are defects the lead had spent the same
session finding in other people's work.

Green CI is not review. The audit-key overwrite described above passed every
suite before an independent pass found it.

## Related

`LRN-001` records that a missing verdict is a transport failure rather than an
absent review, so a review believed lost should be recovered before being
re-run. `PAT-002` governs the sessions reviewers run in: a reviewer gets a clean
session and does not inherit an implementer's unreviewed conclusion.
