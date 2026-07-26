# Testing

The testing approach is boundary-in and ATDD-aligned.

- Unit tests cover plain Java domain policies.
- Component tests drive the mutation loop with fake model, Git, check and
  benchmark ports.
- Integration tests are deferred until Git and SQLite adapters exist.
- Benchmarks use JMH and feed `EvaluationEvidence`.

The first approval-driving test is
`application/src/acceptanceTest/java/io/github/selfadaptingagenticarchitecture/application/MutationEvaluationLoopAcceptanceTest.java`.
It is expected to fail until `CHG-001` is approved and implemented.
