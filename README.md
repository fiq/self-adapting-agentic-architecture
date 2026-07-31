# SAAA — self-adapting-agentic-architecture

SAAA is a Java-first experimental platform for evolving agentic workflows.

**Problem it addresses.** When you or an agent change a workflow, prompt policy,
tool-selection strategy or guardrail, tests, CI and reviewers can tell you
whether the change *works*. What they rarely give you is a *fixed comparison
across many attempts at the same idea*: the deciding step is still reasoning
over evidence, running the same request twice tends to produce two different
verdicts, and useful ideas from a rejected attempt rarely feed the next one.
Population-scale selection and recombination between evaluated candidates are
not really part of the vocabulary.

**Because.** SAAA revives the older genetic-programming shape — propose, score
against a fixed fitness function, keep or discard, later recombine survivors —
and puts it on top of a repo-native context store, following
[Comprehension at AI Speed](https://www.infoq.com/articles/ai-speed-context-store-architecture/).
The deterministic scaffolding is not the novelty — tests, MCP tools, skills, CI
already do that. What moves is the step that *decides*, from reasoning into
fixed code. Model output can propose or repair; it can never approve its own
result.
[More on where this idea comes from →](#how-this-came-about)

**Usage.**

```sh
git clone https://github.com/fiq/self-adapting-agentic-architecutre
cd self-adapting-agentic-architecutre
nix develop
.agentic-template/bin/gradle-command :cli:installDist
./modules/cli/build/install/cli/bin/cli evolve fixtures/toy-workflow \
    --behaviour-case workflow-check --max-lines 80
cat fixtures/toy-workflow/journal.md
```

Runs one full evaluation of the shipped fixture — propose a change, isolate it in
a Git worktree, run your check script, score, decide, journal — with no model
credentials required. The fixture's canned proposal makes the check pass, so the
run promotes; break the fixture and the same command discards.
[See a real run →](#what-it-actually-looks-like)

## Contents

- [How this came about](#how-this-came-about) — genetic programming, and the
  InfoQ piece
- [In plain terms](#in-plain-terms) — what the tool actually does
- [What it actually looks like](#what-it-actually-looks-like) — a promote run
  and a discard run
- [Why not just ask an agent to make the change](#why-not-just-ask-an-agent-to-make-the-change)
- [How fitness is defined](#how-fitness-is-defined) — hard gates, weighted
  objectives, where the numbers come from
- [What is real today, and what is not](#what-is-real-today-and-what-is-not)
- [Run locally](#run-locally) and [Evolve a workflow](#evolve-a-workflow) —
  full CLI reference
- [Repository structure](#repository-structure) and
  [Runtime architecture](#runtime-architecture-diagram)
- [Agent startup](#agent-startup) and
  [Development lifecycle](#development-lifecycle)

The first consumer is a developer-researcher or platform maintainer who wants
auditable experiments over autonomous agent workflow changes.

## How this came about

The shape is older than the agent hype. In the 1990s, genetic programming was
already producing many candidate solutions, scoring each against a fitness
function, keeping what scored well, recombining ideas from the survivors, and
repeating. The parts that made it work were the *strong* fitness function, the
*cheap* evaluation, and the willingness to throw candidates away.

Modern agentic systems have serious deterministic scaffolding — tool calls, MCP
servers, skills, static analysis, unit tests, type checkers, linters, CI
pipelines. That scaffolding does a lot of real work. What is usually *not*
deterministic is the step that decides: the reasoning that reads all of that
evidence and picks a next action or accepts a change. The deterministic tools
produce inputs; a model produces the verdict.

Two things fall out of that arrangement. The deterministic constraints tend to
concentrate on what is easy to encode — does it compile, does the test pass, is
the type well-formed — because harder domain constraints are expensive to write
down, so they get pushed back into the reasoning step, or into more model-graded
checks (an LLM writing tests, an LLM judging outputs, another agent reviewing
the first). And running the same request twice tends to give two different
answers, with no way to compare them on the same axis, because there is no
fitness function ranking them — only a chat log explaining why each one seemed
fine at the time. Useful ideas from a rejected attempt rarely make it into the
next one. Population-scale selection, and recombination between evaluated
candidates, are not really part of the vocabulary.

None of this is a criticism of the tools; SAAA uses the same primitives. It is
a claim about where determinism should carry weight. SAAA moves the *decision*
into fixed code and asks you to grow the fitness function until it can bear it.

The second nudge was Comprehension at AI Speed
([InfoQ, 2026-07-14](https://www.infoq.com/articles/ai-speed-context-store-architecture/)),
which argues that as AI-assisted delivery gets faster, the bottleneck is durable
*context*: specs, tests, fitness functions and handoff metadata living in the
repository itself, not in a chat window. That reframes the loop above as an
engineering discipline rather than a modelling trick, and it is why SAAA keeps
its state — proposals, evidence, decisions, journal — as versioned files next to
the code being evolved.

Put those two together and SAAA is an experiment in reintroducing the older
loop shape on top of a repo-native context store:

- **Fixed fitness, not vibes.** Scoring is deterministic and lives in code, so
  the same evidence always gives the same answer.
- **Bounded mutation, in Git.** Every candidate is a real commit in a real
  worktree, so any decision is reproducible from the commit id.
- **Author and grader are different things.** A model may propose or repair, but
  it never approves its own result.
- **Recombination is on the roadmap, not the first slice.** Crossover between
  evaluated candidates is deferred until single-candidate evaluation is honest,
  because recombining unreliable evidence produces unreliable children.

Hedged claim: for one change against a repository that already has a good test
suite, an agent plus your CI does most of this. The payoff shows up when you
want to try many variants of an idea and rank them on identical evidence,
without a person adjudicating each round.

## In plain terms

You have something an agent depends on to do its job: a workflow definition, a
prompt policy, a tool-selection strategy, a set of guardrails. You want to change
it — and you want to know whether the change was actually an improvement, not
just whether it looked plausible.

That is the whole job here. You describe the behaviours that must still hold, as
ordinary scripts that exit 0 or 1. Something proposes a change. The change is
applied in an isolated copy, measured against your scripts, scored by fixed
rules, and then kept or thrown away. You get a written record either way.

```sh
cli evolve ./my-workflow --behaviour-case publish-guard --behaviour-case renders-draft
```

That says: *evolve what is in this folder, and a candidate is only acceptable if
`publish-guard.sh` and `renders-draft.sh` both still pass.*

One run does this:

```text
1. propose    something suggests one bounded change, with a stated hypothesis
2. isolate    the change is written into a separate Git worktree, never your working copy
3. commit     the candidate is committed, so the exact thing measured is recoverable
4. check      your <case>.sh scripts run inside that candidate
5. score      fixed rules turn the evidence into a number and a decision
6. journal    a human-readable entry is appended to journal.md
```

### What it actually looks like

A candidate that works. The proposal was "enforce the draft check before
publish", and the behaviour script demanding that still passes:

```text
  propose    MUT-toy-fixture  enforce the draft check before publish
  candidate  candidate-mut-toy-fixture  2e02862
  check      workflow-check           PASSED
  score      1.00
  PROMOTE
```

A candidate that does not. Same proposal, but this workflow also had a
`publish: allow` line that the rewrite dropped, and the behaviour script noticed:

```text
  propose    MUT-toy-fixture  enforce the draft check before publish
  candidate  candidate-mut-toy-fixture  a5360bd
  check      workflow-check           FAILED
  score      0.00
  DISCARD
```

Nothing was broken in your files: the candidate lived in its own worktree, and
the run exited 0 because discarding a bad candidate is a successful experiment,
not an error. Both runs leave an entry like this in `journal.md`:

```markdown
## 2026-07-31T09:21:47Z  candidate-mut-toy-fixture

**Hypothesis** enforce the draft check before publish

| | |
|---|---|
| commit | 2e0286278d9429a8cbef0137b34dfe32d42d13ef |
| checks | workflow-check PASSED |
| score | 1.00 |
| decision | PROMOTE |
```

The commit id is the point. Every claim in that table is attached to a commit you
can check out and re-measure.

### Why not just ask an agent to make the change

Because the agent that writes the change is the last thing that should be
grading it.

| Asking an agent directly | This loop |
|---|---|
| The agent decides when it is done | A model may propose and repair, but never approves its own result |
| "Looks correct to me" | Pass or fail from scripts you wrote, before the change existed |
| Edits land in your working tree | Edits land in a throwaway Git worktree; your files are untouched until you promote |
| Ask twice, get two different answers and no way to compare them | The same evidence always produces the same score and decision |
| The reasoning is in a chat log | The decision, the evidence and the commit are in `journal.md` and Git |
| One attempt, judged by vibes | Many attempts, ranked on identical measurements |

Being honest about the trade: for a single change against a repository that
already has good tests and CI, an agent plus your existing pipeline gets you most
of this. The payoff starts when you want to try five variants of the same idea,
compare them on identical evidence, and keep doing that without a human
adjudicating each round. That is what fixed, deterministic scoring buys — an
opinion that does not drift between runs.

### How fitness is defined

Fitness is two layers, and the first one is not negotiable.

**Hard gates.** Fail any one of these and the candidate scores 0.00 and is
discarded, no matter how good the rest looks. There is no trading a gate away
against a high score elsewhere.

| Gate | Fails when |
|---|---|
| `hard_gate_deterministic_checks` | any deterministic check failed, or no check evidence was produced at all |
| `hard_gate_required_behavior_cases` | any behaviour case you declared failed, or produced no evidence of its own |
| `hard_gate_required_objective_scores` | any objective below is missing or is not a number between 0 and 1 |
| `hard_gate_non_empty_realization` | the candidate changed no file, so there is nothing to evaluate |

Absent evidence counts as failure everywhere. A behaviour you asked for and did
not get proof of has not been shown to hold.

**Weighted objectives.** Only once every gate passes are these summed. The total
must reach **0.80** to promote.

| Objective | Weight | Measured as |
|---|---|---|
| `task_success` | 0.40 | fraction of your declared behaviour cases that passed |
| `reliability` | 0.20 | 1.0 when no check timed out, else 0.0 |
| `cost_latency_budget` | 0.20 | worst `budget / measured` across benchmarks, capped at 1.0 |
| `behavioral_safety` | 0.10 | fixed at 1.0 today; gains a real source when reviewer evidence lands |
| `parsimony` | 0.10 | `1 - (lines changed / --max-lines)`, so a tighter change scores higher |

So the promoting run above scored `0.40 + 0.20 + 0.20 + 0.10 + 0.10 = 1.00`. The
discarding run failed the behaviour gate, so it scored 0.00 without any objective
being consulted.

Where the numbers come from:

- **Weights and the 0.80 threshold** are code, in `MutationOperatorPolicy` and
  `PhenotypeFitnessScorer`. They are the same for every mutation operator, so
  candidates stay comparable. Changing them is a reviewed change to the
  repository, not a per-run flag.
- **What "correct" means** is entirely yours: the `--behaviour-case` scripts you
  write. The system has no opinion about your domain.
- **Per-run budgets** are flags, such as `--max-lines` for the change budget
  parsimony is scored against.
- **Nothing the model emits** can influence any of this. A proposal that tries to
  supply its own score or approval is rejected before scoring, and recorded gate
  outcomes are written after measured values so evidence cannot overwrite them.

### What is real today, and what is not

Working end to end: proposal, isolation, realization, commit, checks, scoring,
promote-or-discard, journal. Every part of the loop runs.

Not yet: the proposer is a canned fixture read from a file, not a live model, so
this proves the pipe rather than that a model produces good ideas. One candidate
is evaluated per run, so there is no ranking or selection pressure between
candidates yet — that is the next slice, and it is the point at which this starts
to differ from a capable agent with a good test suite. `behavioral_safety` has no
independent evidence source and sits at 1.0.

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
Deterministic use case: MutationEvaluationLoop
  |
  +--> domain records and deterministic policies
  |
  +--> ports
        |-- MutationProposer          -> adapters/langchain4j
        |-- CandidateWorkspace        -> adapters/git
        |-- ExperimentMetadataStore   -> adapters/sqlite
        |-- CheckRunner               -> adapters/checks
        |-- BenchmarkRunner           -> benchmarks/JMH
```

LangChain4j is intentionally isolated behind adapter ports. The domain layer is
plain Java and must not import model-provider libraries.

## Repository structure

Layers are named for what they are allowed to know. Dependencies point inward:
`cli` and `adapters` may reach `deterministic`, `deterministic` may reach
`domain`, and `domain` may reach nothing. Gradle enforces this, so a violation
is a compile error rather than a review comment.

| Path | Purpose |
|---|---|
| `modules/domain/` | Plain Java records and value types; no dependencies at all |
| `modules/deterministic/` | Validation, scoring, promotion and ports; nothing provider-aware or nondeterministic lives here |
| `modules/adapters/` | Model access, Git worktrees, SQLite persistence and command execution, one package each |
| `modules/benchmarks/` | JMH benchmarks and benchmark evidence adapters |
| `modules/cli/` | picocli command entrypoint |
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

### Evolve a workflow

The `evolve` command runs one complete mutation evaluation end to end — propose,
realize into a Git candidate, check, score, decide, journal — with no model
credentials:

```sh
.agentic-template/bin/gradle-command :cli:installDist
./modules/cli/build/install/cli/bin/cli evolve fixtures/toy-workflow \
    --behaviour-case workflow-check --max-lines 80
cat fixtures/toy-workflow/journal.md
```

| Option | Default | Purpose |
|---|---|---|
| `--profile` | `fixture` | Proposer profile name; `fixture` needs no credentials |
| `--workflow-file` | `workflow.txt` | File inside the target folder being evolved |
| `--behaviour-case` | required | Check name that hard-gates promotion; repeatable |
| `--max-lines` | `80` | Change budget parsimony is scored against |

Each `--behaviour-case <name>` runs `<name>.sh` in the target folder, with a
one-minute timeout per case, and every declared case must pass before promotion,
so the gate cannot pass on the strength of the first case alone. The name is used
as a file-name segment and must match `[a-zA-Z0-9][a-zA-Z0-9._-]*`; duplicates
are rejected. The command refuses to run unless every declared case has an
executable script, so a typo is never recorded as evidence about the mutation.

Checks run inside a worktree created from `HEAD`, so a new check script must be
committed before it can gate a run. A check script must be a regular file, not a
symlink: a program named by path has to resolve inside the candidate worktree, so
a script pointing outside it cannot satisfy a required behaviour. The convention
is POSIX-shaped (`<name>.sh`, executable bit), so `evolve` targets Linux and
macOS.

The target folder must sit inside a Git repository, because candidate isolation
uses `git worktree`. A discarded candidate is a successful run; the command
exits non-zero only when the run itself fails.

A candidate whose realization changed no file is discarded however well it
scores. Parsimony rewards a smaller change, so without that gate the empty change
would score best of all, on evidence about the baseline it never touched.

Candidate worktree names derive from the mutation id, so re-running the fixture
profile against a folder that already has a candidate worktree fails until
`.worktrees/candidate-*` is removed.

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
