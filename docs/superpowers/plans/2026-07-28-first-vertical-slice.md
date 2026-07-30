# First Vertical Slice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `saaa evolve <folder>` run one complete mutation evaluation end to end — propose, realize into a Git candidate, check, score, decide, journal — with no model credentials.

**Architecture:** Six of eight loop ports already have real adapters. This plan adds the three missing pieces (realization, a `FitnessScorer` implementation, progress reporting), the two missing adapters (`CandidateDecisionSink`, a fixture proposer), and the CLI command that wires them together. Nothing in the existing loop orchestration changes except one added constructor parameter.

**Tech Stack:** Java 21, Gradle, picocli, JUnit 5, AssertJ. No new dependencies.

## Global Constraints

- `modules/domain` declares no dependencies; `modules/domain` and `modules/deterministic` must not reference LangChain4j, picocli, SQLite, Flyway or JMH. `.agentic-template/bin/project lint` enforces this and will fail the build.
- Provider dependencies are confined per package: `dev.langchain4j` only under `adapters/langchain4j`, `org.sqlite`/`org.flywaydb` only under `adapters/sqlite`, `picocli.` only in `modules/cli`, `org.openjdk.jmh` only in `modules/benchmarks`.
- No class in `modules/deterministic` writes to standard output.
- Gradle project paths are `:domain`, `:deterministic`, `:adapters`, `:cli`, `:benchmarks`.
- Run commands via `.agentic-template/bin/project <cmd>`; it wraps Gradle through Nix.
- Promotion threshold is `0.80`. Objective ids and weights are fixed: `task_success` 0.40, `reliability` 0.20, `cost_latency_budget` 0.20, `behavioral_safety` 0.10, `parsimony` 0.10.
- Real commit dates only. Never set `GIT_AUTHOR_DATE`, `GIT_COMMITTER_DATE` or `--date`.
- Commit messages end with:
  `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`

## Existing types you will use (do not redefine)

```java
// modules/domain
record WorkflowGraph(String id, String version, String definition)
record Mutation(String id, String summary, MutationScope scope, String patch)
record Candidate(String id, String mutationId, String branchName, Path worktreePath, String commitSha)
record CheckEvidence(String name, CheckStatus status, String summary)   // .passed(n,s) .failed(n,s)
record BenchmarkEvidence(String name, double value, String unit)
record EvaluationEvidence(List<CheckEvidence> checks, List<BenchmarkEvidence> benchmarks, Instant evaluatedAt)
record FitnessResult(Candidate candidate, EvaluationEvidence evidence, Map<String,Double> objectives,
                     double aggregateScore, FitnessDecision decision)
enum FitnessDecision { PROMOTE, DISCARD }
enum CheckStatus { PASSED, FAILED }
enum MutationScope { WORKFLOW_DEFINITION, PROMPT, TOOL_CONFIGURATION }

// modules/deterministic
interface FitnessScorer { FitnessResult score(Candidate candidate, EvaluationEvidence evidence); }
interface CandidateDecisionSink { void promote(Candidate c, FitnessResult r); void discard(Candidate c, FitnessResult r); }
interface CandidateWorkspace { Candidate createCommittedCandidate(WorkflowGraph baseline, Mutation mutation); }
interface MutationProposer { Mutation proposeFor(WorkflowGraph baseline); }
final class PhenotypeFitnessScorer { FitnessResult score(Candidate, PhenotypeEvidence); }
record PhenotypeEvidence(EvaluationEvidence evidence, List<BehaviorCaseEvidence> behaviorCases,
                         Map<String,Double> objectiveScores)
record BehaviorCaseEvidence(String id, CheckStatus status, String summary)  // .passed(id,s) .failed(id,s)
final class MutationOperatorPolicy { static MutationOperatorDefaults defaultsFor(MutationOperatorType); }

// modules/adapters
final class CommandCheckRunner implements CheckRunner
  record CommandCheckRunner.CommandCheck(String name, List<String> command, Duration timeout)  // NESTED
final class GitCandidateWorkspace implements CandidateWorkspace
```

---

### Task 1: MutationRealizer port and TextMutationRealizer

Without this every candidate worktree is byte-identical to `HEAD`, so every candidate scores the same and nothing evolves. This is the defect the spec calls out.

**Files:**
- Create: `modules/deterministic/src/main/java/com/dreamthought/saaa/deterministic/MutationRealizer.java`
- Create: `modules/adapters/src/main/java/com/dreamthought/saaa/adapters/files/TextMutationRealizer.java`
- Test: `modules/adapters/src/test/java/com/dreamthought/saaa/adapters/files/TextMutationRealizerTest.java`

**Interfaces:**
- Consumes: `WorkflowGraph`, `Mutation` from `modules/domain`.
- Produces: `MutationRealizer.realize(Path worktreePath, WorkflowGraph baseline, Mutation mutation)`; `new TextMutationRealizer(String relativeWorkflowPath)`.

- [ ] **Step 1: Write the failing test**

Create `modules/adapters/src/test/java/com/dreamthought/saaa/adapters/files/TextMutationRealizerTest.java`:

```java
package com.dreamthought.saaa.adapters.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dreamthought.saaa.domain.Mutation;
import com.dreamthought.saaa.domain.MutationScope;
import com.dreamthought.saaa.domain.WorkflowGraph;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class TextMutationRealizerTest {
    private final WorkflowGraph baseline = new WorkflowGraph("toy", "v1", "old content");

    @Test
    void writesThePatchAsTheWholeNewFileContent(@TempDir Path worktree) throws IOException {
        Files.createDirectories(worktree.resolve("fixtures/toy"));
        Files.writeString(worktree.resolve("fixtures/toy/workflow.txt"), "old content");

        new TextMutationRealizer("fixtures/toy/workflow.txt")
                .realize(worktree, baseline, mutation("new content"));

        assertThat(worktree.resolve("fixtures/toy/workflow.txt")).hasContent("new content");
    }

    @Test
    void refusesAPathThatEscapesTheWorktree(@TempDir Path worktree) {
        assertThatThrownBy(() -> new TextMutationRealizer("../outside.txt")
                .realize(worktree, baseline, mutation("new content")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must stay inside the candidate worktree");
    }

    @Test
    void failsWhenTheTargetFileDoesNotExistInTheWorktree(@TempDir Path worktree) {
        assertThatThrownBy(() -> new TextMutationRealizer("missing.txt")
                .realize(worktree, baseline, mutation("new content")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing.txt");
    }

    private static Mutation mutation(String patch) {
        return new Mutation("MUT-1", "a summary", MutationScope.WORKFLOW_DEFINITION, patch);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.agentic-template/bin/project test`
Expected: FAIL — compilation error, `TextMutationRealizer` does not exist.

- [ ] **Step 3: Write the port**

Create `modules/deterministic/src/main/java/com/dreamthought/saaa/deterministic/MutationRealizer.java`:

```java
package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.Mutation;
import com.dreamthought.saaa.domain.WorkflowGraph;
import java.nio.file.Path;

/**
 * Applies a mutation to files inside a candidate worktree. The realizer decides what changes; the
 * workspace adapter decides how the change is committed.
 */
@FunctionalInterface
public interface MutationRealizer {
    void realize(Path worktreePath, WorkflowGraph baseline, Mutation mutation);
}
```

- [ ] **Step 4: Write the adapter**

Create `modules/adapters/src/main/java/com/dreamthought/saaa/adapters/files/TextMutationRealizer.java`:

```java
package com.dreamthought.saaa.adapters.files;

import com.dreamthought.saaa.deterministic.MutationRealizer;
import com.dreamthought.saaa.domain.Mutation;
import com.dreamthought.saaa.domain.WorkflowGraph;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Whole-file realization: the mutation patch becomes the entire new content of one file.
 * Hunk-based and AST-aware realization are out of scope; the operator model treats the resulting
 * diff as realization evidence rather than as the mutation itself.
 */
public final class TextMutationRealizer implements MutationRealizer {
    private final String relativeWorkflowPath;

    public TextMutationRealizer(String relativeWorkflowPath) {
        this.relativeWorkflowPath = Objects.requireNonNull(relativeWorkflowPath, "relativeWorkflowPath");
        if (relativeWorkflowPath.isBlank()) {
            throw new IllegalArgumentException("relativeWorkflowPath must not be blank");
        }
    }

    @Override
    public void realize(Path worktreePath, WorkflowGraph baseline, Mutation mutation) {
        Objects.requireNonNull(worktreePath, "worktreePath");
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(mutation, "mutation");

        Path root = worktreePath.toAbsolutePath().normalize();
        Path target = root.resolve(relativeWorkflowPath).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException(
                    "workflow path must stay inside the candidate worktree: " + relativeWorkflowPath);
        }
        if (!Files.isRegularFile(target)) {
            throw new IllegalStateException("workflow file not found in candidate worktree: " + relativeWorkflowPath);
        }
        try {
            Files.writeString(target, mutation.patch());
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to realize mutation into " + relativeWorkflowPath, exception);
        }
    }
}
```

- [ ] **Step 5: Run tests and lint**

Run: `.agentic-template/bin/project test && .agentic-template/bin/project lint`
Expected: PASS, and `ARCHITECTURE BOUNDARIES OK`.

- [ ] **Step 6: Commit**

```bash
git add modules/deterministic/src/main/java/com/dreamthought/saaa/deterministic/MutationRealizer.java \
        modules/adapters/src/main/java/com/dreamthought/saaa/adapters/files/TextMutationRealizer.java \
        modules/adapters/src/test/java/com/dreamthought/saaa/adapters/files/TextMutationRealizerTest.java
git commit -m "Add MutationRealizer port and whole-file text realizer

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 2: Realize into the candidate commit

**Files:**
- Modify: `modules/adapters/src/main/java/com/dreamthought/saaa/adapters/git/GitCandidateWorkspace.java`
- Test: `modules/adapters/src/integrationTest/java/com/dreamthought/saaa/adapters/git/GitCandidateWorkspaceIntegrationTest.java` (add a test to the existing class)

**Interfaces:**
- Consumes: `MutationRealizer` from Task 1.
- Produces: `new GitCandidateWorkspace(Path repositoryRoot, Path worktreesRoot, MutationRealizer realizer)`. Existing constructors keep working and default to a no-op realizer.

- [ ] **Step 1: Write the failing test**

Read the existing test class first to reuse its repository-setup helper. Add this test method to `GitCandidateWorkspaceIntegrationTest`:

```java
    @Test
    void realizesMutationIntoCandidateCommit(@TempDir Path tempDir) throws Exception {
        Path repo = initRepositoryWithWorkflowFile(tempDir);

        var workspace = new GitCandidateWorkspace(
                repo,
                repo.resolve(".worktrees"),
                new TextMutationRealizer("workflow.txt"));

        var candidate = workspace.createCommittedCandidate(
                new WorkflowGraph("toy", "v1", "old content"),
                new Mutation("MUT-1", "tighten guard", MutationScope.WORKFLOW_DEFINITION, "new content"));

        assertThat(candidate.worktreePath().resolve("workflow.txt")).hasContent("new content");

        String committed = runGit(candidate.worktreePath(), "show", candidate.commitSha() + ":workflow.txt");
        assertThat(committed.trim()).isEqualTo("new content");
    }
```

Add a helper in the same class if one does not already exist:

```java
    private static Path initRepositoryWithWorkflowFile(Path tempDir) throws Exception {
        Path repo = tempDir.resolve("repo");
        Files.createDirectories(repo);
        runGit(repo, "init", "--initial-branch=main");
        runGit(repo, "config", "user.name", "Test");
        runGit(repo, "config", "user.email", "test@example.invalid");
        Files.writeString(repo.resolve("workflow.txt"), "old content");
        runGit(repo, "add", "workflow.txt");
        runGit(repo, "commit", "-m", "baseline");
        return repo;
    }
```

If the existing class already has repository-setup and `runGit` helpers, use those instead of duplicating; only add the `workflow.txt` baseline file.

- [ ] **Step 2: Run test to verify it fails**

Run: `.agentic-template/bin/project integration-test`
Expected: FAIL — no three-argument constructor.

- [ ] **Step 3: Add the realizer collaborator**

In `GitCandidateWorkspace`, add the field and constructors:

```java
    private final MutationRealizer realizer;

    public GitCandidateWorkspace(Path repositoryRoot, Path worktreesRoot) {
        this(repositoryRoot, worktreesRoot, (worktree, baseline, mutation) -> { });
    }

    public GitCandidateWorkspace(Path repositoryRoot, Path worktreesRoot, MutationRealizer realizer) {
        this.repositoryRoot = Objects.requireNonNull(repositoryRoot, "repositoryRoot").toAbsolutePath().normalize();
        this.worktreesRoot = Objects.requireNonNull(worktreesRoot, "worktreesRoot").toAbsolutePath().normalize();
        this.realizer = Objects.requireNonNull(realizer, "realizer");
    }
```

Keep the existing one- and two-argument constructors delegating as they do now, so the two-argument one routes through the new no-op default above.

- [ ] **Step 4: Call the realizer and stage everything**

In `createCommittedCandidate`, after the candidate description file is written and before staging, insert:

```java
        realizer.realize(worktreePath, baseline, mutation);
```

Then replace the single-file staging:

```java
        String relativeCandidateFile = worktreePath.relativize(candidateFile).toString();
        git(worktreePath, "add", relativeCandidateFile).requireSuccess("stage candidate file");
```

with staging everything, so realized changes are committed:

```java
        git(worktreePath, "add", "-A").requireSuccess("stage candidate changes");
```

- [ ] **Step 5: Run tests**

Run: `.agentic-template/bin/project test && .agentic-template/bin/project integration-test`
Expected: PASS. The pre-existing workspace integration test must still pass — it uses a two-argument constructor and a no-op realizer, so its candidate commit still contains only the description file.

- [ ] **Step 6: Commit**

```bash
git add modules/adapters/src/main/java/com/dreamthought/saaa/adapters/git/GitCandidateWorkspace.java \
        modules/adapters/src/integrationTest/java/com/dreamthought/saaa/adapters/git/GitCandidateWorkspaceIntegrationTest.java
git commit -m "Realize mutations into the candidate commit

Candidate worktrees were byte-identical to HEAD apart from a description
file, so every candidate scored the same and nothing could evolve.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 3: RealizationInspector port and Git adapter

**Files:**
- Create: `modules/deterministic/src/main/java/com/dreamthought/saaa/deterministic/RealizationInspector.java`
- Create: `modules/domain/src/main/java/com/dreamthought/saaa/domain/RealizationSummary.java`
- Create: `modules/adapters/src/main/java/com/dreamthought/saaa/adapters/git/GitRealizationInspector.java`
- Test: `modules/adapters/src/integrationTest/java/com/dreamthought/saaa/adapters/git/GitRealizationInspectorIntegrationTest.java`

**Interfaces:**
- Produces: `RealizationSummary(int filesChanged, int linesChanged)`; `RealizationInspector.inspect(Candidate) -> RealizationSummary`; `new GitRealizationInspector()`.

- [ ] **Step 1: Write the failing test**

```java
package com.dreamthought.saaa.adapters.git;

import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.domain.Candidate;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class GitRealizationInspectorIntegrationTest {
    @Test
    void countsFilesAndLinesChangedAgainstTheParentCommit(@TempDir Path tempDir) throws Exception {
        Path repo = tempDir.resolve("repo");
        Files.createDirectories(repo);
        git(repo, "init", "--initial-branch=main");
        git(repo, "config", "user.name", "Test");
        git(repo, "config", "user.email", "test@example.invalid");
        Files.writeString(repo.resolve("workflow.txt"), "line one\nline two\n");
        git(repo, "add", "-A");
        git(repo, "commit", "-m", "baseline");

        Files.writeString(repo.resolve("workflow.txt"), "line one\nline changed\n");
        git(repo, "add", "-A");
        git(repo, "commit", "-m", "candidate");
        String sha = git(repo, "rev-parse", "HEAD").trim();

        var summary = new GitRealizationInspector()
                .inspect(new Candidate("cand-1", "MUT-1", "candidate/toy-MUT-1", repo, sha));

        assertThat(summary.filesChanged()).isEqualTo(1);
        assertThat(summary.linesChanged()).isEqualTo(2);
    }

    private static String git(Path dir, String... args) throws Exception {
        var command = new java.util.ArrayList<String>();
        command.add("git");
        command.addAll(java.util.List.of(args));
        var process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        if (process.waitFor() != 0) {
            throw new IllegalStateException("git failed: " + String.join(" ", args) + "\n" + output);
        }
        return output;
    }
}
```

`linesChanged` is 2 because `git diff --numstat` reports one added and one deleted line for a modified line.

- [ ] **Step 2: Run test to verify it fails**

Run: `.agentic-template/bin/project integration-test`
Expected: FAIL — `GitRealizationInspector` does not exist.

- [ ] **Step 3: Write the domain record**

```java
package com.dreamthought.saaa.domain;

/** How much a candidate's realization actually changed, measured from its Git diff. */
public record RealizationSummary(int filesChanged, int linesChanged) {
    public RealizationSummary {
        if (filesChanged < 0) {
            throw new IllegalArgumentException("filesChanged must not be negative");
        }
        if (linesChanged < 0) {
            throw new IllegalArgumentException("linesChanged must not be negative");
        }
    }
}
```

- [ ] **Step 4: Write the port**

```java
package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.RealizationSummary;

/**
 * Reports how large a candidate's realized change is. Scoring needs this for parsimony, and asking
 * through a port keeps Git out of the deterministic layer.
 */
@FunctionalInterface
public interface RealizationInspector {
    RealizationSummary inspect(Candidate candidate);
}
```

- [ ] **Step 5: Write the adapter**

```java
package com.dreamthought.saaa.adapters.git;

import com.dreamthought.saaa.deterministic.RealizationInspector;
import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.RealizationSummary;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Measures the candidate commit against its first parent with {@code git diff --numstat}. */
public final class GitRealizationInspector implements RealizationInspector {
    @Override
    public RealizationSummary inspect(Candidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        String output = run(
                candidate,
                "git", "diff", "--numstat", candidate.commitSha() + "^", candidate.commitSha());

        int files = 0;
        int lines = 0;
        for (String row : output.split("\n")) {
            String trimmed = row.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] columns = trimmed.split("\\s+");
            if (columns.length < 3) {
                continue;
            }
            files++;
            lines += parseCount(columns[0]) + parseCount(columns[1]);
        }
        return new RealizationSummary(files, lines);
    }

    /** Binary files report "-" instead of a count. */
    private static int parseCount(String column) {
        if ("-".equals(column)) {
            return 0;
        }
        return Integer.parseInt(column);
    }

    private static String run(Candidate candidate, String... command) {
        try {
            Process process = new ProcessBuilder(List.of(command))
                    .directory(candidate.worktreePath().toFile())
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes());
            if (process.waitFor() != 0) {
                throw new IllegalStateException(
                        "git diff failed for candidate " + candidate.id() + ": " + output);
            }
            return output;
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to inspect candidate " + candidate.id(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted inspecting candidate " + candidate.id(), exception);
        }
    }
}
```

Imports needed: `java.io.IOException`, `java.io.UncheckedIOException`, `java.util.List`, `java.util.Objects`, plus the three project types.

- [ ] **Step 6: Run tests and lint**

Run: `.agentic-template/bin/project integration-test && .agentic-template/bin/project lint`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add modules/domain/src/main/java/com/dreamthought/saaa/domain/RealizationSummary.java \
        modules/deterministic/src/main/java/com/dreamthought/saaa/deterministic/RealizationInspector.java \
        modules/adapters/src/main/java/com/dreamthought/saaa/adapters/git/GitRealizationInspector.java \
        modules/adapters/src/integrationTest/java/com/dreamthought/saaa/adapters/git/GitRealizationInspectorIntegrationTest.java
git commit -m "Add RealizationInspector port measuring candidate diff size

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 4: PhenotypeBridgeScorer

**Files:**
- Create: `modules/deterministic/src/main/java/com/dreamthought/saaa/deterministic/ScoringConfig.java`
- Create: `modules/deterministic/src/main/java/com/dreamthought/saaa/deterministic/PhenotypeBridgeScorer.java`
- Test: `modules/deterministic/src/test/java/com/dreamthought/saaa/deterministic/PhenotypeBridgeScorerTest.java`

**Interfaces:**
- Consumes: `RealizationInspector` (Task 3), `PhenotypeFitnessScorer`, `PhenotypeEvidence`, `BehaviorCaseEvidence`.
- Produces: `new PhenotypeBridgeScorer(RealizationInspector inspector, ScoringConfig config)` implementing `FitnessScorer`; `new ScoringConfig(Set<String> behaviorCaseNames, int maxLinesChanged, Map<String,Double> benchmarkBudgets)`.

- [ ] **Step 1: Write the failing test**

```java
package com.dreamthought.saaa.deterministic;

import static com.dreamthought.saaa.domain.CheckEvidence.failed;
import static com.dreamthought.saaa.domain.CheckEvidence.passed;
import static com.dreamthought.saaa.domain.FitnessDecision.DISCARD;
import static com.dreamthought.saaa.domain.FitnessDecision.PROMOTE;
import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.domain.BenchmarkEvidence;
import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.RealizationSummary;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class PhenotypeBridgeScorerTest {
    private static final Candidate CANDIDATE =
            new Candidate("cand-1", "MUT-1", "candidate/toy-MUT-1", Path.of("/tmp/wt"), "abc1234");

    @Test
    void derivesPhenotypeEvidenceAndHardGatesBeforeWeighting() {
        var scorer = scorer(new RealizationSummary(1, 8), 80);

        var result = scorer.score(CANDIDATE, new EvaluationEvidence(
                List.of(passed("build", "ok"), failed("publish-guard", "regressed")),
                List.of(),
                Instant.parse("2026-07-28T00:00:00Z")));

        assertThat(result.decision()).isEqualTo(DISCARD);
        assertThat(result.aggregateScore()).isZero();
        assertThat(result.objectives())
                .containsEntry(PhenotypeFitnessScorer.REQUIRED_BEHAVIOR_CASES_GATE, 0.0);
    }

    @Test
    void promotesWhenBehaviourCasesPassAndTheChangeIsSmall() {
        var scorer = scorer(new RealizationSummary(1, 8), 80);

        var result = scorer.score(CANDIDATE, new EvaluationEvidence(
                List.of(passed("build", "ok"), passed("publish-guard", "ok")),
                List.of(),
                Instant.parse("2026-07-28T00:00:00Z")));

        assertThat(result.decision()).isEqualTo(PROMOTE);
        assertThat(result.objectives()).containsEntry("task_success", 1.0);
    }

    @Test
    void scoresParsimonyFromRealizedDiffSizeAgainstBounds() {
        var tight = scorer(new RealizationSummary(1, 8), 80);
        var sprawling = scorer(new RealizationSummary(1, 72), 80);

        var evidence = new EvaluationEvidence(
                List.of(passed("build", "ok"), passed("publish-guard", "ok")),
                List.of(),
                Instant.parse("2026-07-28T00:00:00Z"));

        assertThat(tight.score(CANDIDATE, evidence).objectives()).containsEntry("parsimony", 0.9);
        assertThat(sprawling.score(CANDIDATE, evidence).objectives()).containsEntry("parsimony", 0.1);
        assertThat(tight.score(CANDIDATE, evidence).aggregateScore())
                .isGreaterThan(sprawling.score(CANDIDATE, evidence).aggregateScore());
    }

    @Test
    void scoresCostLatencyFromTheWorstBenchmarkAgainstItsBudget() {
        var scorer = new PhenotypeBridgeScorer(
                candidate -> new RealizationSummary(1, 8),
                new ScoringConfig(Set.of("publish-guard"), 80, Map.of("publish-latency", 50.0)));

        var result = scorer.score(CANDIDATE, new EvaluationEvidence(
                List.of(passed("build", "ok"), passed("publish-guard", "ok")),
                List.of(BenchmarkEvidence.measurement("publish-latency", 100.0, "ms")),
                Instant.parse("2026-07-28T00:00:00Z")));

        assertThat(result.objectives()).containsEntry("cost_latency_budget", 0.5);
    }

    private static PhenotypeBridgeScorer scorer(RealizationSummary summary, int maxLinesChanged) {
        return new PhenotypeBridgeScorer(
                candidate -> summary,
                new ScoringConfig(Set.of("publish-guard"), maxLinesChanged, Map.of()));
    }
}
```

Check the arithmetic before implementing. With `task_success` 1.0, `reliability` 1.0, `cost_latency_budget` 1.0, `behavioral_safety` 1.0 and `parsimony` 0.9, the weighted sum is `0.40 + 0.20 + 0.20 + 0.10 + 0.09 = 0.99`, which promotes. With `parsimony` 0.1 the sum is `0.91`, which also promotes — that is expected; parsimony is a tiebreaker, not a gate.

- [ ] **Step 2: Run test to verify it fails**

Run: `.agentic-template/bin/project test`
Expected: FAIL — `ScoringConfig` and `PhenotypeBridgeScorer` do not exist.

- [ ] **Step 3: Write ScoringConfig**

```java
package com.dreamthought.saaa.deterministic;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Policy inputs the bridge needs that do not come from evidence.
 *
 * @param behaviorCaseNames checks that are required behaviour rather than build health; these
 *                          hard-gate promotion
 * @param maxLinesChanged   the change budget parsimony is measured against
 * @param benchmarkBudgets  benchmark name to its budget in the benchmark's own unit
 */
public record ScoringConfig(Set<String> behaviorCaseNames, int maxLinesChanged, Map<String, Double> benchmarkBudgets) {
    public ScoringConfig {
        behaviorCaseNames = Set.copyOf(Objects.requireNonNull(behaviorCaseNames, "behaviorCaseNames"));
        benchmarkBudgets = Map.copyOf(Objects.requireNonNull(benchmarkBudgets, "benchmarkBudgets"));
        if (behaviorCaseNames.isEmpty()) {
            throw new IllegalArgumentException("at least one check must be declared a behaviour case");
        }
        if (maxLinesChanged <= 0) {
            throw new IllegalArgumentException("maxLinesChanged must be positive");
        }
    }
}
```

- [ ] **Step 4: Write the bridge**

```java
package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.BenchmarkEvidence;
import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.CheckEvidence;
import com.dreamthought.saaa.domain.CheckStatus;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.FitnessResult;
import com.dreamthought.saaa.domain.RealizationSummary;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Adapts the loop's evidence into phenotype evidence and delegates to the hard-gated scorer.
 *
 * <p>Every derivation here is deterministic and evidence-only. The derivations are deliberately
 * crude for a first slice; the one that carries real signal is parsimony, because it reads the
 * realized diff and therefore differs between candidates.
 */
public final class PhenotypeBridgeScorer implements FitnessScorer {
    private final RealizationInspector inspector;
    private final ScoringConfig config;
    private final PhenotypeFitnessScorer delegate = new PhenotypeFitnessScorer();

    public PhenotypeBridgeScorer(RealizationInspector inspector, ScoringConfig config) {
        this.inspector = Objects.requireNonNull(inspector, "inspector");
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public FitnessResult score(Candidate candidate, EvaluationEvidence evidence) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(evidence, "evidence");

        List<BehaviorCaseEvidence> behaviorCases = evidence.checks().stream()
                .filter(check -> config.behaviorCaseNames().contains(check.name()))
                .map(PhenotypeBridgeScorer::toBehaviorCase)
                .toList();

        RealizationSummary realization = Objects.requireNonNull(
                inspector.inspect(candidate), "realization summary");

        Map<String, Double> objectives = new LinkedHashMap<>();
        objectives.put("task_success", passedFraction(behaviorCases));
        objectives.put("reliability", allChecksRan(evidence) ? 1.0 : 0.0);
        objectives.put("cost_latency_budget", budgetScore(evidence.benchmarks()));
        objectives.put("behavioral_safety", 1.0);
        objectives.put("parsimony", parsimony(realization));

        return delegate.score(candidate, new PhenotypeEvidence(evidence, behaviorCases, objectives));
    }

    private static BehaviorCaseEvidence toBehaviorCase(CheckEvidence check) {
        if (check.status() == CheckStatus.PASSED) {
            return BehaviorCaseEvidence.passed(check.name(), check.summary());
        }
        return BehaviorCaseEvidence.failed(check.name(), check.summary());
    }

    private static double passedFraction(List<BehaviorCaseEvidence> behaviorCases) {
        if (behaviorCases.isEmpty()) {
            return 0.0;
        }
        long passed = behaviorCases.stream().filter(c -> c.status() == CheckStatus.PASSED).count();
        return (double) passed / behaviorCases.size();
    }

    /** A check that timed out records "timed out" in its summary; anything else ran to completion. */
    private static boolean allChecksRan(EvaluationEvidence evidence) {
        return evidence.checks().stream().noneMatch(check -> check.summary().contains("timed out"));
    }

    private double budgetScore(List<BenchmarkEvidence> benchmarks) {
        double worst = 1.0;
        for (BenchmarkEvidence benchmark : benchmarks) {
            Double budget = config.benchmarkBudgets().get(benchmark.name());
            if (budget == null || benchmark.value() <= 0.0) {
                continue;
            }
            worst = Math.min(worst, clamp(budget / benchmark.value()));
        }
        return worst;
    }

    private double parsimony(RealizationSummary realization) {
        return clamp(1.0 - ((double) realization.linesChanged() / config.maxLinesChanged()));
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
```

- [ ] **Step 5: Run tests and lint**

Run: `.agentic-template/bin/project test && .agentic-template/bin/project lint`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/deterministic/src/main/java/com/dreamthought/saaa/deterministic/ScoringConfig.java \
        modules/deterministic/src/main/java/com/dreamthought/saaa/deterministic/PhenotypeBridgeScorer.java \
        modules/deterministic/src/test/java/com/dreamthought/saaa/deterministic/PhenotypeBridgeScorerTest.java
git commit -m "Bridge loop evidence into the hard-gated phenotype scorer

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 5: EvolutionReporter port and loop reporting

**Files:**
- Create: `modules/deterministic/src/main/java/com/dreamthought/saaa/deterministic/EvolutionReporter.java`
- Modify: `modules/deterministic/src/main/java/com/dreamthought/saaa/deterministic/MutationEvaluationLoop.java`
- Test: `modules/deterministic/src/test/java/com/dreamthought/saaa/deterministic/MutationEvaluationLoopReportingTest.java`

**Interfaces:**
- Produces: `EvolutionReporter` with `proposed(Mutation)`, `candidateCreated(Candidate)`, `evidenceCollected(EvaluationEvidence)`, `scored(FitnessResult)`, and `EvolutionReporter.NO_OP`. A new `MutationEvaluationLoop` constructor taking a reporter as its ninth argument, before `Clock`.

- [ ] **Step 1: Write the failing test**

Read `MutationEvaluationLoopAcceptanceTest` first and reuse its fake ports rather than writing new ones. Create:

```java
package com.dreamthought.saaa.deterministic;

import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.FitnessResult;
import com.dreamthought.saaa.domain.Mutation;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class MutationEvaluationLoopReportingTest {
    @Test
    void reportsEveryStageThroughThePort() {
        List<String> events = new ArrayList<>();
        EvolutionReporter reporter = new EvolutionReporter() {
            @Override
            public void proposed(Mutation mutation) {
                events.add("proposed:" + mutation.id());
            }

            @Override
            public void candidateCreated(Candidate candidate) {
                events.add("candidate:" + candidate.id());
            }

            @Override
            public void evidenceCollected(EvaluationEvidence evidence) {
                events.add("evidence:" + evidence.checks().size());
            }

            @Override
            public void scored(FitnessResult result) {
                events.add("scored:" + result.decision());
            }
        };

        var baseline = new WorkflowGraph("toy", "v1", "old content");
        var mutation = new Mutation("MUT-1", "tighten guard", MutationScope.WORKFLOW_DEFINITION, "new content");
        var candidate = new Candidate(
                "candidate-MUT-1", "MUT-1", "candidate/toy-MUT-1", Path.of(".worktrees/c"), "abc1234");

        var loop = new MutationEvaluationLoop(
                ignored -> mutation,
                (workflow, proposed) -> ValidationResult.passed(),
                (workflow, proposed) -> candidate,
                ignored -> List.of(CheckEvidence.passed("workflow-check", "ok")),
                ignored -> List.of(),
                (evaluated, evidence) -> new FitnessResult(
                        evaluated, evidence, Map.of(), 0.10, FitnessDecision.DISCARD),
                new ExperimentMetadataStore() {
                    @Override
                    public void recordCandidate(Candidate recorded) { }

                    @Override
                    public void recordFitness(FitnessResult recorded) { }
                },
                new CandidateDecisionSink() {
                    @Override
                    public void promote(Candidate promoted, FitnessResult result) { }

                    @Override
                    public void discard(Candidate discarded, FitnessResult result) { }
                },
                reporter,
                Clock.fixed(Instant.parse("2026-07-28T00:00:00Z"), ZoneOffset.UTC));

        loop.evaluate(baseline);

        assertThat(events).containsExactly(
                "proposed:MUT-1",
                "candidate:candidate-MUT-1",
                "evidence:1",
                "scored:DISCARD");
    }
}
```

Add these imports to the test: `com.dreamthought.saaa.domain.CheckEvidence`, `FitnessDecision`, `MutationScope`, `ValidationResult`, `WorkflowGraph`, `java.nio.file.Path`, `java.time.Clock`, `java.time.Instant`, `java.time.ZoneOffset`, `java.util.List`, `java.util.Map`.

The metadata store and decision sink are written as anonymous classes rather than lambdas because both interfaces declare two methods. The recording fakes in `MutationEvaluationLoopAcceptanceTest` are private nested classes in a different source set, so they cannot be reused here.

- [ ] **Step 2: Run test to verify it fails**

Run: `.agentic-template/bin/project test`
Expected: FAIL — `EvolutionReporter` does not exist.

- [ ] **Step 3: Write the port**

```java
package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.FitnessResult;
import com.dreamthought.saaa.domain.Mutation;

/**
 * Progress events from one evaluation. The loop reports; it never prints. That keeps a terminal, a
 * live session view and a remote caller as equal consumers of the same run.
 */
public interface EvolutionReporter {
    EvolutionReporter NO_OP = new EvolutionReporter() { };

    default void proposed(Mutation mutation) { }

    default void candidateCreated(Candidate candidate) { }

    default void evidenceCollected(EvaluationEvidence evidence) { }

    default void scored(FitnessResult result) { }
}
```

- [ ] **Step 4: Report from the loop**

Add the field, keep every existing constructor working by defaulting to `NO_OP`, and add one that accepts a reporter:

```java
    private final EvolutionReporter reporter;
```

Route the existing eight- and nine-argument constructors through a new full constructor that takes `EvolutionReporter reporter` immediately before `Clock clock`, passing `EvolutionReporter.NO_OP` from the older ones. Then add the four calls inside `evaluate`:

- after the mutation is proposed and non-null checked: `reporter.proposed(mutation);`
- after `metadataStore.recordCandidate(candidate);`: `reporter.candidateCreated(candidate);`
- after the `EvaluationEvidence` is constructed: `reporter.evidenceCollected(evidence);`
- after `metadataStore.recordFitness(result);`: `reporter.scored(result);`

Do not move the existing validation or the candidate-mismatch guard.

- [ ] **Step 5: Run tests and lint**

Run: `.agentic-template/bin/project test && .agentic-template/bin/project component-test && .agentic-template/bin/project lint`
Expected: PASS. The existing acceptance test must compile unchanged because it uses an older constructor.

- [ ] **Step 6: Commit**

```bash
git add modules/deterministic/src/main/java/com/dreamthought/saaa/deterministic/EvolutionReporter.java \
        modules/deterministic/src/main/java/com/dreamthought/saaa/deterministic/MutationEvaluationLoop.java \
        modules/deterministic/src/test/java/com/dreamthought/saaa/deterministic/MutationEvaluationLoopReportingTest.java
git commit -m "Report loop progress through a port instead of printing

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 6: Fixture proposer and profile registry

**Files:**
- Create: `modules/adapters/src/main/java/com/dreamthought/saaa/adapters/fixture/FixtureMutationProposer.java`
- Test: `modules/adapters/src/test/java/com/dreamthought/saaa/adapters/fixture/FixtureMutationProposerTest.java`
- Create: `modules/cli/src/main/java/com/dreamthought/saaa/cli/ProposerProfileRegistry.java`
- Test: `modules/cli/src/test/java/com/dreamthought/saaa/cli/ProposerProfileRegistryTest.java`

**Interfaces:**
- Produces: `new FixtureMutationProposer(Path fixtureFile)` implementing `MutationProposer`; `ProposerProfileRegistry.resolve(String profileName, Path targetFolder) -> MutationProposer` and `ProposerProfileRegistry.knownNames() -> List<String>`.

- [ ] **Step 1: Write the failing proposer test**

```java
package com.dreamthought.saaa.adapters.fixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dreamthought.saaa.domain.MutationScope;
import com.dreamthought.saaa.domain.WorkflowGraph;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FixtureMutationProposerTest {
    private final WorkflowGraph baseline = new WorkflowGraph("toy", "v1", "old content");

    @Test
    void readsSummaryFromTheFirstLineAndPatchFromTheRest(@TempDir Path dir) throws IOException {
        Path fixture = dir.resolve("fixture-mutation.txt");
        Files.writeString(fixture, "tighten the publish guard\nline one\nline two\n");

        var mutation = new FixtureMutationProposer(fixture).proposeFor(baseline);

        assertThat(mutation.summary()).isEqualTo("tighten the publish guard");
        assertThat(mutation.patch()).isEqualTo("line one\nline two\n");
        assertThat(mutation.scope()).isEqualTo(MutationScope.WORKFLOW_DEFINITION);
        assertThat(mutation.id()).isNotBlank();
    }

    @Test
    void producesTheSameMutationEveryTimeForTheSameFixture(@TempDir Path dir) throws IOException {
        Path fixture = dir.resolve("fixture-mutation.txt");
        Files.writeString(fixture, "tighten the publish guard\nnew content\n");
        var proposer = new FixtureMutationProposer(fixture);

        assertThat(proposer.proposeFor(baseline)).isEqualTo(proposer.proposeFor(baseline));
    }

    @Test
    void failsClearlyWhenTheFixtureIsMissing(@TempDir Path dir) {
        assertThatThrownBy(() -> new FixtureMutationProposer(dir.resolve("absent.txt")).proposeFor(baseline))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("absent.txt");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.agentic-template/bin/project test`
Expected: FAIL — `FixtureMutationProposer` does not exist.

- [ ] **Step 3: Write the proposer**

```java
package com.dreamthought.saaa.adapters.fixture;

import com.dreamthought.saaa.deterministic.MutationProposer;
import com.dreamthought.saaa.domain.Mutation;
import com.dreamthought.saaa.domain.MutationScope;
import com.dreamthought.saaa.domain.WorkflowGraph;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Reads a canned mutation from a file so the pipe can be exercised with no model and no network.
 *
 * <p>The format is deliberately trivial — first line is the summary, the remainder is the proposed
 * new file content — because there is no TOON reader in Java yet. This is a recorded deviation from
 * the structured-data rule, scoped to this proposer, and it retires when the TOON envelope reader
 * lands.
 *
 * <p>Being deterministic, this proposer cannot supply the variance a population needs. That is a
 * dependency of the population slice, not a gap here.
 */
public final class FixtureMutationProposer implements MutationProposer {
    private final Path fixtureFile;

    public FixtureMutationProposer(Path fixtureFile) {
        this.fixtureFile = Objects.requireNonNull(fixtureFile, "fixtureFile");
    }

    @Override
    public Mutation proposeFor(WorkflowGraph baseline) {
        Objects.requireNonNull(baseline, "baseline");
        if (!Files.isRegularFile(fixtureFile)) {
            throw new IllegalStateException("fixture mutation file not found: " + fixtureFile);
        }
        String content = read();
        int firstBreak = content.indexOf('\n');
        if (firstBreak < 0) {
            throw new IllegalStateException(
                    "fixture mutation must have a summary line and a body: " + fixtureFile);
        }
        String summary = content.substring(0, firstBreak).trim();
        String patch = content.substring(firstBreak + 1);
        if (summary.isBlank() || patch.isBlank()) {
            throw new IllegalStateException(
                    "fixture mutation must have a summary line and a body: " + fixtureFile);
        }
        return new Mutation("MUT-" + baseline.id() + "-fixture", summary, MutationScope.WORKFLOW_DEFINITION, patch);
    }

    private String read() {
        try {
            return Files.readString(fixtureFile);
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to read fixture mutation: " + fixtureFile, exception);
        }
    }
}
```

- [ ] **Step 4: Write the failing registry test**

```java
package com.dreamthought.saaa.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ProposerProfileRegistryTest {
    private final ProposerProfileRegistry registry = new ProposerProfileRegistry();

    @Test
    void resolvesKnownProfileAndListsKnownNamesOnFailure() {
        assertThat(registry.knownNames()).containsExactly("fixture");
        assertThat(registry.resolve("fixture", Path.of("some/folder"))).isNotNull();

        assertThatThrownBy(() -> registry.resolve("gpt-cloud", Path.of("some/folder")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unknown proposer profile: gpt-cloud; known profiles: fixture");
    }
}
```

- [ ] **Step 5: Write the registry**

```java
package com.dreamthought.saaa.cli;

import com.dreamthought.saaa.adapters.fixture.FixtureMutationProposer;
import com.dreamthought.saaa.deterministic.MutationProposer;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Resolves a profile name to a proposer. Choosing a proposer is composition, not policy, so this
 * lives in the CLI rather than the deterministic layer.
 *
 * <p>Adding a live provider means adding an entry here plus provider-neutral {@code ChatModel}
 * construction. The proposal adapter already accepts any {@code ChatModel}, so no adapter changes
 * are needed for any provider.
 */
public final class ProposerProfileRegistry {
    private final Map<String, Function<Path, MutationProposer>> factories = new LinkedHashMap<>();

    public ProposerProfileRegistry() {
        factories.put("fixture", folder ->
                new FixtureMutationProposer(folder.resolve(".saaa/fixture-mutation.txt")));
    }

    public List<String> knownNames() {
        return List.copyOf(factories.keySet());
    }

    public MutationProposer resolve(String profileName, Path targetFolder) {
        Objects.requireNonNull(profileName, "profileName");
        Objects.requireNonNull(targetFolder, "targetFolder");
        Function<Path, MutationProposer> factory = factories.get(profileName);
        if (factory == null) {
            throw new IllegalArgumentException(
                    "unknown proposer profile: " + profileName
                            + "; known profiles: " + String.join(", ", knownNames()));
        }
        return factory.apply(targetFolder);
    }
}
```

- [ ] **Step 6: Give the CLI module its adapter dependency**

In `modules/cli/build.gradle.kts`, add the adapters project so the registry can construct adapter types:

```kotlin
dependencies {
    implementation(project(":deterministic"))
    implementation(project(":adapters"))
    implementation(libs.picocli)
}
```

- [ ] **Step 7: Run tests and lint**

Run: `.agentic-template/bin/project test && .agentic-template/bin/project lint`
Expected: PASS. Lint must stay green: the CLI may reference adapter classes, but `dev.langchain4j` must not appear in any CLI source file.

- [ ] **Step 8: Commit**

```bash
git add modules/adapters/src/main/java/com/dreamthought/saaa/adapters/fixture/ \
        modules/adapters/src/test/java/com/dreamthought/saaa/adapters/fixture/ \
        modules/cli/src/main/java/com/dreamthought/saaa/cli/ProposerProfileRegistry.java \
        modules/cli/src/test/java/com/dreamthought/saaa/cli/ProposerProfileRegistryTest.java \
        modules/cli/build.gradle.kts
git commit -m "Add fixture proposer and the profile registry seam

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 7: Journal reporter and decision sink

**Files:**
- Create: `modules/adapters/src/main/java/com/dreamthought/saaa/adapters/journal/JournalReporter.java`
- Create: `modules/adapters/src/main/java/com/dreamthought/saaa/adapters/journal/JournalDecisionSink.java`
- Test: `modules/adapters/src/test/java/com/dreamthought/saaa/adapters/journal/JournalReporterTest.java`

**Interfaces:**
- Consumes: `EvolutionReporter` (Task 5), `CandidateDecisionSink`.
- Produces: `new JournalReporter(Path journalFile, Clock clock)` implementing `EvolutionReporter`; `new JournalDecisionSink()` implementing `CandidateDecisionSink`.

- [ ] **Step 1: Write the failing test**

```java
package com.dreamthought.saaa.adapters.journal;

import static com.dreamthought.saaa.domain.CheckEvidence.passed;
import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.FitnessDecision;
import com.dreamthought.saaa.domain.FitnessResult;
import com.dreamthought.saaa.domain.Mutation;
import com.dreamthought.saaa.domain.MutationScope;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class JournalReporterTest {
    private static final Candidate CANDIDATE =
            new Candidate("cand-1", "MUT-1", "candidate/toy-MUT-1", Path.of("/tmp/wt"), "abc1234");

    @Test
    void appendsATraceableEntryForOneRun(@TempDir Path dir) throws IOException {
        Path journal = dir.resolve("journal.md");
        var reporter = new JournalReporter(
                journal, Clock.fixed(Instant.parse("2026-07-28T09:14:02Z"), ZoneOffset.UTC));

        reporter.proposed(new Mutation("MUT-1", "tighten the publish guard",
                MutationScope.WORKFLOW_DEFINITION, "new content"));
        reporter.candidateCreated(CANDIDATE);
        reporter.evidenceCollected(evidence());
        reporter.scored(new FitnessResult(CANDIDATE, evidence(),
                Map.of("parsimony", 0.9), 0.87, FitnessDecision.PROMOTE));

        String written = Files.readString(journal);
        assertThat(written)
                .contains("## 2026-07-28T09:14:02Z")
                .contains("tighten the publish guard")
                .contains("abc1234")
                .contains("publish-guard")
                .contains("0.87")
                .contains("PROMOTE");
    }

    @Test
    void appendsRatherThanOverwritingPreviousRuns(@TempDir Path dir) throws IOException {
        Path journal = dir.resolve("journal.md");
        Files.writeString(journal, "# Journal\n\n## earlier run\n");
        var reporter = new JournalReporter(
                journal, Clock.fixed(Instant.parse("2026-07-28T09:14:02Z"), ZoneOffset.UTC));

        reporter.proposed(new Mutation("MUT-1", "tighten the publish guard",
                MutationScope.WORKFLOW_DEFINITION, "new content"));
        reporter.candidateCreated(CANDIDATE);
        reporter.evidenceCollected(evidence());
        reporter.scored(new FitnessResult(CANDIDATE, evidence(),
                Map.of(), 0.87, FitnessDecision.PROMOTE));

        assertThat(Files.readString(journal))
                .contains("## earlier run")
                .contains("## 2026-07-28T09:14:02Z");
    }

    private static EvaluationEvidence evidence() {
        return new EvaluationEvidence(
                List.of(passed("publish-guard", "ok")), List.of(), Instant.parse("2026-07-28T09:14:00Z"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.agentic-template/bin/project test`
Expected: FAIL — `JournalReporter` does not exist.

- [ ] **Step 3: Write the journal reporter**

Accumulate events, then append one markdown section when `scored` arrives.

```java
package com.dreamthought.saaa.adapters.journal;

import com.dreamthought.saaa.deterministic.EvolutionReporter;
import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.FitnessResult;
import com.dreamthought.saaa.domain.Mutation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Appends a human-readable entry per run.
 *
 * <p>The journal is a narrative view, never the source of truth for a decision. Git commits and the
 * experiment metadata store hold provenance, which is why a later slice can safely compact this file
 * for readability without weakening the audit trail.
 */
public final class JournalReporter implements EvolutionReporter {
    private final Path journalFile;
    private final Clock clock;

    private Mutation mutation;
    private Candidate candidate;
    private EvaluationEvidence evidence;

    public JournalReporter(Path journalFile, Clock clock) {
        this.journalFile = Objects.requireNonNull(journalFile, "journalFile");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void proposed(Mutation mutation) {
        this.mutation = mutation;
    }

    @Override
    public void candidateCreated(Candidate candidate) {
        this.candidate = candidate;
    }

    @Override
    public void evidenceCollected(EvaluationEvidence evidence) {
        this.evidence = evidence;
    }

    @Override
    public void scored(FitnessResult result) {
        Objects.requireNonNull(result, "result");
        append(entry(result));
    }

    private String entry(FitnessResult result) {
        String checks = evidence == null
                ? "none"
                : evidence.checks().stream()
                        .map(check -> check.name() + " " + check.status())
                        .collect(Collectors.joining(", "));
        String hypothesis = mutation == null ? "unknown" : mutation.summary();
        String commit = candidate == null ? "unknown" : candidate.commitSha();
        String candidateId = candidate == null ? "unknown" : candidate.id();

        return """

                ## %s  %s

                **Hypothesis** %s

                | | |
                |---|---|
                | commit | %s |
                | checks | %s |
                | score | %.2f |
                | decision | %s |

                Scored %.2f against a threshold of 0.80.
                """.formatted(
                        Instant.now(clock),
                        candidateId,
                        hypothesis,
                        commit,
                        checks,
                        result.aggregateScore(),
                        result.decision(),
                        result.aggregateScore());
    }

    private void append(String text) {
        try {
            Files.createDirectories(journalFile.toAbsolutePath().getParent());
            Files.writeString(
                    journalFile, text, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to append to journal: " + journalFile, exception);
        }
    }
}
```

- [ ] **Step 4: Write the decision sink**

```java
package com.dreamthought.saaa.adapters.journal;

import com.dreamthought.saaa.deterministic.CandidateDecisionSink;
import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.FitnessResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Records the outcome without acting on it. Creating a promoted Git ref is CHG-002 task T5; this
 * slice deliberately stops at recording so promotion semantics stay unproven rather than assumed.
 */
public final class JournalDecisionSink implements CandidateDecisionSink {
    private final List<String> decisions = new ArrayList<>();

    @Override
    public void promote(Candidate candidate, FitnessResult result) {
        record("PROMOTE", candidate, result);
    }

    @Override
    public void discard(Candidate candidate, FitnessResult result) {
        record("DISCARD", candidate, result);
    }

    public List<String> decisions() {
        return List.copyOf(decisions);
    }

    private void record(String decision, Candidate candidate, FitnessResult result) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(result, "result");
        decisions.add(decision + " " + candidate.id() + " " + result.aggregateScore());
    }
}
```

- [ ] **Step 5: Test the decision sink**

The `decisions()` accessor needs a consumer or it is dead public surface. Add to
`modules/adapters/src/test/java/com/dreamthought/saaa/adapters/journal/JournalDecisionSinkTest.java`:

```java
package com.dreamthought.saaa.adapters.journal;

import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.FitnessDecision;
import com.dreamthought.saaa.domain.FitnessResult;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class JournalDecisionSinkTest {
    private static final Candidate CANDIDATE =
            new Candidate("cand-1", "MUT-1", "candidate/toy-MUT-1", Path.of("/tmp/wt"), "abc1234");

    @Test
    void recordsBothOutcomesInOrder() {
        var sink = new JournalDecisionSink();

        sink.promote(CANDIDATE, result(FitnessDecision.PROMOTE, 0.87));
        sink.discard(CANDIDATE, result(FitnessDecision.DISCARD, 0.10));

        assertThat(sink.decisions()).containsExactly(
                "PROMOTE cand-1 0.87",
                "DISCARD cand-1 0.1");
    }

    private static FitnessResult result(FitnessDecision decision, double score) {
        var evidence = new EvaluationEvidence(List.of(), List.of(), Instant.parse("2026-07-28T00:00:00Z"));
        return new FitnessResult(CANDIDATE, evidence, Map.of(), score, decision);
    }
}
```

Note `0.1` not `0.10` — `Double.toString(0.10)` is `"0.1"`. Run the test and match
the implementation's string format to whatever it actually produces rather than
guessing; if the format differs, fix the assertion, not the production code.

- [ ] **Step 6: Run tests and lint**

Run: `.agentic-template/bin/project test && .agentic-template/bin/project lint`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add modules/adapters/src/main/java/com/dreamthought/saaa/adapters/journal/ \
        modules/adapters/src/test/java/com/dreamthought/saaa/adapters/journal/
git commit -m "Add journal reporter and recording decision sink

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 8: The toy-workflow fixture

**Files:**
- Create: `fixtures/toy-workflow/workflow.txt`
- Create: `fixtures/toy-workflow/check.sh`
- Create: `fixtures/toy-workflow/.saaa/fixture-mutation.txt`
- Create: `fixtures/toy-workflow/README.md`

**Interfaces:**
- Produces: a target folder satisfying the contract in the design document.

- [ ] **Step 1: Create the workflow being evolved**

`fixtures/toy-workflow/workflow.txt`:

```text
publish-policy: allow
draft-check: skip
```

- [ ] **Step 2: Create the check**

`fixtures/toy-workflow/check.sh`:

```bash
#!/usr/bin/env bash
# Behaviour case: the publish policy must not allow publishing without a draft check.
set -euo pipefail

workflow="$(dirname "$0")/workflow.txt"

if grep -q '^draft-check: skip$' "$workflow"; then
  echo "publish guard is disabled: draft-check is skipped"
  exit 1
fi

echo "publish guard holds: draft-check is enforced"
```

Make it executable and commit the bit:

```bash
chmod +x fixtures/toy-workflow/check.sh
git update-index --chmod=+x fixtures/toy-workflow/check.sh
```

- [ ] **Step 3: Create the canned mutation that fixes the check**

`fixtures/toy-workflow/.saaa/fixture-mutation.txt`:

```text
enforce the draft check before publishing
publish-policy: allow
draft-check: enforce
```

Baseline fails the check; the realized candidate passes it. That is what makes the demo show a real transition rather than a constant.

- [ ] **Step 4: Document the fixture**

`fixtures/toy-workflow/README.md`:

```markdown
# Toy Workflow Fixture

A minimal target folder for `saaa evolve`, used by the acceptance test and by
anyone wanting to see one generation run without model credentials.

| File | Role |
|---|---|
| `workflow.txt` | the artifact being evolved |
| `check.sh` | a behaviour case; fails on the baseline, passes on the candidate |
| `.saaa/fixture-mutation.txt` | the canned mutation, summary on line one |
| `journal.md` | written by a run; not committed |

The baseline deliberately fails `check.sh`. The fixture mutation fixes it, so a
run demonstrates a real DISCARD-to-PROMOTE transition rather than scoring a
constant.
```

- [ ] **Step 5: Ignore generated journals**

Append to `.gitignore`:

```text
fixtures/**/journal.md
```

- [ ] **Step 6: Verify the check behaves as intended**

```bash
./fixtures/toy-workflow/check.sh; echo "baseline exit: $?"
```

Expected: prints the guard-disabled message and `baseline exit: 1`.

- [ ] **Step 7: Commit**

```bash
git add fixtures/toy-workflow .gitignore
git commit -m "Add toy-workflow fixture whose baseline fails its own check

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 9: The evolve command

**Files:**
- Create: `modules/cli/src/main/java/com/dreamthought/saaa/cli/EvolveCommand.java`
- Create: `modules/cli/src/main/java/com/dreamthought/saaa/cli/ConsoleReporter.java`
- Create: `modules/cli/src/main/java/com/dreamthought/saaa/cli/CompositeReporter.java`
- Modify: `modules/cli/src/main/java/com/dreamthought/saaa/cli/MutationLoopCli.java`
- Modify: `modules/cli/build.gradle.kts` (add an `acceptanceTest` source set)
- Modify: `.agentic-template/bin/project` (run the new acceptance task)
- Test: `modules/cli/src/acceptanceTest/java/com/dreamthought/saaa/cli/EvolveCommandAcceptanceTest.java`

**Interfaces:**
- Consumes: everything from Tasks 1 through 8.
- Produces: `saaa evolve <folder> [--profile <name>] [--workflow-file <name>] [--behaviour-case <name>]... [--max-lines <n>]`.

- [ ] **Step 1: Write the failing acceptance test**

```java
package com.dreamthought.saaa.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

final class EvolveCommandAcceptanceTest {
    @Test
    void runsOneGenerationWithFixtureProfileAndReportsADecision(@TempDir Path tempDir) throws Exception {
        Path repo = tempDir.resolve("repo");
        Path target = repo.resolve("toy");
        Files.createDirectories(target.resolve(".saaa"));

        Files.writeString(target.resolve("workflow.txt"), "draft-check: skip\n");
        Files.writeString(target.resolve(".saaa/fixture-mutation.txt"),
                "enforce the draft check\ndraft-check: enforce\n");
        Path check = target.resolve("check.sh");
        Files.writeString(check, """
                #!/usr/bin/env bash
                set -euo pipefail
                grep -q '^draft-check: enforce$' "$(dirname "$0")/workflow.txt"
                """);
        check.toFile().setExecutable(true);

        git(repo, "init", "--initial-branch=main");
        git(repo, "config", "user.name", "Test");
        git(repo, "config", "user.email", "test@example.invalid");
        git(repo, "add", "-A");
        git(repo, "commit", "-m", "baseline");

        int exitCode = new CommandLine(new MutationLoopCli()).execute(
                "evolve", target.toString(),
                "--profile", "fixture",
                "--behaviour-case", "workflow-check",
                "--max-lines", "80");

        assertThat(exitCode).isZero();
        assertThat(Files.readString(target.resolve("journal.md")))
                .contains("enforce the draft check")
                .contains("PROMOTE");
    }

    private static void git(Path dir, String... args) throws Exception {
        var command = new java.util.ArrayList<String>();
        command.add("git");
        command.addAll(java.util.List.of(args));
        Files.createDirectories(dir);
        var process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        if (process.waitFor() != 0) {
            throw new IllegalStateException("git failed: " + String.join(" ", args) + "\n" + output);
        }
    }
}
```

- [ ] **Step 2: Add the acceptanceTest source set to the CLI module**

Append to `modules/cli/build.gradle.kts`:

```kotlin
val acceptanceTest by sourceSets.creating {
    java.srcDir("src/acceptanceTest/java")
    compileClasspath += sourceSets.main.get().output + configurations.testRuntimeClasspath.get()
    runtimeClasspath += output + compileClasspath
}

configurations[acceptanceTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[acceptanceTest.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

tasks.register<Test>("acceptanceTest") {
    description = "Runs outside-in acceptance tests for CLI commands."
    group = "verification"
    testClassesDirs = acceptanceTest.output.classesDirs
    classpath = acceptanceTest.runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter(tasks.named("test"))
}
```

In `.agentic-template/bin/project`, change the `component-test` entry to run both:

```python
    "component-test": [[str(BIN / "gradle-command"), ":deterministic:acceptanceTest", ":cli:acceptanceTest"]],
```

- [ ] **Step 3: Run test to verify it fails**

Run: `.agentic-template/bin/project component-test`
Expected: FAIL — no `evolve` subcommand.

- [ ] **Step 4: Write the console reporter**

```java
package com.dreamthought.saaa.cli;

import com.dreamthought.saaa.deterministic.EvolutionReporter;
import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.FitnessResult;
import com.dreamthought.saaa.domain.Mutation;
import java.io.PrintWriter;
import java.util.Objects;

/** Prints one line per stage. Printing belongs here, never in the deterministic layer. */
public final class ConsoleReporter implements EvolutionReporter {
    private final PrintWriter out;

    public ConsoleReporter(PrintWriter out) {
        this.out = Objects.requireNonNull(out, "out");
    }

    @Override
    public void proposed(Mutation mutation) {
        out.printf("  propose    %s  %s%n", mutation.id(), mutation.summary());
    }

    @Override
    public void candidateCreated(Candidate candidate) {
        out.printf("  candidate  %s  %s%n", candidate.id(), candidate.commitSha());
    }

    @Override
    public void evidenceCollected(EvaluationEvidence evidence) {
        evidence.checks().forEach(check ->
                out.printf("  check      %-24s %s%n", check.name(), check.status()));
    }

    @Override
    public void scored(FitnessResult result) {
        out.printf("  score      %.2f%n", result.aggregateScore());
        out.printf("  %s%n", result.decision());
        out.flush();
    }
}
```

- [ ] **Step 5: Write the composite reporter**

```java
package com.dreamthought.saaa.cli;

import com.dreamthought.saaa.deterministic.EvolutionReporter;
import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.FitnessResult;
import com.dreamthought.saaa.domain.Mutation;
import java.util.List;
import java.util.Objects;

/** Fans one run's events out to several reporters. */
public final class CompositeReporter implements EvolutionReporter {
    private final List<EvolutionReporter> reporters;

    public CompositeReporter(List<EvolutionReporter> reporters) {
        this.reporters = List.copyOf(Objects.requireNonNull(reporters, "reporters"));
    }

    @Override
    public void proposed(Mutation mutation) {
        reporters.forEach(reporter -> reporter.proposed(mutation));
    }

    @Override
    public void candidateCreated(Candidate candidate) {
        reporters.forEach(reporter -> reporter.candidateCreated(candidate));
    }

    @Override
    public void evidenceCollected(EvaluationEvidence evidence) {
        reporters.forEach(reporter -> reporter.evidenceCollected(evidence));
    }

    @Override
    public void scored(FitnessResult result) {
        reporters.forEach(reporter -> reporter.scored(result));
    }
}
```

- [ ] **Step 6: Write the evolve command**

Create `modules/cli/src/main/java/com/dreamthought/saaa/cli/EvolveCommand.java`:

```java
package com.dreamthought.saaa.cli;

import com.dreamthought.saaa.adapters.checks.CommandCheckRunner;
import com.dreamthought.saaa.adapters.git.GitCandidateWorkspace;
import com.dreamthought.saaa.adapters.git.GitRealizationInspector;
import com.dreamthought.saaa.adapters.files.TextMutationRealizer;
import com.dreamthought.saaa.adapters.journal.JournalDecisionSink;
import com.dreamthought.saaa.adapters.journal.JournalReporter;
import com.dreamthought.saaa.deterministic.BoundedMutationValidator;
import com.dreamthought.saaa.deterministic.EvolutionReporter;
import com.dreamthought.saaa.deterministic.ExperimentMetadataStore;
import com.dreamthought.saaa.deterministic.MutationEvaluationLoop;
import com.dreamthought.saaa.deterministic.PhenotypeBridgeScorer;
import com.dreamthought.saaa.deterministic.ScoringConfig;
import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.FitnessResult;
import com.dreamthought.saaa.domain.WorkflowGraph;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

@Command(
        name = "evolve",
        description = "Run one mutation evaluation against a target folder."
)
public final class EvolveCommand implements Callable<Integer> {
    @Parameters(index = "0", description = "Folder containing the workflow being evolved.")
    private Path targetFolder;

    @Option(names = "--profile", defaultValue = "fixture", description = "Proposer profile name.")
    private String profile;

    @Option(names = "--workflow-file", defaultValue = "workflow.txt",
            description = "File inside the target folder that is being evolved.")
    private String workflowFile;

    @Option(names = "--behaviour-case", required = true,
            description = "Name of a check that is required behaviour and hard-gates promotion.")
    private List<String> behaviourCases;

    @Option(names = "--max-lines", defaultValue = "80",
            description = "Change budget that parsimony is scored against.")
    private int maxLines;

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        Path folder = targetFolder.toAbsolutePath().normalize();
        Path gitRoot = findGitRoot(folder);
        Path workflowPath = folder.resolve(workflowFile);
        if (!Files.isRegularFile(workflowPath)) {
            throw new IllegalArgumentException("workflow file not found: " + workflowPath);
        }

        String relativeWorkflow = gitRoot.relativize(workflowPath).toString();
        var baseline = new WorkflowGraph(folder.getFileName().toString(), "baseline", readString(workflowPath));

        var reporter = new CompositeReporter(List.of(
                new ConsoleReporter(out),
                new JournalReporter(folder.resolve("journal.md"), Clock.systemUTC())));

        var loop = new MutationEvaluationLoop(
                new ProposerProfileRegistry().resolve(profile, folder),
                new BoundedMutationValidator(),
                new GitCandidateWorkspace(
                        gitRoot, gitRoot.resolve(".worktrees"), new TextMutationRealizer(relativeWorkflow)),
                new CommandCheckRunner(List.of(new CommandCheckRunner.CommandCheck(
                        behaviourCases.get(0),
                        List.of(folder.resolve("check.sh").toString()),
                        Duration.ofMinutes(1)))),
                candidate -> List.of(),
                new PhenotypeBridgeScorer(
                        new GitRealizationInspector(),
                        new ScoringConfig(Set.copyOf(behaviourCases), maxLines, Map.of())),
                new NoOpMetadataStore(),
                new JournalDecisionSink(),
                reporter,
                Clock.systemUTC());

        loop.evaluate(baseline);
        out.printf("  journal    %s%n", folder.resolve("journal.md"));
        out.flush();
        return 0;
    }

    private static Path findGitRoot(Path folder) {
        for (Path current = folder; current != null; current = current.getParent()) {
            if (Files.exists(current.resolve(".git"))) {
                return current;
            }
        }
        throw new IllegalArgumentException("target folder is not inside a Git repository: " + folder);
    }

    private static String readString(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to read " + path, exception);
        }
    }

    /**
     * Persistent experiment metadata arrives with CHG-002 task T5. Keeping this a no-op avoids
     * pulling SQLite into the CLI before promotion semantics exist.
     */
    private static final class NoOpMetadataStore implements ExperimentMetadataStore {
        @Override
        public void recordCandidate(Candidate candidate) {
            // no persistent store in this slice
        }

        @Override
        public void recordFitness(FitnessResult result) {
            // no persistent store in this slice
        }
    }
}
```

The `check.sh` command runs with the *coordination checkout's* path but with the candidate worktree as its working directory, because `CommandCheckRunner` sets `directory(candidate.worktreePath())`. Change the command to `folder`-relative inside the worktree so the check reads the realized file:

```java
                        List.of(gitRoot.relativize(folder).resolve("check.sh").toString()),
```

Use that form, not the absolute one, so the check executes against the candidate rather than the working tree.

Then register the subcommand. In `MutationLoopCli`, change the annotation to:

```java
@Command(
        name = "saaa",
        mixinStandardHelpOptions = true,
        version = "self-adapting-agentic-architecture 0.1.0-SNAPSHOT",
        description = "Experimental workflow mutation and fitness evaluation CLI.",
        subcommands = EvolveCommand.class
)
```

Keep its existing `call()` body unchanged so the bare command still prints its status line.

- [ ] **Step 7: Run everything**

Run:
```bash
.agentic-template/bin/project test
.agentic-template/bin/project component-test
.agentic-template/bin/project integration-test
.agentic-template/bin/project lint
.agentic-template/bin/project check
.agentic-template/bin/project ready
```
Expected: all pass, `READY: PASS`.

- [ ] **Step 8: Run it for real**

```bash
.agentic-template/bin/gradle-command :cli:installDist
./modules/cli/build/install/cli/bin/cli evolve fixtures/toy-workflow \
    --behaviour-case workflow-check --max-lines 80
cat fixtures/toy-workflow/journal.md
```

Expected: stage lines, a score, `PROMOTE`, and a journal entry. Then clean up the worktree it created:

```bash
git worktree list
git worktree remove .worktrees/<created-worktree>
git branch -D candidate/<created-branch>
```

- [ ] **Step 9: Commit**

```bash
git add modules/cli .agentic-template/bin/project
git commit -m "Add the evolve command wiring the loop end to end

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 10: Update spec, handoff and docs

**Files:**
- Modify: `specs/changes/CHG-003-first-vertical-slice/change.toon`
- Modify: `HANDOFF.toon`
- Modify: `README.md`
- Modify: `docs/wiki/development.md`

- [ ] **Step 1: Mark the tasks complete**

Set every `CHG-003` task status to `completed` and the change `status` to `delivered`.

- [ ] **Step 2: Record the structured-data deviation**

Add to `docs/structured-data.md` that `.saaa/fixture-mutation.txt` is a plain-text deviation scoped to the fixture proposer, retiring when the TOON envelope reader lands under CHG-002 task T3d.

- [ ] **Step 3: Document the command**

Add the `evolve` command to the README command table and a short usage section showing the fixture run.

- [ ] **Step 4: Update the handoff**

Record objective, phase, completed work, next actions, files changed, tests run with results, branch and commit, and the fitness-function delta (none — no boundary rule changed). Record that RISK-002 is unchanged and that `behavioral_safety` remains inert.

- [ ] **Step 5: Validate and commit**

```bash
.agentic-template/bin/project check
.agentic-template/bin/project check-wiki
.agentic-template/bin/project ready
git add -A
git commit -m "Record CHG-003 delivery

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Self-review notes

**Spec coverage.** S1 is Tasks 1 and 2. S2 is Task 9. S3 is Task 4. S4 is Task 5. S5 is Task 7. S6 is Task 6. Task 3 supports S3's parsimony requirement, Task 8 supplies the fixture S2 runs against, Task 10 satisfies the documentation-update trigger.

**Known gaps carried deliberately.** `behavioral_safety` is hardcoded 1.0, so one objective is inert. RISK-002 is untouched: the scorer still never sees the contract. `ExperimentMetadataStore` is a no-op in the CLI wiring. All three are recorded in the spec's risks and in Task 10's handoff step.

**Sequencing.** Tasks 1 to 8 are independently testable and can be reviewed separately. Task 9 depends on all of them. Do not start Task 9 before Task 8, because its acceptance test needs a fixture whose baseline fails its own check.
