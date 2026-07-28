# First Vertical Slice Design

## Flow

```text
saaa evolve ./fixtures/toy-workflow --profile fixture
    |
    v
ProposerProfileRegistry      resolve "fixture" -> FixtureMutationProposer
    |
    v
MutationProposer             read the canned mutation
    |
    v
MutationValidator            bounded? no authority claims?
    |
    v
CandidateWorkspace           git worktree add off HEAD
    +-> MutationRealizer     write the realized workflow file      <- new
    +-> git add -A, commit
    |
    v
CheckRunner                  run the target folder's checks in the worktree
BenchmarkRunner              (no-op source in this slice)
    |
    v
FitnessScorer                PhenotypeBridgeScorer                 <- new
    +-> derive PhenotypeEvidence
    +-> PhenotypeFitnessScorer: hard gates, then weights
    |
    v
CandidateDecisionSink        record the decision
EvolutionReporter            console lines + journal.md            <- new
```

Every stage above already exists except the three marked new.

## Realization

`MutationRealizer` is a port in the deterministic layer:

```java
public interface MutationRealizer {
    void realize(Path worktreePath, WorkflowGraph baseline, Mutation mutation);
}
```

`GitCandidateWorkspace` gains a realizer collaborator. Order inside
`createCommittedCandidate` becomes: `worktree add`, write the candidate
description, call the realizer, `git add -A`, commit. Staging moves from a single
named file to `-A` so realized changes are captured.

`TextMutationRealizer` writes `mutation.patch()` as the whole new content of one
file. Whole-file replacement, not a diff application. AST-aware and hunk-based
realization stay out of scope; the operator model already treats the diff as
realization evidence rather than as the mutation.

`WorkflowGraph` carries `id`, `version` and `definition` but no path, so the
realizer cannot infer which file to write. `TextMutationRealizer` is therefore
constructed with the workflow file's repository-relative path, which the CLI
already knows because it read that file to build the `WorkflowGraph` in the
first place:

```text
evolve <folder>
  reads   <folder>/workflow.txt
  builds  WorkflowGraph(id = folder name, version = baseline commit, definition = contents)
  builds  TextMutationRealizer(relativePathOf(<folder>/workflow.txt))
```

The path is resolved against the candidate worktree at realization time, never
against the coordination checkout, so a run cannot modify the working tree the
user is sitting in.

The realizer decides *what* changes. The Git adapter decides *how* it is
committed. Neither knows about the other's concern.

## Scoring bridge

`PhenotypeBridgeScorer` implements the existing `FitnessScorer` port, so
`MutationEvaluationLoop` needs no change to use it.

```text
EvaluationEvidence (checks + benchmarks)
        |
        +--> behaviour cases:  checks named in the target folder's config
        |                      as required behaviour
        +--> plain checks:     everything else, feeding the checks hard gate
        |
        +--> objective scores: derived below
        |
        v
PhenotypeEvidence  ->  PhenotypeFitnessScorer  ->  FitnessResult
```

Splitting checks into behaviour cases and plain checks is what lets the hard
gate mean "the candidate does the right thing" rather than "the candidate
builds". Without the split the behaviour-case gate would duplicate the
deterministic-checks gate.

### Objective derivation

All five derivations are deterministic and evidence-only.

| Objective | Derivation |
|---|---|
| `task_success` | passed behaviour cases divided by total behaviour cases |
| `reliability` | 1.0 when no check timed out or errored, otherwise 0.0 |
| `cost_latency_budget` | the lowest `budget / measured` across all benchmarks, clamped to the unit interval, so being twice the budget scores 0.5 and being under budget scores 1.0 |
| `behavioral_safety` | fixed at 1.0 |
| `parsimony` | `1 - (linesChanged / maxLinesChanged)`, clamped to the unit interval |

### Where the diff size comes from

`parsimony` needs the realized diff size, which only Git knows. The scorer must
not shell out to Git: that would put infrastructure inside the deterministic
layer. So realization size arrives as evidence, through a port:

```java
public interface RealizationInspector {
    RealizationSummary inspect(Candidate candidate);
}

public record RealizationSummary(int filesChanged, int linesChanged) {}
```

`GitRealizationInspector` in the adapters layer runs `git diff --numstat` against
the candidate commit's parent. `PhenotypeBridgeScorer` takes the inspector as a
collaborator, so the deterministic layer asks a question and never learns how it
is answered.

The remaining derivation inputs are configuration, bundled so the scorer keeps
one constructor argument for policy:

```java
public record ScoringConfig(
        Set<String> behaviorCaseNames,
        int maxLinesChanged,
        Map<String, Double> benchmarkBudgets
) {}
```

`maxLinesChanged` is supplied at wiring time from the operator defaults, because
this slice still runs the transitional `Mutation` stack and no `MutationContract`
flows through the loop. That is a known seam, not an oversight; it closes when
`CHG-002` task `T4b` threads the contract through.

`parsimony` is the one that makes candidates genuinely differ, because it reads
the realized diff. A mutation that changes eighty lines to achieve what another
achieved in six scores lower. That is real selection pressure rather than
decoration.

`behavioral_safety` is honest but inert this slice: an invalid or
authority-bearing contract is rejected before scoring, so no candidate reaching
the scorer can score anything but 1.0. It gains a real source when reviewer
evidence lands in `CHG-002` task `T6`.

`cost_latency_budget` is 1.0 whenever no benchmarks are configured, which is the
fixture case. Benchmarks are wired but unused in this slice.

## Reporting

```java
public interface EvolutionReporter {
    void proposed(Mutation mutation);
    void candidateCreated(Candidate candidate);
    void evidenceCollected(EvaluationEvidence evidence);
    void scored(FitnessResult result);
}
```

`MutationEvaluationLoop` calls the reporter at each stage. No deterministic
class writes to standard output, so the same loop serves a terminal today and a
live TUI or an MCP server later without change. That is the whole reason the
seam exists now rather than later.

Two implementations ship: `ConsoleReporter` writes the terminal lines,
`JournalReporter` appends to `journal.md`. A composite fans out to both.

## The journal

`journal.md` is written into the target folder, next to what is being evolved.
One appended section per run:

```markdown
## 2026-07-28T09:14:02Z  candidate-MUT-001

**Hypothesis** tighten the publish guard to reject empty drafts

| | |
|---|---|
| operator | targeted-behavior-change |
| commit | a1b2c3d |
| checks | draft-renders PASS, publish-guard PASS |
| score | 0.87 |
| decision | PROMOTE |

Scored 0.87 against a threshold of 0.80. Both required behaviour cases
passed, so the hard gates cleared and the weighted objectives decided it.
Parsimony was 0.93: the change touched 6 of an allowed 80 lines.
```

The prose is assembled from a deterministic template. No model is involved in
this slice, and when narration arrives it will read the record after the fact
rather than participate in the decision.

### Journal is not the audit trail

| Artifact | Role | Compactable |
|---|---|---|
| Git candidate commits | provenance | never |
| SQLite experiment metadata | machine record | never |
| `journal.md` | human narrative | yes, safely |

Compaction of the journal is safe precisely because the journal is a view. This
distinction is what lets a later slice sub-edit the journal for readability
without ever weakening auditability.

## Profile seam

```java
public interface MutationProposerFactory {
    String name();
    MutationProposer create(ProfileConfig config);
}
```

`ProposerProfileRegistry` resolves a name to a factory and fails with the list
of known names when it cannot. This slice registers one factory, `fixture`.
Adding OpenAI-compatible, Anthropic and Ollama later means adding registry
entries and a `ChatModel` construction branch. `LangChain4jMutationProposalAdapter`
already accepts a provider-neutral `ChatModel`, so no adapter changes are needed
for any of them.

The registry lives in the CLI module, because choosing a proposer is a
composition concern, not a deterministic policy.

## Target folder contract

```text
fixtures/toy-workflow/
  workflow.txt                 the artifact being evolved
  check.sh                     a real command, real exit code
  .saaa/fixture-mutation.txt   the canned mutation for the fixture profile
  journal.md                   written by the run
```

### Why the fixture mutation is not TOON

The repository standard is TOON for reviewable contracts, and there is no TOON
reader in Java yet — the existing TOON files are read by Python tooling. Writing
one is a slice of its own and is not what this change is about.

So `.saaa/fixture-mutation.txt` uses a deliberately trivial format: the first
line is the summary, everything after it is the proposed new content of
`workflow.txt`.

```text
tighten the publish guard to reject empty drafts
<new contents of workflow.txt from here down>
```

This is a recorded deviation from the structured-data rule, scoped to the
fixture proposer, and it disappears when `CHG-002` task `T3d` adds the TOON
envelope reader. Scoring configuration is passed as CLI flags for the same
reason, rather than inventing a second unparsed file format.

The target folder must sit inside a Git repository, because candidate isolation
uses `git worktree`. For the shipped fixture that repository is this one, so the
worktree is a worktree of `self-adapting-agentic-architecture` and the realized
change lands at `fixtures/toy-workflow/workflow.txt` inside it. Worktrees are
created under `.worktrees/`, which is already ignored.

## Error handling

| Failure | Behaviour |
|---|---|
| target folder is not in a Git repository | fail before any worktree is created, naming the folder |
| unknown profile name | fail listing known names |
| mutation fails validation | fail with the validation messages; no candidate is created |
| a check fails or times out | not an error; it is evidence, and the candidate is scored and discarded |
| candidate worktree already exists | fail rather than reuse or delete, per the worktree rules |

A discarded candidate is a successful run. The command exits non-zero only when
the run itself could not complete.

## Testing

| Layer | Covers |
|---|---|
| unit | objective derivation, parsimony from diff size, reporter fan-out, profile registry resolution and failure |
| integration | realization actually lands in the candidate commit, using a real Git repository |
| acceptance | the evolve command against the shipped fixture, asserting a decision and a journal entry |

The acceptance test runs the fixture profile, so it needs no credentials and no
network.

## Seam for the population slice

The next slice evaluates several candidates per generation and ranks them. This
design keeps that additive rather than a rewrite, in three ways.

`MutationEvaluationLoop.evaluate` returns one `FitnessResult` for one candidate.
Population belongs *above* the loop: the next slice calls it N times and compares
the results. Nothing in the loop needs to change to support that.

`EvolutionReporter` events already carry the candidate and its result, so a
population runner can fan the same events into a ranking view without new
plumbing.

The one thing that does not carry forward is the fixture proposer. It is
deterministic, so calling it N times produces N identical candidates and a
population of clones. The population slice therefore needs a variance source:
either a real model, where sampling supplies the variation, or a fixture that
yields a set of declared variants. That is a dependency of the next slice, not a
gap in this one, but it is the reason the fixture format keeps the mutation in
its own file rather than hard-coding it.

## Revisit conditions

- whole-file realization stops being sufficient, because mutations need to touch
  part of a file without rewriting it
- the objective derivations prove too crude to separate good candidates from bad
- `journal.md` grows past readable length, which is when compaction is needed
- a second front end arrives, which is when the reporter port earns its keep
