# Module Boundaries

self-adapting-agentic-architecture uses a small Clean Architecture shape. All
Java lives under `modules/`, and each layer is named for what it is allowed to
know rather than for its position in a stack.

```text
modules/cli
  -> modules/deterministic
        -> modules/domain
  -> modules/adapters      (implements ports: proposer, Git, SQLite, MCP, journal)
  -> modules/benchmarks    (implements the BenchmarkRunner port with real JMH evidence)

modules/adapters    -> modules/deterministic
modules/benchmarks  -> modules/deterministic, modules/domain
```

Dependencies point inward. `domain` is the innermost layer and depends on
nothing at all — its Gradle build file declares no dependencies, so the rule is
a compile error rather than a convention.

`cli` is the only layer permitted to depend on more than one port-implementing
layer. It is the composition root and entry point: it is the one place that may
know about both `adapters` and `benchmarks` concrete classes at once, in order
to wire a chosen `BenchmarkRunner` implementation — the constant empty one, or
`JmhBenchmarkRunner` from `:benchmarks` — into `EvolveRunner`, which only ever
sees the `BenchmarkRunner` port. Neither `adapters` nor `benchmarks` may depend
on the other, so `EvolveRunner` (in `adapters`) takes its `BenchmarkRunner` as a
constructor parameter rather than constructing a JMH-backed one itself.

## Domain

`modules/domain/` contains plain Java records and deterministic value types —
the vocabulary of what is being evolved, not the machinery that evolves it:

- `WorkflowGraph`
- `Mutation`, `MutationContract`, `MutationOperatorType`
- `Candidate`
- `FitnessResult`, `FitnessObjective`
- `EvaluationEvidence`

Domain must not import LangChain4j, picocli, SQLite, Flyway, Git command
implementation details, or any `deterministic`, `adapters` or `cli` package.

## Deterministic

`modules/deterministic/` owns the mutation evaluation use case, its ports, and
every decision that must be repeatable:

- mutation contract canonicalization and validation
- deterministic check execution and benchmark orchestration ports
- phenotype fitness scoring and hard gates
- promotion or discard selection
- metadata recording
- exact/vector/graph rank fusion, capsule compilation, retrieval budgets and
  evolutionary-memory projection contracts

The name is the rule. Nothing provider-aware, network-bound, clock-dependent or
otherwise nondeterministic belongs in this layer. A model may propose or repair
a mutation, but validation, scoring, promotion and rollback are decided here and
only here. That is what keeps `RISK-001` — model self-approval — closed.

This layer defines orchestration contracts but does not choose model providers,
Git commands, SQLite schemas or benchmark implementations.

## Adapters

`modules/adapters/` implements the ports, one package per concern:

- `adapters/langchain4j` owns LangChain4j model access, typed AI services,
  tool calling, retrieval integration and agent coordination.
- `adapters/git` owns isolated Git worktree and commit behavior.
- `adapters/sqlite` owns experiment metadata persistence and migrations.
- `adapters/neo4j` owns the rebuildable graph/vector projection and explicit
  outcome relationships.
- `adapters/repository` owns deterministic extraction of repository knowledge,
  Java type/import and test-reference facts.
- `adapters/retrieval` composes local graph, embedding, capsule-cache and audit
  adapters for an explicitly selected treatment.
- `adapters/checks` owns deterministic command execution.

These are packages inside one Gradle module rather than four modules. The
boundary that carries weight is `deterministic <- adapters`; adapter-versus-
adapter isolation was four build files protecting eight files, which
`PROJECT_PROFILE.toon` right-sizing treats as pre-abstraction.

`modules/benchmarks/` owns JMH benchmarks and benchmark evidence.
`modules/cli/` owns picocli command parsing and user-facing command contracts.

## Fitness Function

Run:

```sh
.agentic-template/bin/project lint
```

The check fails if LangChain4j, picocli, SQLite, Flyway, JGit or JMH implementation
dependencies appear in `modules/domain/` or `modules/deterministic/`, or if
`domain` references an outward package.

It also fails if a scanned layer directory is missing. A rename or move that
outruns this check must break the build loudly; returning an empty file list and
reporting OK would be worse than having no check at all.

## Deferred Boundaries

OpenSearch and generic vector platforms, AST mutation, LSP integration,
distributed workers and automatic production deployment remain deferred. The
former blanket vector deferral has been intentionally revisited only for the
bounded local Neo4j experiment in ADR-0004. Neo4j and SQLite remain outward,
rebuildable adapters; they may inform a proposal but never enter scoring or
promotion authority.

`adapters/git` uses pinned JGit as its zero-setup primary API for read-only
identity, revision and historic-tree operations. Native Git is a visible fallback
and remains the established linked candidate-worktree implementation because
JGit has no comparable worktree-creation API. `adapters/sqlite` keeps disposable
retrieval projections and durable experiment memory behind different ports and
classes even when experiment stores share one physical database.
