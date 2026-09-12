---
name: review-routing
description: Decide whether a change needs independent review, route each pass to a model and a region of the change, and judge merge readiness from what comes back.
---

# Review Routing

## Outcome

Turn "should someone else look at this?" into a repeatable decision: how many
passes, which model, aimed at what, and what their answers are worth. Prevents
both under-reviewing (merging on green CI) and over-reviewing (three passes over
one surface that converge and stop finding things).

## When to trigger

- before marking a PR ready for review;
- when a change touches a gate, a decision, an audit record or a promotion;
- when a design is about to become code;
- when the lead has been working alone long enough that their assumptions are
  no longer being challenged.

## The rule that governs everything else

**Passing checks are not a review.** Green CI means the tests the author wrote
pass. The author's tests are the weakest part of the author's output, for the
same reason a delegated agent's tests are the weakest part of its output. See
`PAT-003`.

**Reviewing a design is not reviewing the code.** A design pass cannot find a
defect in an implementation that did not exist yet. Both are worth doing; one
does not substitute for the other. This is the easiest gate to skip by accident
after a long design conversation.

## How many passes

| Situation | Passes |
|---|---|
| Mechanical edit, docs, metadata | none; lead self-review |
| Bounded implementation, no gate touched | one |
| Touches a gate, decision, audit record or promotion | two, different ground |
| Architecture, ambiguity, or reviewers disagreed | two plus adjudication; consider `adversarial-debate` |

Prefer **the same change with different briefs** over more reviewers sharing one
brief. Repeated passes over one surface converge; a pass aimed somewhere new
does not.

This is measured here, not assumed. Three passes over one change, three regions,
two providers: every finding came from exactly one pass and no two passes
overlapped on any finding. One returned a blocker the other two did not see, on
ground only its brief covered.

Once the passes are decided, `dispatching-review-passes` is how to run them
without them interfering.

## Routing by model

Route by what the task needs, not by which model is best.

- **Different providers over two runs of one provider.** Two providers disagree
  in ways one provider twice does not. That disagreement is the product.
- **Low reasoning effort is often enough for review.** Finding a defect in
  written code is easier than designing the code. Spend the strong-model budget
  on architecture, ambiguity and adjudication.
- **A reviewer gets a clean session, never a resumed one.** A reviewer must not
  inherit an implementer's conclusions.
- **Read-only is an invariant to enforce, not to assume.** A reviewer with write
  tools mutates the change the others are reading, and the passes stop being
  additive.

Detect what is installed before naming a tool; see the delegation section of
`AGENTS.md` for current invocations.

## Aiming a brief

Each brief owns a **region**, and says which regions belong to someone else.
Regions that have worked here:

- the seam the change introduces, and whether it is complete;
- validation and collision rules, and whether any is wrong or missing;
- the wiring — does the new thing actually execute;
- blast radius — what else builds or serialises the changed type;
- the tests — which could pass for the wrong reason;
- **declared but unenforced** — a guard that is written but that nothing reads.

Say plainly what earlier passes already covered, and tell the reviewer not to
re-litigate settled design.

Ask directly about anything the lead already suspects is wrong. A brief that
names the doubt gets a sharper answer than one that hopes the reviewer stumbles
onto it.

Always end a brief with: *is this safe to merge on your ground, yes or no.*

## Every pass must return a verdict

**Silence is not a pass.** A reviewer that exits zero having concluded nothing
has told you nothing, and reading that as "no findings" is the same error as
merging on green CI. This has happened here: a pass tried to write a probe file,
was refused, stopped, and exited zero with no verdict.

Four things make it not happen again, in the order they apply.

**Make the verdict machine-checkable.** End every brief with a required line, on
its own line, in a fixed form:

    SAFE TO MERGE ON MY GROUND: YES|NO

Then checking for it is a grep rather than a judgement, and a missing one is
visible without reading the whole output.

**Offer "insufficient evidence" explicitly.** A reviewer with no way to say *I
could not establish enough* will pad instead, and padding reads like a finding.
Give it the third option and require it to name what it could not check:

    INSUFFICIENT EVIDENCE FOR A VERDICT — <what you could not check>

**Check the verdict separately from the exit code.** The process exiting tells
you it stopped, not that it concluded. Count verdicts against passes launched
before consolidating; do not begin consolidation one short.

**Launch read-only by construction, never by request.** A brief asking a reviewer
not to write still leaves it able to try, and being refused mid-flight is exactly
how a pass dies without concluding.

| Harness | Read-only invocation |
|---|---|
| `codex` | `codex exec --sandbox read-only --ephemeral --skip-git-repo-check` |
| `opencode` | `opencode run --agent plan` |

Say it in the brief as well, and say what to do *instead* — "describe the
mutation precisely rather than applying it", "verify SQL by replaying the DDL in
an in-memory database". A reviewer told only what it cannot do will stop at the
obstacle; one told what to do instead will route around it.

## Recovering a pass that returned no verdict

Cheapest first. Verified in a September 2026 session against `opencode`, and the
rungs generalise even where the flags do not.

1. **Resume the session and ask only for the verdict.** `opencode session list`
   recovers the session id even when the launch never captured it, so a lost id
   is not a lost session. Then resume read-only with a short prompt that forbids
   restarting the review. One turn, and the analysis is still there.
   `opencode export <id>` also recovers a verdict from a session whose output
   looked lost.
2. **Relaunch clean with the blockage removed** — when whatever blocked the pass
   was load-bearing for its verdict. This is the rung most easily skipped, and
   the reason not to skip it is measurable. Both were run on the same brief here:
   the resumed session returned a verdict marking its SQLite claims "unverified
   empirically" three times, because the probe it wanted was refused; the clean
   read-only relaunch verified those same claims by replaying the DDL in memory
   and reached the same conclusion with the caveats gone. **A resumed pass
   returns the verdict it could form; a relaunched one returns the verdict you
   asked for.**
3. **Route to a different provider** if a pass fails to conclude twice. A second
   failure is usually the brief or the model, not the run.
4. **Record the lost independent challenge** if none is available, and get
   explicit authorisation for the degraded path. See `PAT-001` and the
   team-and-model fallback rule in `AGENTS.md`.

Two rungs cost less than one re-derivation, so there is no case for skipping
straight to a fresh pass that starts cold.

**A recovered verdict is still a claim.** Agreement between a resumed pass and
its relaunch is a consistency signal and nothing more — both runs here reached
the same substantive findings, and every one of them was still verified against
the code before being acted on.

## What comes back is a set of claims

- **Verify every finding against the code**, including the ones expected to be
  right.
- **Reproduce at least one mutation proof** before repeating any as evidence.
- **Never accept "the suite passed" on trust.** Run it with `--rerun-tasks`; a
  cached no-op prints `BUILD SUCCESSFUL` in under a second.
- **Adjudicate contradictions rather than averaging them.** Two reviewers
  disagreeing is the mechanism working.
- Apply the resulting fixes serially, in one place.

A reviewer reporting a blocker instead of working around it is the behaviour to
want. Say so, and finish the job it could not.

## Merge readiness

Ready to merge when all of these hold, and not before:

- [ ] CI passes, and the jobs that matter actually ran rather than skipping;
- [ ] the implementation, not only its design, has been independently reviewed;
- [ ] every finding is verified, then fixed or explicitly triaged with a reason;
- [ ] a lead self-review in code-review style, naming findings or saying none;
- [ ] new assertions about a gate, decision, audit record or promotion have
      been seen to fail, with the mutation recorded;
- [ ] `HANDOFF.toon` records validation, branch, commit and review state;
- [ ] the human has authorised the merge. Merge is theirs, not the lead's.

If any gate is degraded — no review tooling, a reviewer that timed out — record
which independent challenge was lost and get explicit authorisation for the
degraded path. See `PAT-001` and the team-and-model fallback rule in
`AGENTS.md`.

## Proposing next steps

After consolidating, state plainly: what is blocking, what is merely untidy, and
a recommendation for each. Separate "must fix on this branch" from "track into
the next change" and give the reason, because that split is a judgement the
human may want to overturn.
