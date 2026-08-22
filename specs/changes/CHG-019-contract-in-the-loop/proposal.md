# CHG-019: Put the accepted contract on the wired promotion path

## Why

`RISK-002` says a `repair` contract declaring `failing_case_reproduced` and
`regression_case_added` can be realized with neither and still promote. CHG-014
gave the scorer an entry point that enforces declared evidence, then left the
risk open on purpose, because the wired path cannot reach it: `FitnessScorer`
takes `(Candidate, EvaluationEvidence)` and has no parameter for a contract.

This closes it for any run that declares a contract. A run that declares none is
unchanged, so RISK-002 narrows rather than closing.

## Where the contract comes from

The **operator** declares it, not the proposer.

`CHG-002 T3d` sequenced contracts as something a model emits in a TOON envelope.
That needs an envelope parser, which needs a Java TOON reader, and neither
exists — `GoldenCorpus` shipped as Java constants for that reason. So the
recorded route is three chunks deep before a single declared gate is enforced.

It is also the wrong shape. A model that declares the evidence it will be judged
against is choosing its own grading criteria, which is the concern `ARCH-001`
exists to prevent. The operator declaring bounds and required evidence, and the
model producing a realization within them, keeps the deciding step outside the
thing being decided about.

Model-emitted envelopes remain possible later; `T3d` is unaffected and this
change does not close it.

## What a declared evidence id means

It names a check that must exist and must pass. `--required-evidence
regression_case_added` means the run must produce a check called
`regression_case_added` with a passing outcome. Absent is a discard, failed is a
discard, and neither can be satisfied by a check the contract did not name.

That makes the declaration enforceable against something already measured, rather
than requiring a new evidence pipeline.

## Not this change

- the TOON envelope parser, or any model-emitted contract;
- making `behavioral_safety` vary, or wiring further evidence sources;
- ranking, population, or any change to weights or the threshold;
- removing the contractless entry point, which stays for callers that supply no
  contract.

## Relates to

RISK-002, CHG-002 T4b and T4c, CHG-014, ARCH-001.
