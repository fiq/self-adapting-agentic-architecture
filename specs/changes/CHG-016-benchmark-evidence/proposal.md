# CHG-016: Wire a real benchmark evidence source into cost_latency_budget

## Why

`subject.objective.cost_latency_budget` is one of the five weighted fitness
objectives, worth `0.20`. `PhenotypeBridgeScorer.budgetScore` already computes
it correctly from `EvaluationEvidence.benchmarks()` against
`ScoringConfig.benchmarkBudgets()` — `PhenotypeBridgeScorerTest
.scoresCostLatencyFromTheWorstBenchmarkAgainstItsBudget` proves it varies given
real input. The objective was not inert by design; it was starved of input.
`EvolveRunner` wired the benchmark runner to the constant `candidate ->
List.of()` and `ScoringConfig` to an empty budget map, and `modules/cli`
had no Gradle dependency on `modules/benchmarks`, so no CLI run could ever
supply a benchmark measurement. `JmhBenchmarkRunner`, which implements the
`BenchmarkRunner` port with real JMH evidence, existed and was
integration-tested, but nothing in the loop ever called it.

## What this change delivers

`EvolveRunner` gains an injectable `BenchmarkRunner` (a fifth constructor
parameter, with every existing constructor still defaulting to the same
constant empty list as before) and `EvolveRunRequest` gains a
`benchmarkBudgets` map (an additional record component, with every existing
shorter constructor still defaulting to an empty map). Both changes are
purely additive: every existing caller of `EvolveRunner` and
`EvolveRunRequest` — `BenchmarkCommand`, `SaCommand`, `EvolveMcpServer`, and
their tests — compiles and behaves unchanged.

`modules/cli/build.gradle.kts` gains a dependency on `:benchmarks`.
`EvolveCommand` gains two repeatable options, `--benchmark
name=jmh-include-regex` and `--benchmark-budget name=value`. When neither is
given, `EvolveCommand` still passes the same constant empty benchmark runner
and empty budget map as before — the stock `saaa-evolve` example in the
README is unaffected. When `--benchmark` is given, `EvolveCommand` constructs
a real `JmhBenchmarkRunner` from `:benchmarks` and passes it, and the
configured budgets, through to `EvolveRunner`.

`EvolveRunner` itself does not gain a dependency on `:benchmarks`. It only
ever sees the `BenchmarkRunner` port (already visible through its existing
`:deterministic` dependency); the concrete `JmhBenchmarkRunner` is
constructed in `:cli`, the composition root, and handed down. `adapters` and
`benchmarks` remain siblings that never depend on each other. This is why
`EvolveMcpServer`, which also lives in `adapters` and also constructs an
`EvolveRunner`, does not need to know anything changed: it keeps using the
no-argument constructor, which still defaults to the inert benchmark runner.

## What this change does not deliver

`JmhBenchmarkRunner.runBenchmarks(Candidate)` does not read the candidate at
all — it never has. It runs whatever fixed JMH benchmark classes are named in
its `BenchmarkDefinition` list, compiled ahead of time into `:benchmarks`.
Today that is exactly one class, `WorkflowGraphBenchmark`, benchmarking a
constructor call on SAAA's own domain code. So `cost_latency_budget` can now
vary and discriminate candidates, but only in the sense that a caller who
configures `--benchmark`/`--benchmark-budget` chooses a fixed measurement to
compare against a fixed budget on every run; it is not yet a measurement of
whatever the candidate's mutated file contains. Making the benchmark runner
candidate-aware, and adding benchmark classes beyond `WorkflowGraphBenchmark`,
are both out of scope here.

`PhenotypeBridgeScorer`, `PhenotypeFitnessScorer`, `FitnessScorer` and
`MutationEvaluationLoop` are unchanged. `budgetScore` already behaved
correctly; the gap was entirely upstream of the scorer, in what evidence ever
reached it.

## Architecture boundary check

Before writing any code, `docs/architecture/module-boundaries.md`, the README
and `docs/wiki/architecture.md` were re-read together with
`.agentic-template/bin/check-architecture-boundaries`. The check enforces
JMH implementation dependencies staying confined to `modules/benchmarks/`
text-wise, and fails only if a scanned layer directory goes missing; it does
not itself encode module-to-module Gradle edges. The module-boundaries
diagram and the README both already described `cli` and `adapters` as the two
layers that "implement ports" alongside `benchmarks`, and `cli` as the picocli
entrypoint — the composition root. Adding `:cli -> :benchmarks` follows the
same shape as the pre-existing, if previously undocumented, `:cli ->
:adapters` edge: the entry point composes concrete port implementations from
more than one adapter-shaped module. `.agentic-template/bin/project lint`
passes after the change (`ARCHITECTURE BOUNDARIES OK`). `adapters` was
deliberately **not** given a dependency on `:benchmarks` — see "What this
change does not deliver" and the non-goal in `change.toon` — so `EvolveRunner`
keeps depending only on the `BenchmarkRunner` port, never on a concrete JMH
class.

## Test-first and mutation-check evidence

A component test,
`adapters/evolve/EvolveRunnerTest.discriminatesCandidatesByAConfiguredBenchmarkBudget`,
was written first and confirmed to fail to *compile* — the injectable
`BenchmarkRunner` constructor and the `benchmarkBudgets` request field did not
exist yet — before either was added. Once added, the test passes: an
in-budget injected benchmark leaves `cost_latency_budget` at `1.0`, an
over-budget one drops it to `budget / measured` and lowers the aggregate
score, over a real fixture run through Git, the check runner and the fitness
scorer.

A CLI acceptance test,
`cli/EvolveCommandAcceptanceTest.wiresARealBenchmarkRunnerSoAnOverBudgetMeasurementDiscardsAnOtherwisePromotingCandidate`,
exercises the real `JmhBenchmarkRunner` path end to end through
`saaa-evolve --benchmark ... --benchmark-budget ...`, with the budget set
astronomically small so the assertion (`DISCARD` instead of `PROMOTE`) does
not depend on host-specific JMH timing.

Both wiring points were then deliberately reverted to their prior inert
behaviour (`candidate -> List.of()` / `Map.of()` in `EvolveRunner.run`, and
the constant empty `BenchmarkRunner` in `EvolveCommand`), the corresponding
test was confirmed to fail, and the file was restored byte-identical
(`diff` against a saved copy) before moving on. See the change's task list and
the delivery handoff for the exact commands run.

## Relates to

Q-004 (initial fitness objectives and weights, which named "cost and latency
budgets" as intended weighted evidence from the start), SYS-001, ARCH-001, and
the living capability `specs/capabilities/CAP-001-mutation-fitness-loop.toon`.
