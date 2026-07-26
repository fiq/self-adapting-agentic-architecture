# Testing

The testing approach is boundary-in and ATDD-aligned.

- Unit tests cover plain Java domain policies.
- Component tests drive the mutation loop with fake model, Git, check and
  benchmark ports.
- Integration tests cover real Git worktree, SQLite, command-check and JMH
  adapter semantics.
- Benchmarks use JMH and feed `EvaluationEvidence`.

The first approval-driving test is
`application/src/acceptanceTest/java/io/github/selfadaptingagenticarchitecture/application/MutationEvaluationLoopAcceptanceTest.java`.
It currently passes against fake ports and proves the deterministic application
orchestration. Adapter integration tests prove real Git worktree creation,
candidate commit provenance, SQLite metadata persistence, command-check
evidence and JMH benchmark evidence.
