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
