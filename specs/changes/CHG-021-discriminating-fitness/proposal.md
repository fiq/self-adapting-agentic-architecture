# CHG-021: Make the fitness function discriminate

## Why

The weighted sum could not tell two candidates apart.

`behavioral_safety` was the literal `1.0`. And every candidate that failed a gate
was recorded as `0.0`, so one that failed a single behaviour case of four and one
that failed all four were indistinguishable afterwards.

For a project whose premise is selection under deterministic fitness, that is the
part that has to work. Choosing which failure to mutate from next is the whole
question a population asks, and the answer was being erased one line before it
was written down.

## What

Two halves of the same problem.

`behavioral_safety` becomes the pass fraction of the safety probes an operator
declares with `--safety-probe`. A probe that did not run counts as failed, the
rule the gates already apply.

A failed candidate keeps its magnitude. `CON-002` and the glossary already
specify this: an invariant is binary for the promote-or-discard decision while
still carrying a magnitude, so a near miss stays distinguishable from a total
miss. Only the zeroing had to go.

## What does not change

The promote decision. A failing gate discards, un-tradeably, whatever the score
says. A discarded candidate can now record a score above the promotion threshold;
the decision promotes, not the number.

## Probes grade, required evidence gates

A probe lowers the objective and discards nothing. That distinction is the reason
probes are withheld from the deterministic-checks gate — a probe is a check, and
every failing check fails that gate, so a probe left in the list would discard
rather than grade.

A safety property that must hold belongs in a contract's `required_evidence`,
where absence or failure discards. That is what CHG-019 built, and it is where the
critical half of the safety design lives.

## Not this change

- ranking or population, which this unblocks but does not implement;
- reclaiming `reliability`, which with a single run can only observe a timeout and
  needs repeated execution;
- the richer score type `CON-002` implies for ordering across severity classes.

## Relates to

CON-002, CHG-019, PAT-004, ADR-0002.
