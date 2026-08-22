# Testing

The testing approach is boundary-in and ATDD-aligned.

```sh
.agentic-template/bin/project test
.agentic-template/bin/project lint
.agentic-template/bin/project component-test
.agentic-template/bin/project integration-test
.agentic-template/bin/project graphrag-integration-test
```

- Unit tests cover plain Java domain policies, deterministic bounded mutation
  validation, mutation contract canonicalization and hard-gated phenotype
  fitness scoring. Scorer coverage includes a jqwik property suite with fixed
  numeric seeds and a nine-entry golden verdict corpus in `GoldenCorpus.java`
  holding entries either side of `PROMOTION_THRESHOLD`. `ContractAwareFitnessTest`
  covers the contract-aware entry point, and two characterisation tests pin what
  must not move while it exists:
  `PhenotypeFitnessScorerTest.contractlessScoringPreservesTheExistingGates` holds
  the wired two-argument path, and
  `PhenotypeBridgeScorerTest.theWiredBridgeStillUsesTheContractlessEntryPoint`
  asserts that the wired port still cannot carry a contract, so `RISK-002`'s
  remaining gap fails a test rather than being assumed.
- Component tests drive the mutation loop with fake model, Git, check and
  benchmark ports, and drive the CLI outside-in, including a WireMock-backed
  live-proposer run that needs no real credentials.
- Adapter unit tests cover the provider-neutral LangChain4j typed service
  boundary without live model credentials, and the MCP tool contract: closed
  input schema, rejection of fields that would force promotion, override a gate,
  request a merge or carry credentials, API-key scrubbing and non-terminating
  tool errors.
- Integration tests cover real Git worktree, SQLite, command-check and JMH
  adapter semantics. The Neo4j graph tests are opt-in behind
  `SAAA_NEO4J_INTEGRATION`, so a plain `integration-test` skips them;
  `graphrag-integration-test` sets it, starts Compose and shuts it down.
- JMH benchmarks exist and `JmhBenchmarkRunner` has integration coverage, but
  the CLI does not wire them, so they do not currently feed
  `EvaluationEvidence` on any runnable path.
- Three tests hold the no-merge boundary:
  `hasNoFlagThatTurnsAnyScoreIntoAMerge`,
  `promotedCandidateLandsAsABranchPointerAndNotAMerge` and
  `DeterministicLayerHasNoMergeReferenceTest`.

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
