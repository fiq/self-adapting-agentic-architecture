# CHG-016 design

## What was wired before

```text
EvolveCommand (cli)
  -> new EvolveRunner()                       // no-arg, default wiring
       -> run(EvolveRunRequest, reporter)
            -> new MutationEvaluationLoop(
                   ...,
                   candidate -> List.of(),     // BenchmarkRunner: constant, empty
                   new PhenotypeBridgeScorer(
                       ...,
                       new ScoringConfig(..., Map.of())),  // benchmarkBudgets: constant, empty
                   ...)

PhenotypeBridgeScorer.budgetScore(evidence.benchmarks())
  -> always iterates an empty list -> always returns 1.0
```

`JmhBenchmarkRunner implements BenchmarkRunner` existed in `modules/benchmarks`
and was integration-tested in isolation
(`JmhBenchmarkRunnerIntegrationTest.convertsJmhPrimaryResultToBenchmarkEvidence`),
but nothing constructed one on the path from `saaa-evolve` to the loop, and
`modules/cli/build.gradle.kts` had no dependency that would let it.

## What this change adds

```text
EvolveCommand (cli, now depends on :benchmarks)
  benchmarks.isEmpty()
    ? candidate -> List.of()                  // unchanged default
    : new JmhBenchmarkRunner(definitions)      // real evidence, only when --benchmark is given
  -> new EvolveRunner(benchmarkRunner)          // new constructor overload
       -> run(EvolveRunRequest{..., benchmarkBudgets}, reporter)
            -> new MutationEvaluationLoop(
                   ...,
                   this.benchmarkRunner,                    // now injected
                   new PhenotypeBridgeScorer(
                       ...,
                       new ScoringConfig(..., request.benchmarkBudgets())),
                   ...)
```

`PhenotypeBridgeScorer.budgetScore` is untouched. It already iterated
`evidence.benchmarks()` against `config.benchmarkBudgets()` correctly; the
fix is entirely about what reaches it.

## Why the change sits in `EvolveRunner` and `EvolveRunRequest`, not just `EvolveCommand`

The task description that started this framed the fix as ":cli has no
dependency on :benchmarks", which is true, but the actual constant wiring —
`candidate -> List.of()` and `Map.of()` — lives inside `EvolveRunner.run`,
which is in `modules/adapters`, not `modules/cli`. `EvolveCommand` in `:cli`
only ever calls `new EvolveRunner().run(request, reporter)`; it never touches
`MutationEvaluationLoop` construction directly.

Two shapes were considered:

1. **Move the loop-construction responsibility into `:cli`.** Rejected: it
   would duplicate `EvolveRunner`'s git-root resolution, symlink checks,
   proposer resolution and retrieval wiring into the CLI layer, or force
   `MutationEvaluationLoop` construction itself to move — a far larger,
   unbounded change touching code this task's constraints explicitly protect
   (`MutationEvaluationLoop`).
2. **Give `EvolveRunner` an injectable `BenchmarkRunner` and
   `EvolveRunRequest` an injectable budgets map, and let the composition root
   supply both.** This is what shipped. `EvolveRunner` keeps owning loop
   construction; it just stops hard-coding one of the loop's ports.

Shape 2 also answers the harder architecture question directly: `EvolveRunner`
depends on `BenchmarkRunner`, a `:deterministic` port `adapters` already
depends on, never on `JmhBenchmarkRunner`, a concrete `:benchmarks` class. The
`:benchmarks` dependency lives only in `:cli`'s build file, where the concrete
class is actually referenced.

## Backward compatibility of the two constructor/record changes

`EvolveRunner` previously had four constructors, all delegating down to one
that took `(profileRegistry, clock, retrievalResolver, memoryResolver)`. That
constructor now also takes `benchmarkRunner`; every other constructor,
including the no-arg one, still delegates to it with `candidate -> List.of()`
supplied automatically. A new `EvolveRunner(BenchmarkRunner)` convenience
constructor was added for `:cli`'s use, mirroring the no-arg constructor's
defaults for everything else. No existing call site — `BenchmarkCommand`,
`SaCommand`, `EvolveMcpServer`, `EvolveMcpAcceptanceTest`,
`SaCommandAcceptanceTest`, `EvolveRunnerTest`'s existing tests — needed to
change.

`EvolveRunRequest` previously had three constructors, all delegating to an
eight-component canonical (record) constructor. `benchmarkBudgets` is a new
ninth component; every shorter constructor now delegates with `Map.of()`
appended, so no existing call site needed to change. `EvolveCommand` and the
new tests are the only callers of the new nine-argument form.

## Test-first sequence actually run

1. Wrote `EvolveRunnerTest.discriminatesCandidatesByAConfiguredBenchmarkBudget`
   against the intended new API (`new EvolveRunner(BenchmarkRunner)`, the
   nine-argument `EvolveRunRequest`). Ran `gradle :adapters:test --tests
   EvolveRunnerTest`: four compile errors, all "no suitable constructor
   found" for the two new call shapes — failing for the right reason, the
   capability did not exist yet.
2. Added the constructor/record changes described above. Reran: build
   succeeded, three tests passed including the new one.
3. Reverted `EvolveRunner.run`'s two call sites back to
   `candidate -> List.of()` / `Map.of()`. Reran: the new test failed at its
   assertion (`AssertionError`), the other two still passed. Restored the
   file; `diff` against the pre-mutation copy showed no difference.
4. Added `modules/cli/build.gradle.kts`'s `:benchmarks` dependency and
   `EvolveCommand`'s `--benchmark`/`--benchmark-budget` options and wiring.
   Wrote
   `EvolveCommandAcceptanceTest.wiresARealBenchmarkRunnerSoAnOverBudgetMeasurementDiscardsAnOtherwisePromotingCandidate`
   and ran `gradle :cli:acceptanceTest`: passed immediately, since the
   implementation was already in place by that point in the work (the
   compile-first "red" step from step 1 already having proven the pattern).
5. Reverted `EvolveCommand.call()` to always use the constant empty
   `BenchmarkRunner`. Reran: the new acceptance test failed (`DISCARD`
   expected, `PROMOTE` observed). Restored the file; `diff` showed no
   difference.
6. Ran `.agentic-template/bin/project lint`: `ARCHITECTURE BOUNDARIES OK`.

## What this deliberately does not decide

Whether `JmhBenchmarkRunner` should become candidate-aware — recompiling or
re-benchmarking something derived from the candidate's realized diff, rather
than a fixed set of classes compiled ahead of time — is a larger design
question involving how (or whether) a text-based workflow mutation can ever
correspond to a JMH-benchmarkable unit at all. This change only makes the
existing, non-candidate-aware `JmhBenchmarkRunner` reachable.
