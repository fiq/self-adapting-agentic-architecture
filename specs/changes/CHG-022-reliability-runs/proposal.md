# CHG-022 — Give the reliability objective something to measure

## Why

`reliability` asked one question: did anything time out? No candidate that cleared the
deterministic-checks gate could ever answer yes, because a timed-out check is not a passed check and
the gate had already discarded it. The objective restated its own gate.

The consequence is arithmetic. For any candidate that promotes, `task_success` is 1.0 by
construction — the gate requires every declared behaviour case to pass — and `reliability` is 1.0 by
construction for the same kind of reason. That is 0.60 of the weight pinned at a constant. With no
benchmarks and no probes declared, `cost_latency_budget` and `behavioral_safety` sit at 1.0 too,
giving a floor of 0.90 against a 0.80 threshold.

So everything that clears the gates promotes, and among the promoted the only thing that moves is
`parsimony` at 0.10. Default fitness is a gate plus a code-size tiebreak.

CHG-021 made the score discriminate among failures, which is where a population chooses what to
mutate from next. This is the other half: discriminating among winners, which is where a population
chooses parents.

## What changes

Each behaviour case may run more than once. The first run gates exactly as before. The repeats are
withheld from the deterministic-checks gate and graded: `reliability` becomes the pass fraction
across all runs.

A candidate passing eight of ten runs is eligible and scores 0.8. One passing all ten scores 1.0.
Both promote, and they no longer score the same.

## What does not change

The decision. A candidate that fails its canonical run is discarded exactly as before, and no number
of passing repeats buys it a promotion. Withholding applies to the gate, never to the record: every
run stays in the evidence, so a lowered score can be traced to the run that lowered it.

Callers who declare no repeats are unaffected. At one run per case the objective keeps its previous
meaning, timeout rule included.

## The trap this avoids

Making `reliability` a pass fraction at a single run would have made it a second copy of
`task_success`, which is already the pass fraction of the same behaviour cases. It would have
double-weighted failing a case rather than measuring anything new. The golden corpus caught that
during implementation.
