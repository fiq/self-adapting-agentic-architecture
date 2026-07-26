# self-adapting-agentic-architecture

self-adapting-agentic-architecture is a Java-first experimental platform for
evolving agentic workflows. It explores a bounded loop where a model proposes a
workflow mutation, deterministic validators evaluate the result in an isolated
Git worktree, and a multi-objective fitness score decides whether the candidate
is promoted or discarded.

The first consumer is a developer-researcher or platform maintainer who wants
auditable experiments over autonomous agent workflow changes.

## Intended thin slice

The first approved implementation slice will prove one candidate path:

```text
baseline workflow
  -> model proposes bounded mutation
  -> deterministic validation
  -> isolated Git worktree candidate
  -> candidate commit
  -> deterministic checks and JMH benchmark evidence
  -> multi-objective fitness result
  -> deterministic promote or discard
```

The model may propose mutations and repairs, but it must never approve its own
result.

## Runtime architecture diagram

```text
CLI (picocli)
  |
  v
Application use case: MutationEvaluationLoop
  |
  +--> core records and deterministic policies
  |
  +--> ports
        |-- MutationProposer          -> adapters/langchain4j
        |-- CandidateWorkspace        -> adapters/git
        |-- ExperimentMetadataStore   -> adapters/sqlite
        |-- CheckRunner               -> adapters/checks
        |-- BenchmarkRunner           -> benchmarks/JMH
```

LangChain4j is intentionally isolated behind adapter ports. The core domain is
plain Java and must not import model-provider libraries.

## Repository structure

| Path | Purpose |
|---|---|
| `core/` | Plain Java records and deterministic domain types |
| `application/` | Use-case orchestration and ports |
| `adapters/langchain4j/` | Model access, typed AI services, tool calling and retrieval integration |
| `adapters/git/` | Git worktree and commit isolation |
| `adapters/sqlite/` | SQLite experiment metadata persistence |
| `adapters/checks/` | Deterministic command execution for checks |
| `benchmarks/` | JMH benchmarks and benchmark evidence adapters |
| `cli/` | picocli command entrypoint |
| `specs/` | Capability and change specs |
| `docs/` | Architecture, validation, decisions, runbooks and wiki |

## Agent startup

Fresh agent sessions must run `.agentic-template/bin/project startup`, confirm
that `AGENTS.md` was read from disk, review the printed sequence and options,
then continue from the operating contract. For non-trivial work, read
`HANDOFF.toon`, `PROJECT_PROFILE.toon`, `docs/context-store.md` and
`.agents/knowledge/index.md` before planning or implementation.

## Documentation IA

| Need | Start with | Then read |
|---|---|---|
| Continue current work | `.agentic-template/bin/project backlog` | `HANDOFF.toon` |
| Understand architecture | `docs/architecture/module-boundaries.md` | `PROJECT_PROFILE.toon` |
| Plan a behavior change | `specs/README.md` | `specs/changes/` |
| Validate work | `docs/validation.md` | `.agentic-template/bin/project check` |
| Maintain context | `docs/context-store.md` | `.agents/knowledge/` |
| Check documentation drift | `.agentic-template/bin/project check-wiki` | `docs/wiki/` |

## Context store

The repository is the durable context store. Structure lives in `AGENTS.md`,
this README, `PROJECT_PROFILE.toon` and architecture docs. Lineage lives in
`HANDOFF.toon`, ADRs and `.agents/knowledge/`. Behavior lives in specs and
tests. Conformance lives in repository checks, CI and architecture fitness
functions.

Do not add an external vector store, database memory layer or SaaS memory layer
by default. Add one only when project evidence justifies it and
`PROJECT_PROFILE.toon` records the decision. Every non-trivial handoff should
include the spec reference, validation run, fitness-function delta and
knowledge update or no-record rationale.

## Run locally

Use the Nix development shell:

```sh
nix develop
.agentic-template/bin/project run
```

The CLI is scaffolded. The application mutation loop orchestration is
implemented and covered by a component test. Deterministic bounded mutation
validation is implemented. Git, SQLite, command-check and JMH evidence adapters
have integration coverage; the LangChain4j mutation proposer adapter has
provider-neutral typed-service coverage. Live provider selection and credential
configuration remain deferred.

## Run with containers

Not applicable for the initial architecture. This is a local experimental CLI,
not a deployable service or web application. Revisit an application image when
the CLI needs reproducible distribution outside the Nix/Gradle environment.

## Tests

```sh
.agentic-template/bin/project test
.agentic-template/bin/project lint
.agentic-template/bin/project component-test
.agentic-template/bin/project integration-test
```

`component-test` runs the first outside-in acceptance test for the mutation and
fitness loop. `integration-test` covers real Git worktree candidate creation,
SQLite experiment metadata persistence, deterministic command checks and JMH
benchmark evidence. `test` covers the provider-neutral LangChain4j mutation
proposal adapter without live provider credentials and the deterministic
bounded mutation validator.

## Configuration and environment variables

No required model-provider configuration is committed. The LangChain4j adapter
can be constructed from a provider-neutral `ChatModel`; future provider
configuration should read credentials from environment variables or local
ignored config and keep provider-specific details out of the core domain.

Initial expected variables, names still subject to approval:

| Variable | Purpose |
|---|---|
| `SAAA_MODEL_PROVIDER` | Select the LangChain4j-backed model adapter |
| `SAAA_MODEL_API_KEY` | Provider API key for model-backed mutation proposal |
| `SAAA_EXPERIMENT_DB` | SQLite database path for experiment metadata |

## Infrastructure and deployment state

Local topology is Nix plus Gradle. Deployment target is `local_cli` only.
Infrastructure as code is not applicable until a remote execution or deployment
target is selected.

## Deliberate non-goals

- OpenSearch or vector storage
- AST mutation
- LSP integration
- distributed workers
- automatic production deployment

Each is recorded as deferred in `PROJECT_PROFILE.toon` with revisit
conditions.

## Development lifecycle

Work flows from a narrative or `/ideate` into a structured change spec, then a
boundary-first acceptance test, then implementation, review, validation,
handoff and knowledge/wiki upkeep. Meaningful behavior changes must update the
spec and validation evidence in the same change.

Default integration is branch plus PR. If PR tooling is unavailable and the user
explicitly authorizes skipping the PR, use the documented fallback: keep work on
a bounded branch, run checks, request risk-appropriate actor review when agent
tooling is available, retry or get human review if a reviewer times out, address
actor or human review findings, self-review in code-review style, update
handoff with the fallback reason, validation and actor-review or substituted
human-review result, merge to `main`, then push `main` without force-pushing. If
actor review tooling is unavailable, disclose the lost actor-review gate and get
explicit user authorization for that degraded path before merge.

## Important decisions and documentation links

- `PROJECT_PROFILE.toon` records current evidence-backed architecture state.
- `docs/architecture/module-boundaries.md` records module boundaries.
- `docs/context-store.md` records the repo-native context-store model.
- `specs/capabilities/CAP-001-mutation-fitness-loop.toon` records the first
  living capability.
- `specs/changes/CHG-001-mutation-fitness-loop/` records the first proposed
  implementation slice.

## AI-assisted delivery statement

AI agents may assist delivery, propose changes and run checks. Deterministic
validation, fitness scoring, promotion and rollback remain outside model
authority. The operating contract lives in `AGENTS.md`.
