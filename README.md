# SAAA — self-adapting-agentic-architecture

SAAA takes one proposed change to one file, realises it in an isolated Git
worktree, runs check scripts you wrote against that worktree, turns the results
into a score with fixed code, and records promote or discard. Nothing merges.

```text
proposal -> isolated Git candidate -> measured evidence -> fixed decision -> promote or discard
```

The intended target is what an agent depends on rather than the agent itself: a
workflow definition, a prompt policy, a tool-selection strategy, a set of
guardrails. Anything that is a file and can be graded by a script exiting 0 or 1.

Fixed in code: validation, the hard gates, the weighted objectives, the 0.80
promotion threshold and the promote-or-discard rule. A model may propose or
repair a candidate, but it must never approve its own result.

This is an experiment, and an early one. The loop runs end to end for one
candidate per run. The default proposer is a canned file, so a stock run proves
the pipe rather than that a model has good ideas, and no benchmark evidence
reaches the score. [Why this shape](#why-this-shape) covers the
genetic-programming lineage it is borrowing from and what the borrowing is for.

```sh
git clone https://github.com/fiq/self-adapting-agentic-architecture
cd self-adapting-agentic-architecture
nix develop
.agentic-template/bin/gradle-command :cli:installDist
./modules/cli/build/install/saaa/bin/saaa saaa-evolve fixtures/toy-workflow \
    --behaviour-case workflow-check --max-lines 80
```

No model credentials required. Real output:

```text
  retrieval NONE  config=retrieval-config-v1 evidence=0 tokens~0
  propose    MUT-toy-workflow-fixture  enforce the draft check before publishing
  candidate  candidate-mut-toy-workflow-fixture  56bf0b9d69bae647ad7c8b68d4346e905a2d05ce
  check      workflow-check           PASSED
  score      1.00
  PROMOTE
  journal    /home/raf/…/fixtures/toy-workflow/journal.md
```

The fixture baseline deliberately fails `workflow-check.sh` and the canned
proposal fixes it, so the run promotes.

## What is real today

### Harness-agnostic execution boundary

SAAA is being shaped as an agentic harness governor, not as another
general-purpose coding agent. `AgentHarness` is the provider-neutral boundary
for calling an execution engine such as Goose, OpenCode, Codex, Claude, a local
process or a direct model API. The engine is replaceable; SAAA retains the
route, resource budget, isolated worktree, deterministic checks, fitness,
promotion and audit evidence.

The first compatibility adapter wraps the existing proposer path. ACP and
concrete external-agent adapters are deliberately follow-up work so the neutral
contract can be tested before transport-specific behavior is introduced. See
[`CHG-006`](specs/changes/CHG-006-agent-harness-boundary/change.toon) and the
[architecture wiki](docs/wiki/architecture.md).

**Working** means implemented and runnable now. **Partial** means a real
implementation exists but the useful capability is incomplete. **Not
implemented** means no code.

| Capability | Status | Detail |
|---|---|---|
| Propose, isolate, commit, check, score, decide, journal | Working | one candidate per `saaa-evolve` run |
| Canned proposer | Working | `--profile fixture` reads `<target>/.saaa/fixture-mutation.txt` |
| Live model proposer | Partial | `--profile openai-compatible` via LangChain4j, covered by a WireMock-backed acceptance test, not exercised by the shipped fixture |
| MCP exposure | Partial | `saaa-mcp` serves the `saaa_evolve` tool over stdio; startup and tool contract are tested, client-disconnect lifecycle is not |
| Benchmark-backed objectives | Not implemented | `EvolveRunner` wires the benchmark runner to a constant empty list, and `:cli` has no dependency on `:benchmarks` |
| Behavioural-safety evidence | Not implemented | the objective is the literal `1.0` |
| Retrieval treatments | Partial | `NONE` needs nothing; `VECTOR`, `GRAPH` and `HYBRID` need Neo4j and an embedding endpoint |
| Population, ranking, selection | Not implemented | |
| Recombination | Not implemented | `ConceptualCrossoverPolicy` exists with unit tests and is wired into no command |
| Evolving product code | Partial | `--workflow-file` accepts any regular file and an acceptance test targets a Java file; realisation is whole-file replacement, not an AST edit |

## Run locally

`nix develop` supplies Java, Gradle, Git and the Docker client. The installed
executable is always `saaa`; every public command token carries the `saaa-`
prefix so `index` and `retrieve` never appear as bare names in a tool registry.

| Option | Default | Purpose |
|---|---|---|
| `--profile` | `fixture` | `fixture` or `openai-compatible` |
| `--workflow-file` | `workflow.txt` | the file inside the target folder being replaced |
| `--behaviour-case` | required | check name that hard-gates promotion; repeatable |
| `--max-lines` | `80` | change budget; both a pre-realisation validator and the parsimony denominator |
| `--retrieval` | `NONE` | `NONE`, `VECTOR`, `GRAPH` or `HYBRID` |
| `--task` | a bounded default goal | intent passed to retrieval and to the model prompt |

Each `--behaviour-case <name>` runs `<name>.sh` in the target folder with a
one-minute timeout, and every declared case must pass, so the gate cannot be
satisfied by the first case alone. The run refuses to start unless every
declared case has an executable regular file, so a typo is never recorded as
evidence about the mutation. Checks run in a worktree created from `HEAD`, so a
new or edited check script must be committed before it can gate a run.
The mutation target must also be different from every declared check script, so
a candidate cannot rewrite the file that grades it.

The target folder must sit inside a Git repository, because isolation uses
`git worktree`. A discarded candidate is a successful run and exits 0; a
non-zero exit means the run itself failed. For a live proposal, set the three
model variables in [configuration](#configuration-and-environment-variables) and
pass `--profile openai-compatible`.

Naming rules, the symlink restriction and the two ways a repeat run collides are
in [docs/wiki/operations.md](docs/wiki/operations.md).

### A discard

The fixture proposer reads `.saaa/fixture-mutation.txt` from your working tree
rather than from `HEAD`, so editing it changes the proposal without a commit.
Give it a body that changes a line but leaves `draft-check: skip` in place, and
the same command gives:

```text
  check      workflow-check           FAILED
  score      0.00
  DISCARD
```

Nothing in your files broke. The candidate lived in its own worktree.

## What one run leaves behind

- **A candidate commit** on `candidate/<workflow>-<mutation>`, holding the
  realised file plus `.saaa/candidates/<candidate>.toon` bookkeeping: baseline,
  mutation, and for a live proposer the prompt digest, prompt, raw response and
  token counts.
- **`journal.md`**, appended in the target folder:

  ```markdown
  ## 2026-08-04T07:36:54.889592334Z  candidate-mut-toy-workflow-fixture

  **Hypothesis** enforce the draft check before publishing

  | | |
  |---|---|
  | commit | 56bf0b9d69bae647ad7c8b68d4346e905a2d05ce |
  | checks | workflow-check PASSED |
  | score | 1.00 |
  | decision | PROMOTE |

  Scored 1.00 against a threshold of 0.80.
  ```

- **`experiments/ledger/<candidate>-<digest>.toon`**, a Git-visible envelope
  carrying subject and process repository revisions, mutation and retrieval
  strategy ids, changed paths, checks, benchmarks, fitness and decision. It
  excludes prompts, raw responses, embeddings and reasoning, and can rehydrate
  the memory tables used to rebuild Neo4j.
- **Rows in `.saaa/experiments.sqlite`**, one per objective and gate, plus
  **`docs/wiki/experiments.md`** regenerated from the envelopes as a readable
  projection that states it is not authority or ranking weight.

Every claim there is attached to a commit you can check out and re-measure.

Promotion writes none of your files. It records a decision and leaves the branch
in place; `main` is untouched, and there is no flag, no port method and no merge
string in the deterministic layer that turns a score into a merge. Three tests
hold that line, listed in [docs/wiki/testing.md](docs/wiki/testing.md).

## Fitness and the decision

```text
candidate --> hard gates pass? --no--> discard
                    |
                   yes
                    v
              weighted sum >= 0.80? --no--> discard
                    |
                   yes --> promote

eligible(c) = every hard gate passes
fitness(c)  = Σ weight_i × objective_i(c)
promote(c)  = eligible(c) and fitness(c) >= 0.80
```

### Hard gates

Fail one and the candidate scores 0.00 and is discarded, whatever else is true.
A gate cannot be traded against a high score elsewhere.

| Gate | Fails when |
|---|---|
| `subject.invariant.deterministic_checks` | any check failed, or no check evidence was produced at all |
| `subject.invariant.required_behavior_cases` | any declared behaviour case failed or produced no evidence of its own |
| `subject.invariant.required_objective_scores` | any objective below is missing, or is not a finite number in `[0, 1]` |
| `subject.invariant.non_empty_realization` | the candidate commit changed no file outside its own `.saaa/` bookkeeping |

Absent evidence counts as failure everywhere: a behaviour you asked for and got
no proof of has not been shown to hold. The empty-realisation gate exists
because parsimony rewards the smallest diff, so without it a candidate that
changed nothing would score best of all, on evidence about a baseline it never
touched. Gate outcomes are written into the result map after the measured
scores, so evidence content cannot overwrite a recorded gate outcome; a jqwik
property asserts that, not just an example.

### Weighted objectives

Weights live in `MutationOperatorPolicy.DEFAULT_OBJECTIVES`, identical for every
mutation operator so candidates stay comparable. Values are derived in
`PhenotypeBridgeScorer`.

| Objective | Weight | Derived from | Varies between eligible candidates? |
|---|---:|---|---|
| `subject.objective.task_success` | 0.40 | fraction of declared behaviour cases that passed | No |
| `subject.objective.reliability` | 0.20 | `1.0` unless structured check evidence has status `TIMED_OUT` | No, in practice |
| `subject.objective.cost_latency_budget` | 0.20 | worst `budget / measured` over benchmarks, clamped to `[0, 1]`, starting at `1.0` | No |
| `subject.objective.behavioral_safety` | 0.10 | the literal `1.0` | No |
| `subject.objective.parsimony` | 0.10 | `1 - (linesChanged / --max-lines)`, clamped | Yes |

The threshold compares against the raw sum; the reported score is rounded to two
decimals for display only, so `0.7950` cannot promote.

From the run above: the fixture replaces one line of `workflow.txt`, counted as
one removed plus one added, so parsimony is `1 - 2/80 = 0.975` and the raw sum
is `0.40 + 0.20 + 0.20 + 0.10 + 0.0975 = 0.9975`, reported as `1.00`.

### Why four of those five decide nothing

`subject.objective.task_success` duplicates its own gate: every declared case must pass to clear
`subject.invariant.required_behavior_cases`, so the passed fraction is 1.0 by
construction. Partial credit would need the gate relaxed first.

`subject.objective.reliability` only drops when a check times out and records structured
`TIMED_OUT` evidence; an ordinary failed check leaves the objective at 1.0, although either result
fails the deterministic checks gate. The diagnostic summary still contains timeout text, but
candidate-controlled stdout cannot spoof reliability.

`subject.objective.cost_latency_budget` cannot be measured. `EvolveRunner` wires the benchmark
runner to `candidate -> List.of()`, `ScoringConfig` gets an empty budget map,
and `:cli` has no Gradle dependency on `:benchmarks`, so the loop never executes
and the value stays at its `1.0` starting point. `JmhBenchmarkRunner` is real
and integration-tested; nothing in the loop calls it. A benchmark with a zero
value or no budget is skipped rather than failed, so absent benchmark evidence
is invisible rather than a gate failure.

`subject.objective.behavioral_safety` is the literal constant `1.0`. SAAA evaluates no behavioural
safety property. It contributes 0.10 of unearned weight.

`subject.objective.parsimony` is the one that varies. `linesChanged` comes from the candidate
commit against its first parent through JGit, summing `lengthA + lengthB` per
edit, so a one-line replacement counts as 2. Paths under `.saaa/` are excluded
so bookkeeping does not inflate the diff; everything else counts, including
generated files and reformatting. At or above `--max-lines` it clamps to 0.0,
and `--max-lines` is also a pre-realisation validator, so a patch exceeding the
budget is rejected before a candidate exists and the run exits non-zero.

So through `PhenotypeBridgeScorer`, the only scorer the CLI wires, the weighted
sum cannot fall below 0.90 and the 0.80 threshold cannot reject an eligible
candidate. The gates are the decision; the score is a record of it. `PhenotypeFitnessScorer` underneath
compares properly and the golden corpus exercises both sides of the threshold,
but nothing on the CLI path produces those inputs.

### The limitation of deterministic fitness

A fixed decision is reproducible. It is not automatically a good decision.

The machinery earns its cost only when the fitness function reflects the
behaviour that matters, separates better candidates from merely passing ones,
uses evidence independent of whatever proposed the change, resists gaming, is
cheap to run repeatedly, treats absent evidence as visible failure, and stays
versioned. SAAA currently manages the last two.

A candidate can optimise the checks rather than the intended behaviour when the
checks are weak or observable. What narrows that here: check scripts come from
`HEAD`, so a candidate cannot introduce the script that grades it within the
same run; a check program named by path must resolve inside the worktree and
must not be a symlink; the check environment is scrubbed to `PATH`, `HOME`,
`LANG`, `LC_*` and `JAVA_HOME` with provider credentials denied, and an
acceptance test asserts a candidate reading the API key sees the empty string;
the MCP input schema is closed against fields that would force promotion,
override a gate, request a merge or carry credentials. What does not: the check
process is not sandboxed, so it keeps whatever filesystem and network access
launched it. This narrows the obvious routes rather than solving anything.

Ranking several candidates on identical evidence, selecting between them and
recombining ideas from evaluated parents are not implemented, and the current
score should not be read as if they were.

## Why this shape

In the 1990s genetic programming was already running this loop: produce many
candidates, score each against a fitness function, keep what scores well,
recombine ideas from the survivors, repeat. It needed a strong fitness function,
cheap evaluation and willingness to throw candidates away. None of the three is
free. The first is the one this repository is about.

Agentic systems have plenty of deterministic scaffolding: tool calls, MCP
servers, skills, static analysis, tests, CI. The step that decides is not part
of it. Scaffolding produces inputs, a model produces the verdict, and encoded
constraints then drift towards whatever is cheap to express while everything
harder goes back into reasoning or into model-graded checks. Ask twice and you
get two answers with no shared axis to compare them on, so a good idea inside a
rejected attempt rarely survives into the next one.

That last part is the argument for a population, and also why recombination sits
behind single-candidate evaluation rather than beside it. Recombining unreliable
evidence produces unreliable children.

So SAAA moves the deciding step into fixed code, and the work becomes growing a
fitness function that can carry it. The
[audit above](#why-four-of-those-five-decide-nothing) is how far that has
actually got: one measured objective out of five.

The second input is
[Comprehension at AI Speed](https://www.infoq.com/articles/ai-speed-context-store-architecture/)
(InfoQ, 2026-07-14): the bottleneck is durable context living in the repository
rather than a chat window. Hence evidence, decisions and journal as versioned
files beside the code being evolved.

## Compared with an agent plus CI

For one change against a repository with strong tests and CI, a capable coding
agent already provides much of this workflow.

The intended differentiator is what a single adjudicated change cannot give you:

```text
several candidate variants
        + identical external evidence
        + fixed comparison
        + retained experiment history
```

Three of the four exist. The candidate variants do not: SAAA evaluates one
candidate per run, so the differentiator is a design claim rather than a working
property.

## Architecture

All Java lives under `modules/`, in layers named for what they may know.
Dependencies point inward: `cli` and `adapters` may reach `deterministic`,
`deterministic` may reach `domain`, and `domain` may reach nothing. Gradle
enforces it, so a violation is a compile error rather than a review comment.

```text
cli -> deterministic -> domain
             ^
             |
  adapters and benchmarks implement ports
```

`MutationEvaluationLoop` sits in `deterministic` and talks only to ports:
`MutationProposer`, `EvidenceRetriever`, `CandidateWorkspace`, `CheckRunner`,
`BenchmarkRunner`, `FitnessScorer`, `ExperimentMetadataStore` and
`CandidateDecisionSink`. The port wiring, module table and extension points are
in [docs/wiki/architecture.md](docs/wiki/architecture.md); the boundary rules
are in
[docs/architecture/module-boundaries.md](docs/architecture/module-boundaries.md).

`CandidateDecisionSink` exposes no merge operation, so promotion cannot become
an automatic merge through adapter configuration.

## Run with containers

There is no application image. ADR-0004 revisits the earlier container and
vector deferrals only for one optional, on-demand Neo4j Community dependency
used by retrieval experiments:

```sh
export SAAA_NEO4J_PASSWORD='choose-a-local-password'
nix develop --command docker compose up -d --wait neo4j

nix run . -- saaa-index build --role SUBJECT_AND_PROCESS
nix run . -- saaa-retrieve \
    --repository . --task 'preserve deterministic fitness' \
    --mode GRAPH --exact ARCH-001

nix develop --command docker compose down
```

Retrieval requires the projection revision, query revision and working-tree
fingerprint to match exactly, so run `saaa-index update` after any repository
change; it fails closed rather than serving stale evidence under the requested
treatment.

The image is pinned by digest, HTTP and HTTPS are off, authenticated Bolt binds
`127.0.0.1` only, `no-new-privileges` is set and no plugins are installed. On
2026-08-02 Trivy 0.72 found no high or critical operating-system findings in
that image and ten unique high Java dependency findings, whose published fixed
versions are newer than anything Neo4j currently ships. RISK-005 tracks them.
That is containment, not absence, so do not expose this topology to an untrusted
network.

Topology, the volume and password trap, repository roles, the
`lineage-novelty-v1` retention policy, historic reinflation, the JGit fallback
and `saaa-ablate retrieval` are all in
[docs/wiki/operations.md](docs/wiki/operations.md).

## Tests

```sh
.agentic-template/bin/project test
.agentic-template/bin/project lint
.agentic-template/bin/project component-test
.agentic-template/bin/project integration-test
.agentic-template/bin/project graphrag-integration-test
```

`lint` is the architecture-boundary fitness function guarding this repository:
model-provider imports may not appear in `modules/domain` or
`modules/deterministic`, and it fails when a scanned layer directory is missing,
so a rename cannot make it pass vacuously. The Neo4j tests are opt-in behind
`SAAA_NEO4J_INTEGRATION`, so a plain `integration-test` skips them. What each
suite covers is in [docs/wiki/testing.md](docs/wiki/testing.md).

## Configuration and environment variables

No model-provider configuration is committed. Configure only the boundary you
are using; local Git and SQLite defaults need nothing.

| Variable | Purpose |
|---|---|
| `SAAA_MODEL_BASE_URL` | OpenAI-compatible chat endpoint for `--profile openai-compatible` |
| `SAAA_MODEL_API_KEY` | model credential |
| `SAAA_MODEL_NAME` | model id |
| `SAAA_NEO4J_PASSWORD` | credential shared by Compose and the graph adapter |
| `SAAA_EMBEDDING_*` | `BASE_URL`, `API_KEY`, `MODEL_ID` and `DIMENSIONS` for `VECTOR` and `HYBRID` indexing |
| `SAAA_PROCESS_REPOSITORY` | SAAA process checkout when evolving a different subject repository; defaults to the subject |
| `SAAA_MEMORY_*` | `POLICY_ID` plus the slot counts bounding the hot graph |

The experiment database path is not configurable: it is
`.saaa/experiments.sqlite` at the Git root of the target repository.

## Known limitations

Beyond the fitness gaps above:

- `journal.md` is written into your target folder, so the command writes outside
  its own repository. This repository's `.gitignore` covers `**/journal.md`.
- Reporters print only check name and status, so a behaviour case that fails for
  its own reasons shows no diagnostics.
- The MCP server accepts any local Git repository visible to the stdio process
  and runs check scripts from it. Fine for a local developer tool, unsafe as-is
  for anything remote or multi-user.
- The non-empty realisation gate rejects a no-op candidate but says nothing about
  whether the change is meaningful; a whitespace edit passes it.
- The `MutationContract` and `MutationContractValidator` stack runs parallel to
  the wired `Mutation` and `MutationValidator` ports. `Mutation.patch` is
  recorded as transitional with no migration step scheduled.
- Token counts are captured but no price schedule is configured, so ablation cost
  falls back to token count.

## Infrastructure and deployment state

Local topology is Nix plus Gradle, Git, SQLite and optional on-demand Neo4j
Community. The deployment target is `local_cli`. Infrastructure as code is not
applicable until a remote execution or deployment target is chosen.

## Deliberate non-goals

- OpenSearch, generic vector platforms or production retrieval infrastructure
- AST mutation
- LSP integration
- distributed workers
- automatic production deployment

Each is recorded as deferred in `PROJECT_PROFILE.toon` with revisit conditions.

## Roadmap

Three layers, detailed in
[ADR-0002](docs/decisions/0002-three-layer-vision.md): evolve a
workflow or prompt file (shipped for one candidate per run); SAAA as a tool an
outer agentic loop calls (`saaa-mcp` serves it, the planning loop is not built);
the same loop on product code gated by existing tests and benchmarks
(`--workflow-file` can target a Java file, but realisation is whole-file
replacement and no benchmark gating exists).

Population and conceptual crossover stay foundation slices that upgrade all
three at once. Both are blocked on the same thing, and it is the thing described
in [Why this shape](#why-this-shape): objectives that actually vary between
passing candidates. Ranking candidates on a score that cannot separate them
would be theatre.

## Agent startup

Fresh agent sessions must run `.agentic-template/bin/project startup`, confirm
that `AGENTS.md` was read from disk, then continue from the operating contract.
For non-trivial work, read `HANDOFF.toon`, `PROJECT_PROFILE.toon`,
`docs/context-store.md` and `.agents/knowledge/index.md` before planning or
implementation.

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

The repository is the durable context store: structure in `AGENTS.md`, this
README and `PROJECT_PROFILE.toon`; lineage in `HANDOFF.toon`, ADRs and
`.agents/knowledge/`; behavior in specs and tests; conformance in repository
checks, CI and architecture fitness functions. The model is described in
[docs/context-store.md](docs/context-store.md).

Do not add an external vector store or SaaS memory layer by default. Add one
only when project evidence justifies it and `PROJECT_PROFILE.toon` records the
decision.

## Development lifecycle

Work flows from a narrative or `/ideate` into a structured change spec, then a
boundary-first acceptance test, implementation, review, validation, handoff and
knowledge upkeep. Meaningful behavior changes update the spec and validation
evidence in the same change. Default integration is branch plus PR; the
tool-unavailable fallback is in `AGENTS.md`.

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
