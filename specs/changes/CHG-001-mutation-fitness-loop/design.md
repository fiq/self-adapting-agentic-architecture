# Design

## Shape

The first slice is a local CLI over a Java application use case. The use case
depends on ports for model proposal, mutation validation, Git candidate
worktrees, deterministic checks, JMH benchmark evidence, fitness scoring and
candidate disposition.

```text
cli -> application -> core
             |
             +-> ports <- adapters/langchain4j
                     <- adapters/git
                     <- adapters/sqlite
                     <- adapters/checks
                     <- benchmarks
```

## Boundary

LangChain4j is an adapter detail. Core records and application orchestration
must not import LangChain4j, SQLite, picocli or Git implementation details.

## Reversibility

Candidate state is isolated in a Git worktree and committed before checks and
benchmarks run. Promotion starts as a local reviewed branch decision; direct
writes to `main` or production are deferred.

## Deferred capabilities

OpenSearch/vector storage, AST mutation, LSP integration, distributed workers
and automatic deployment are deliberately excluded until their revisit
conditions in `PROJECT_PROFILE.toon` are met.
