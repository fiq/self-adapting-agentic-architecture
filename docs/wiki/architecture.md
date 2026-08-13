# Architecture

self-adapting-agentic-architecture is a local Java CLI with Clean Architecture
module boundaries.

All Java lives under `modules/`, and layers are named for what they may know.

```text
cli -> deterministic -> domain
             ^
             |
  adapters and benchmarks implement ports
```

`domain` declares no dependencies at all, so the inward rule is a compile error.
`deterministic` holds validation, scoring, promotion and ports; nothing
provider-aware or nondeterministic belongs there.

| Path | Purpose |
|---|---|
| `modules/domain/` | plain Java records and value types; no dependencies at all |
| `modules/deterministic/` | validation, scoring, promotion and ports |
| `modules/adapters/` | model access, Git worktrees, SQLite, Neo4j, command execution, MCP, journal |
| `modules/benchmarks/` | JMH benchmarks and the benchmark evidence adapter |
| `modules/cli/` | picocli entrypoint |

## Loop wiring

```text
CLI (picocli) / MCP stdio server
  |
  v
MutationEvaluationLoop  (deterministic)
  |
  +--> domain records and deterministic policies
  |
  +--> ports
        |-- MutationProposer          -> adapters/fixture, adapters/langchain4j
        |-- AgentHarness             -> adapters/acp, adapters/CLI, adapters/providers
        |-- EvidenceRetriever         -> adapters/retrieval, adapters/neo4j
        |-- CandidateWorkspace        -> adapters/git
        |-- CheckRunner               -> adapters/checks
        |-- BenchmarkRunner           -> benchmarks/JMH (not wired into the CLI)
        |-- FitnessScorer             -> deterministic/PhenotypeBridgeScorer
        |-- ExperimentMetadataStore   -> adapters/sqlite
        |-- CandidateDecisionSink     -> adapters/journal
```

`:cli` has no Gradle dependency on `:benchmarks`, and `EvolveRunner` supplies a
constant empty benchmark list, so no CLI run produces benchmark evidence.
`JmhBenchmarkRunner` exists and is integration-tested but nothing in the loop
calls it.

Check execution records timeouts as the structured `CheckStatus.TIMED_OUT`
value. Reliability therefore ignores candidate-controlled check summaries and
cannot be spoofed by a passing script that prints the words "timed out".

Extension points that do not touch the loop: register a proposer in
`ProposerProfileRegistry` for a new `--profile`; write a `<name>.sh` for a new
behaviour case; supply benchmark budgets through `ScoringConfig` once something
produces benchmark evidence. `CandidateDecisionSink` exposes no merge
operation, so promotion cannot become an automatic merge through adapter
configuration.

The key architecture rule is `ARCH-001`: LangChain4j is an adapter detail, and
validation, fitness scoring, promotion and rollback remain deterministic Java
decisions. `RISK-001` tracks the model self-approval failure mode.

## Harness-agnostic southbound execution

`AgentHarness` is the neutral port for invoking an external agent or model. Its
request carries the selected route, isolated workspace, capability allowlist,
expected output schema and remaining resource budget. Its result carries only
provider-neutral status and usage evidence; a completed result is still merely
an agent proposal.

```text
SAAA route + budget + worktree
             |
       AgentHarness port
       /       |       \
    ACP     OpenCode   process/API
    Goose   Codex      local model
             |
 deterministic validation -> fitness -> promote/discard
```

Goose, OpenCode, Codex, Claude and direct model APIs are intentionally adapters,
not architectural authorities. This keeps the harness swappable and lets
experiments compare agent engines under the same candidate, resource and
fitness policy. `CHG-006` defines the boundary and `CHG-009` provides the first
ACP-over-stdio adapter. Real OpenCode, Goose, Codex or Claude command coverage
remains opt-in because it requires locally installed agents and credentials.

Evolutionary operator policy is captured in
[`docs/architecture/evolutionary-operators.md`](../architecture/evolutionary-operators.md):
mutation is a targeted behavioral variation, not a patch; the realization is
the candidate Git diff; crossover starts as conceptual trait recombination over
evaluated parents, not raw diff merging. `CON-001` defines the initial mutation
operator enum, including `hill-climb` and `exploratory-leap` search posture
operators. `Q-002`, `Q-004`, `Q-005` and `Q-006` track the open contract,
fitness, crossover and search posture details.

Run `.agentic-template/bin/project lint` to enforce the first boundary fitness
function.
