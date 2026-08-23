---
id: RISK-008
type: risk
title: Held-out cases are protected by mutation scope, not by the check-script guard
status: open
summary: A held-out case is a script in the repository that grades a candidate without gating it. It is out of the candidate's reach only because mutation scope is WORKFLOW_DEFINITION; requireWorkflowIsNotCheckScript does not protect it, so widening scope would let a candidate rewrite the test that scores it.
owners:
  - architect
relates_to:
  - ARCH-001
  - RISK-001
decisions:
  - ADR-0002
evidence:
  - modules/adapters/src/main/java/com/dreamthought/saaa/adapters/evolve/EvolveRunner.java
  - modules/deterministic/src/main/java/com/dreamthought/saaa/deterministic/ScoringConfig.java
  - specs/changes/CHG-024-held-out-task-success/change.toon
review_after: 2026-11-30
---

# Held-Out Cases Depend On Mutation Scope

## What is wrong

`CHG-024` added held-out behaviour cases: scripts that run and feed
`task_success` but decide no gate. A candidate that could edit one would be
grading itself, which is the failure `ARCH-001` and `RISK-001` exist to prevent.

Two guards look like they cover this and only one does.

- `requireWorkflowIsNotCheckScript` rejects the case where `--workflow-file`
  names a check script. It says nothing about any other file.
- `MutationScopeValidator(Set.of(MutationScope.WORKFLOW_DEFINITION))` is what
  actually keeps check scripts out of reach, because a candidate may only change
  the workflow definition.

So the protection is a property of the currently narrow mutation scope, not of
anything specific to held-out cases.

## Why it is not blocking now

Under `WORKFLOW_DEFINITION` scope a candidate cannot write to a check script at
all, held out or gating. Held-out cases also add no new incentive: the objective
is a pass fraction capped at 1.0, so there is no magnitude to over-fit even if a
script were reachable.

## What would make it real

- Widening mutation scope to include files beyond the workflow definition, which
  Layer 3 requires by definition: the mutation target there is product code.
- Any realization path that writes outside the declared scope.
- A held-out case whose script reads generated state a candidate can influence.

## What to do when it matters

The gating cases have the same exposure, so the answer is not specific to
held-out cases: check scripts need to live somewhere a candidate provably cannot
write, and the promotion path needs to verify that the scripts which graded a
candidate are the ones the baseline declared. That is a scope-widening
precondition rather than a `CHG-024` follow-up, and it belongs with the Layer-3
slice that first widens scope.
