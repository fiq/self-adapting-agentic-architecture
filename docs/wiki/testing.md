# Testing

The testing approach is boundary-in and ATDD-aligned.

- Unit tests cover plain Java domain policies, deterministic bounded mutation
  validation, mutation contract canonicalization and hard-gated phenotype
  fitness scoring.
- Component tests drive the mutation loop with fake model, Git, check and
  benchmark ports.
- Adapter unit tests cover the provider-neutral LangChain4j typed service
  boundary without live model credentials.
- Integration tests cover real Git worktree, SQLite, command-check and JMH
  adapter semantics.
- Benchmarks use JMH and feed `EvaluationEvidence`.

The first approval-driving test is
`modules/deterministic/src/acceptanceTest/java/com/dreamthought/saaa/deterministic/MutationEvaluationLoopAcceptanceTest.java`.
It currently passes against fake ports and proves the deterministic
orchestration. Acceptance `test_id`s in `specs/changes/` are written as
`<layer>/<TestClass>.<method>`, where the layer is the module directory under
`modules/`. Adapter integration tests prove real Git worktree creation,
candidate commit provenance, SQLite metadata persistence, command-check
evidence and JMH benchmark evidence. LangChain4j adapter tests prove typed
service mapping, provider-neutral `ChatModel` wiring and rejection of
unsupported model scopes without requiring network access.
