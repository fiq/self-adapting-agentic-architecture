# CHG-021 design

## The line that erased the evidence

```text
before:  rawScore = gatesPassed ? weightedScore : 0.0
         decision = gatesPassed && rawScore >= 0.80

after:   rawScore = weightedScore
         decision = gatesPassed && rawScore >= 0.80      unchanged
```

Every failed candidate collapsed to `0.0`, so failures were mutually
indistinguishable. `CON-002` already described the intended model — binary for the
decision, with a magnitude retained so a near miss outranks a total miss — and the
glossary already recorded that it was not implemented. Only the zeroing stood in
the way.

## Why probes are withheld from the gate

A probe is a check, and every failing check fails the deterministic-checks gate.
Left in the list, a failing probe would discard the candidate, which is gating
under another name and defeats the split the design depends on:

```text
  required_evidence ──► GATE   absent or failed ⇒ DISCARD
  --safety-probe ─────► GRADE  pass fraction ⇒ 0.10 of the weighted sum
```

This was found by a test rather than by reasoning: `aFailingProbeLowersTheScore\
WithoutDiscarding` discarded, because the probe was still in the gated evidence.

## The mechanism has to run

Probe scripts are executed alongside behaviour cases. Without that the objective
reads `0.0` from absence and looks measured. An acceptance test asserted the right
outcome and passed for exactly that wrong reason; mutating the withholding logic
changed nothing, which is what exposed it. `PAT-004` records the case.

## What this does not fix

Among candidates that promote, the sum still has little range: `reliability`
remains pinned at `1.0`, and with a single run it can only observe a timeout.
Reclaiming it needs repeated execution and is its own slice.

The discrimination this change delivers is mostly among *failures*, which is where
a population chooses. That is the useful half first, not the whole problem.
