# Module Boundaries

self-adapting-agentic-architecture uses a small Clean Architecture shape:

```text
cli
  -> application
        -> core

adapters/langchain4j -> application
adapters/git        -> application
adapters/sqlite     -> application
adapters/checks     -> application
benchmarks          -> core
```

## Core

`core/` contains plain Java records and deterministic value types:

- `WorkflowGraph`
- `Mutation`
- `Candidate`
- `FitnessResult`
- `EvaluationEvidence`

Core must not import LangChain4j, picocli, SQLite, Flyway, Git command
implementation details or adapter/application packages.

## Application

`application/` owns the mutation evaluation use case and ports:

- model proposal
- mutation validation
- candidate worktree creation
- deterministic check execution
- benchmark execution
- fitness scoring
- metadata recording
- candidate promotion or discard

Application code defines orchestration contracts but does not choose model
providers, Git commands, SQLite schemas or benchmark implementations.

## Adapters

Adapters implement ports:

- `adapters/langchain4j/` owns LangChain4j model access, typed AI services,
  tool calling, retrieval integration and agent coordination.
- `adapters/git/` owns isolated Git worktree and commit behavior.
- `adapters/sqlite/` owns experiment metadata persistence and migrations.
- `adapters/checks/` owns deterministic command execution.
- `benchmarks/` owns JMH benchmarks and benchmark evidence.
- `cli/` owns picocli command parsing and user-facing command contracts.

## Fitness Function

Run:

```sh
.agentic-template/bin/project lint
```

The current architecture check fails if LangChain4j, picocli or SQLite
implementation dependencies leak into `core/` or `application/`.

## Deferred Boundaries

OpenSearch/vector storage, AST mutation, LSP integration, distributed workers
and automatic production deployment are deferred. Revisit only when the
conditions in `PROJECT_PROFILE.toon.rejected_options` are met.
