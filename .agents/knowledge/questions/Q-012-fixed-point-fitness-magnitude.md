---
id: Q-012
type: question
title: Should the fitness magnitude be a scaled integer rather than a BigDecimal
status: open
summary: Renormalising introduced a division that turns clean rational inputs into repeating decimals, so some rounding is unavoidable. A scaled integer makes addition and multiplication exact, leaves exactly one rounding point, and deletes the equals/compareTo normalisation BigDecimal forces. It is a persisted, compared value type, so it carries the CHG-023 blast radius and its scale must be fingerprinted.
owners:
  - architect
relates_to:
  - CON-002
  - RISK-007
decisions:
  - ADR-0002
evidence:
  - modules/domain/src/main/java/com/dreamthought/saaa/domain/FitnessScore.java
  - modules/deterministic/src/main/java/com/dreamthought/saaa/deterministic/PhenotypeFitnessScorer.java
review_after: 2026-11-30
---

# Fixed-Point Fitness Magnitude

## What prompted it

Renormalising the weighted sum over measured objectives introduced a division.
Four of the five objectives are fractions with small denominators — passed cases
over cases, passed runs over runs, lines over a line budget — and the weights are
two decimal places. The inputs are almost all exact rationals. The division is
what turns them into values like `0.4142857142857143`, which is `29/70` and has
no finite decimal expansion.

So rounding is unavoidable whatever the representation. The question is only
where it happens and under what rule. Today it is a `MathContext.DECIMAL64`
inside one expression: a policy decision wearing the clothes of an implementation
detail.

## The case for a scaled integer

- Addition and multiplication become exact, so **exactly one operation rounds** —
  the renormalising division. One nameable rounding point rather than an ambient
  context.
- It deletes existing complexity. `FitnessScore` currently carries a paragraph of
  javadoc about `BigDecimal.equals` distinguishing `0.5` from `0.50`, and calls
  `stripTrailingZeros` to force `equals` and `compareTo` to agree. With a scaled
  `long` that problem stops existing rather than being managed.
- The SQLite column is `real`, so the magnitude currently round-trips through a
  float. An integer column is exact.
- Boundary behaviour becomes exact rather than delicate. The promotion threshold
  is `800_000`, not a value that two arithmetic paths might disagree about — the
  defect found while retuning the boundary corpus entry.

## How much precision

Six decimal places is generous; four would serve.

- The finest genuine input step is about `0.0001`, from a line budget or a
  reliability run count at its cap of 50.
- `RISK-007`'s near-miss case needed four decimals to separate `0.5949` from
  `0.5851`.

Precision beyond the granularity of the inputs is false precision. Sixteen digits
of a renormalised quotient implies a resolution the measurement does not have,
and in ranking that is worse than a tie, because a tie is visibly a tie.

## What makes it a change rather than an edit

`FitnessScore` is persisted, transported and compared: SQLite, ledger envelopes,
the MCP surface, the console reporter and the evolutionary-memory policy. That is
the `CHG-023` blast radius.

**The scale and the rounding rule must be part of `ScoringContext`.** Changing
either changes what a magnitude means, which is precisely what that fingerprint
exists to catch. Omitting them would reintroduce, one level down, the defect the
fingerprint was built to close.

## Not yet

Deferred deliberately rather than forgotten. The branch it would land on is green
and closeable, and this is its own change with its own review.
