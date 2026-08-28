# Architecture

self-adapting-agentic-architecture (SAAA) is a local Java CLI built with Clean
Architecture module boundaries.

All Java lives under `modules/`, and layers are named for what they may know:

```text
cli -> deterministic -> domain
 |           ^
 |           |
 +--> adapters and benchmarks implement ports
```

- `cli` is the composition root (the single place where the layers are wired
  together). It is the only layer that depends on both `adapters` and
  `benchmarks` at once, so it is the one place that can wire a chosen
  `BenchmarkRunner` implementation — the constant empty one, or
  `JmhBenchmarkRunner` from `:benchmarks` when `--benchmark` is given — into
  `EvolveRunner`.
- `adapters` and `benchmarks` never depend on each other.
- `domain` declares no dependencies at all, so the inward rule is a compile
  error when broken.
- `deterministic` holds validation, scoring, promotion and ports; nothing
  provider-aware or nondeterministic belongs there.

| Path | Purpose |
|---|---|
| `modules/domain/` | plain Java records and value types; no dependencies at all |
| `modules/deterministic/` | validation, scoring, promotion and ports |
| `modules/adapters/` | model access, Git worktrees, SQLite, Neo4j, command execution, MCP, journal |
| `modules/benchmarks/` | JMH benchmarks and the benchmark evidence adapter |
| `modules/cli/` | picocli entrypoint |

## Loop wiring

The evaluation loop runs one mutation through deterministic policy. It talks to
the outside world only through ports, and the arrows below show what fills each
one. Most are filled by an adapter, but not all: scoring stays inside
`deterministic` deliberately, because a model provider must never be able to
grade its own candidate.

```text
CLI (picocli) / MCP stdio server
  |
  v
MutationEvaluationLoop  (deterministic)
  |
  +--> domain records and deterministic policies
  |
  +--> ports
        |-- MutationProposer          -> adapters/fixture, adapters/langchain4j, adapters/acp
        |-- AgentHarness             -> adapters/acp, adapters/CLI, adapters/providers
        |-- EvidenceRetriever         -> adapters/retrieval, adapters/neo4j
        |-- CandidateWorkspace        -> adapters/git
        |-- CheckRunner               -> adapters/checks
        |-- BenchmarkRunner           -> benchmarks/JMH, wired by EvolveCommand when --benchmark is given
        |-- FitnessScorer             -> deterministic/PhenotypeBridgeScorer
        |-- ExperimentMetadataStore   -> adapters/sqlite
        |-- CandidateDecisionSink     -> adapters/journal
        |-- SourceStructureInspector  -> adapters/parser (no frontend written yet)
```

Notes on the ports above:

- `SourceStructureInspector` is the newest port and the only one nothing fills
  yet. It reads a source file into `SourceStructure`, the one layered model
  every structural capability consumes. A frontend fills the layers it can:
  `SYNTAX` needs only a grammar, `SYMBOL` needs a language tool, and which of
  them a frontend filled is data on the result rather than a different type, so
  a capability asks "was the symbol layer filled" instead of "which parser
  answered". A capability needing a layer nobody filled is unsupported for that
  language rather than quietly given less than it asked for.

  What makes it unusual is that the contract is executable. A frontend is
  supported exactly when it passes `SourceStructureConformance`, the shared
  suite in the deterministic module's test fixtures, against fixtures in its own
  language. That is deliberate: reviewing an adapter for a language you do not
  read is not a control, so the suite is what a contributed frontend is judged
  by. A contributor supplies six strings and inherits every assertion. See
  `ADR-0005` and `CHG-025`.

- `PhenotypeFitnessScorer` also exposes a contract-aware entry point that takes
  a `MutationContract` and a typed required-evidence channel. The bridge above
  does not call it. The `FitnessScorer` port has no parameter for a contract, so
  the wired path reaches only the contractless entry point. See `RISK-002` and
  `CHG-014`.
- `:cli` has a Gradle dependency on `:benchmarks`. `EvolveRunner` takes an
  injected `BenchmarkRunner` rather than constructing one, so `EvolveCommand`
  composes the concrete choice:
    - the constant empty list by default; or
    - a real `JmhBenchmarkRunner` when `--benchmark name=jmh-include-regex` is
      given, with budgets threaded through `--benchmark-budget name=value` into
      `ScoringConfig`.
- `JmhBenchmarkRunner` is integration-tested and now reachable from a CLI run.
  It still does not inspect the candidate: `modules/benchmarks` compiles one
  fixed JMH benchmark class, `WorkflowGraphBenchmark`, ahead of any candidate,
  so what gets measured is a fixed microbenchmark of SAAA's own domain code,
  not whatever the candidate's mutated file contains. See `CHG-016`.
- Check execution records timeouts as the structured `CheckStatus.TIMED_OUT`
  value. Reliability therefore ignores candidate-controlled check summaries and
  cannot be spoofed by a passing script that prints the words "timed out".

Extension points that do not touch the loop:

- Register a proposer in `ProposerProfileRegistry` for a new `--profile`.
- Write a `<name>.sh` for a new behaviour case.
- Add a JMH benchmark class to `modules/benchmarks` and name it with
  `--benchmark`/`--benchmark-budget` for `cost_latency_budget` to compare
  against.
- `CandidateDecisionSink` exposes no merge operation, so promotion cannot
  become an automatic merge through adapter configuration.

The key architecture rule is `ARCH-001`: LangChain4j is an adapter detail, and
validation, fitness scoring, promotion and rollback remain deterministic Java
decisions. `RISK-001` tracks the model self-approval failure mode.

## Harness-agnostic southbound execution

`AgentHarness` is the neutral port for invoking an external agent or model. SAAA
calls the model through this port.

- Its request carries the selected route, isolated workspace, capability
  allowlist, expected output schema and remaining resource budget.
- Its result carries only provider-neutral status and usage evidence; a
  completed result is still merely an agent proposal.

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

- The live `acp` profile is composed into `MutationProposer` at the adapter
  edge. It receives the prepared retrieval bundle as advisory context, invokes
  the agent in a disposable proposal workspace, requires a completed
  provider-neutral result, and fails before candidate creation when the agent
  fails, times out or exhausts its invocation budget.
- This is the first end-to-end harness path; it intentionally does not add
  provider selection or automatic merge authority.
- The ACP adapter can reject a request whose configured token, credit or
  wall-clock allowance is already exhausted.
- ACP 0.14.0 does not expose provider billing or token usage through this
  adapter, so runtime usage is currently authoritative only for wall-clock
  duration; token, credit and retry values are request policy and audit inputs
  until a richer usage contract is added.
- Goose, OpenCode, Codex, Claude and direct model APIs are intentionally
  adapters, not architectural authorities. This keeps the harness swappable and
  lets experiments compare agent engines under the same candidate, resource and
  fitness policy.
- `CHG-006` defines the boundary and `CHG-009` provides the first
  ACP-over-stdio adapter.
- Real OpenCode, Goose, Codex or Claude command coverage remains opt-in because
  it requires locally installed agents and credentials.

## Interactive harness control plane

`saaa sa` is the SAAA-owned interactive client for an operator at a terminal.

- Its deterministic session state records an explicit target kind
  (`HARNESS_WORKFLOW` or `CODE`) and an explicit registered proposer route.
- The client can inspect capabilities and skills without invoking an agent; only
  `evolve` dispatches through the existing `EvolveRunner` path.

```text
operator -> sa session state -> target + route -> EvolveRunner
                                                    |
                                              AgentHarness / proposer
                                                    |
                          deterministic validation -> fitness -> promote/discard
```

- Both target kinds currently use bounded whole-file realization plus declared
  behaviour checks. This is intentionally not AST-aware code evolution.
- The session exposes route choices but does not implement automatic resource or
  ethical routing; `Q-010` and `Q-011` remain open.
- MCP remains the agent-host integration surface rather than a competing session
  transport.

## Agent-session and multi-model efficiency

This section describes how the agentic team operating on this repository works.
It is not implemented SAAA runtime behaviour.

Why this is not runtime behaviour: `AcpAgentHarness.run` constructs a new
transport, client and session for every invocation and closes the client before
returning, so nothing in the harness retains a provider session across runs.
Runtime session retention is unimplemented and is not required by any current
change.

As an operating practice:

- A provider session is disposable execution state, not repository memory.
- One bounded objective with the same role and permission scope may retain a
  session so stable context can be cached.
- Start a new session when the objective or privacy changes, a transport failure
  occurs, the response reserve is exhausted, or independent review is required.
- Durable context is a source-referenced packet plus the handoff, never an
  opaque transcript.

Roles are explicit:

- A strong lead resolves ambiguity and synthesises.
- A midrange model handles bounded implementation or documentation.
- A lesser/local model handles mechanical work.
- An independent reviewer gets a clean session and focused diff.
- Route/model identity, budget and observed usage are audit evidence when
  exposed, not fitness or promotion inputs.

The operational rules live in the [model-routing and session-efficiency
policy](../../.agents/coordination/MODEL_ROUTING_POLICY.md); automatic routing
remains deferred by `Q-010` and preference constraints by `Q-011`.

Evolutionary operator policy is captured in
[`docs/architecture/evolutionary-operators.md`](../architecture/evolutionary-operators.md):

- Mutation is a targeted behavioral variation, not a patch; the realization is
  the candidate Git diff.
- Crossover starts as conceptual trait recombination over evaluated parents, not
  raw diff merging.
- `CON-001` defines the initial mutation operator enum, including `hill-climb`
  and `exploratory-leap` search posture operators.
- `Q-002`, `Q-004`, `Q-005` and `Q-006` track the open contract, fitness,
  crossover and search posture details.

Run `.agentic-template/bin/project lint` to enforce the first boundary fitness
function.
