# Operations

SAAA is a local CLI. The commands below build and run it on a developer
machine.

Environment setup:

- `nix develop` supplies Java, Gradle, the Docker client and Compose; it uses
  the host Docker daemon.
- `nix run . -- <saaa-command>` builds incrementally and invokes the same
  installed Java binary.
- Devenv is not an additional requirement.

## GraphRAG topology (optional)

The optional GraphRAG topology is one pinned Neo4j Community container with a
health check and a named `neo4j-data` volume.

- Ordinary `docker compose down` retains the volume.
- The volume is outside Git and disposable:
    - repository facts come from Git;
    - outcome memory comes from `experiments/ledger/*.toon` through the
      efficient `.saaa/experiments.sqlite` ledger;
    - `.saaa/retrieval.sqlite` is a disposable embedding/capsule/provenance
      projection.

Changing the Neo4j password:

- The volume survives `compose down`, and Neo4j applies `NEO4J_AUTH` only when
  initialising an empty one.
- Changing `SAAA_NEO4J_PASSWORD` against an existing volume therefore fails
  authentication with "incorrect authentication details too many times in a
  row" until the volume is removed.
- Either keep the original password, or drop the volume before changing it.

Image and exposure:

- The image is the official 5.26.28 Community UBI10 manifest pinned by digest.
- HTTP/HTTPS is disabled and authenticated Bolt is bound only to loopback.
- The latest scan and residual Java dependency findings are recorded in
  `RISK-005`; this topology is not suitable for remote or untrusted exposure.

```sh
export SAAA_NEO4J_PASSWORD='choose-a-local-password'
docker compose up -d --wait neo4j
./modules/cli/build/install/saaa/bin/saaa saaa-index build --repository . --role SUBJECT_AND_PROCESS
./modules/cli/build/install/saaa/bin/saaa saaa-index status --repository .
docker compose down
```

Repository roles:

- Use `--role SUBJECT` for the implementation repository and `--role PROCESS`
  for the SAAA repository when they differ.
- Both live in one repository-partitioned graph.
- An evolution envelope records both revisions.
- `SAAA_PROCESS_REPOSITORY` selects the process checkout; it defaults to the
  subject for self-evolution.

Historic inflation is explicit and replaces the selected repository's graph
projection without changing the checkout:

```sh
./modules/cli/build/install/saaa/bin/saaa saaa-reinflate --repository . --revision <commit>
```

Git access:

- JGit performs read-only repository access without user setup.
- If it cannot open an unusual repository layout, SAAA prints a warning before
  using native Git as a compatibility fallback.

## Retrieval and memory variables

These environment variables configure embedding and graph working-set memory.

| Variable | Purpose |
|---|---|
| `SAAA_EMBEDDING_BASE_URL` | OpenAI-compatible embedding endpoint |
| `SAAA_EMBEDDING_API_KEY` | embedding-provider credential |
| `SAAA_EMBEDDING_MODEL_ID` | stable model and cache identity |
| `SAAA_EMBEDDING_DIMENSIONS` | provider and Neo4j vector dimensions |
| `SAAA_MEMORY_POLICY_ID` | versioned graph working-set policy identity; change it when policy semantics change |
| `SAAA_MEMORY_CHAMPION_SLOTS` | champion representatives retained in the hot graph |
| `SAAA_MEMORY_LINEAGE_SLOTS` | known ancestors of selected champions |
| `SAAA_MEMORY_FAILURE_FINGERPRINT_SLOTS` | distinct failed-behaviour representatives |
| `SAAA_MEMORY_NOVELTY_SLOTS` | distinct evidence and strategy niches |
| `SAAA_MEMORY_EXPLORATION_SLOTS` | deterministic exploration reservoir |
| `SAAA_MEMORY_MAX_ACTIVE_EVALUATIONS` | absolute hot-graph evaluation bound |

Indexing:

- `VECTOR` and `HYBRID` indexing publishes only after the complete embedding set
  succeeds.
- Embeddings are memoised in `.saaa/retrieval.sqlite` by model id plus content
  hash, and that database is disposable.

## Running evolve

Three things bite in practice when running `evolve`.

1. Checks run in a worktree created from `HEAD`, so a new or edited check
   script must be committed before it can gate a run. The pre-flight inspects
   the working tree, so an uncommitted script passes pre-flight and then fails
   to start inside the candidate.

2. The selected `--workflow-file` must not be one of the declared
   `<behaviour-case>.sh` scripts. This fails before candidate creation so a
   mutation cannot rewrite its own grader.

3. A check script must not be a symlink. `git worktree add` recreates a tracked
   symlink faithfully, and the program has to resolve inside the candidate. The
   convention is POSIX-shaped, so `saaa-evolve` targets Linux and macOS.
   Behaviour case names must match `[a-zA-Z0-9][a-zA-Z0-9._-]*` and are compared
   case-insensitively, because on a case-insensitive filesystem `Foo` and `foo`
   both exec `foo.sh` and one script would count as two cases.

Re-running the same fixture against the same folder fails twice over:

- The worktree name derives from the mutation id, so `.worktrees/candidate-*`
  and its branch must be removed first.
- The `experiments/ledger/` envelope refuses to record a second, different
  outcome for the same candidate id.
- That second failure lands after the decision is journalled, so the run prints
  its verdict and then exits non-zero.

## Interactive harness session

`saaa sa` starts the line-oriented interactive client. `status`,
`capabilities`, and `skills` inspect the active session without calling a
proposer. Select an explicit target and profile before `evolve`:

```text
target CODE /path/to/repository
route fixture
evolve Example.java code-check
quit
```

- `evolve <workflow-file> <behaviour-case>...` takes one or more behaviour cases;
  each whitespace-separated name is run as its own declared check rather than
  being folded into a single case name.
- A failing command prints an `error` line and returns to the prompt, so one bad
  run does not end the session.
- `HARNESS_WORKFLOW` and `CODE` both use the existing whole-file realization
  path, isolated worktree, behaviour checks, and deterministic decision.
- Route selection is explicit operator configuration, not automatic model
  routing; MCP remains the machine-facing integration surface.

## Retrieval ablation

Ablation measures how retrieval affects acceptance and fitness across a corpus.

```sh
./modules/cli/build/install/saaa/bin/saaa saaa-ablate retrieval \
    --experiment-id ablation-001 --corpus retrieval-corpus.tsv --attempts 1
```

Corpus format:

- Tab-separated with the header `id`, `target_folder`, `profile`,
  `workflow_file`, `max_lines`, `baseline_fitness`, `behaviour_cases`, `task`.
- Candidate worktrees are namespaced by task, mode and attempt, so treatments
  do not collide.

The report covers:

- acceptance per attempt;
- mean attempts to first accepted;
- best fitness;
- accepted fitness improvement per provider-cost unit (or per token when cost is
  unavailable);
- context tokens per accepted candidate; and
- hard-gate, cache, graph and timing diagnostics.

It states `improvement_claim: not_evaluated_by_reporter` and no ablation has
been measured yet.

Do not add cloud emulators, Kubernetes or service dependencies without evidence.
