package com.dreamthought.saaa.adapters.git;

import static com.dreamthought.saaa.domain.MutationScope.WORKFLOW_DEFINITION;
import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.adapters.files.TextMutationRealizer;
import com.dreamthought.saaa.deterministic.CandidateNamespace;
import com.dreamthought.saaa.domain.Mutation;
import com.dreamthought.saaa.domain.ProposerEvidence;
import com.dreamthought.saaa.domain.WorkflowGraph;
import java.io.IOException;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class GitCandidateWorkspaceIntegrationTest {
    @TempDir
    private Path tempDir;

    @Test
    void createsCommittedCandidateInIsolatedWorktreeWithoutTouchingCoordinationCheckout() throws IOException {
        Path repository = initializedRepository();
        Path worktrees = tempDir.resolve("worktrees");
        var baseline = new WorkflowGraph("baseline", "v1", "agent -> tool -> answer");
        var mutation = new Mutation("mut-001", "tighten tool selection", WORKFLOW_DEFINITION, "replace tool policy");

        var workspace = new GitCandidateWorkspace(repository, worktrees);

        var candidate = workspace.createCommittedCandidate(baseline, mutation);

        assertThat(candidate.id()).isEqualTo("candidate-mut-001");
        assertThat(candidate.mutationId()).isEqualTo(mutation.id());
        assertThat(candidate.branchName()).isEqualTo("candidate/baseline-mut-001");
        assertThat(candidate.worktreePath()).isEqualTo(worktrees.resolve("candidate-baseline-mut-001").toAbsolutePath().normalize());
        assertThat(candidate.worktreePath()).isDirectory();
        assertThat(candidate.commitSha()).matches("[0-9a-f]{40}");
        assertThat(repository.resolve(".saaa")).doesNotExist();
        assertThat(runOutput(repository, "status", "--short")).isEmpty();

        Path candidateFile = candidate.worktreePath().resolve(".saaa/candidates/candidate-mut-001.toon");
        assertThat(candidateFile).isRegularFile();
        assertThat(Files.readString(candidateFile, StandardCharsets.UTF_8))
                .contains(
                        "id: candidate-mut-001",
                        "workflow_id: baseline",
                        "mutation_id: mut-001",
                        "patch: |",
                        "    replace tool policy"
                );
        assertThat(runOutput(candidate.worktreePath(), "rev-parse", "HEAD")).isEqualTo(candidate.commitSha());
        assertThat(runOutput(candidate.worktreePath(), "log", "-1", "--pretty=%s"))
                .isEqualTo("Create candidate candidate-mut-001");
    }

    @Test
    @DisplayName("each candidate in a generation gets its own worktree, branch and id")
    void eachCandidateInAGenerationGetsItsOwnWorktree() throws IOException {
        Path repository = initRepositoryWithWorkflowFile();
        Path worktrees = tempDir.resolve("worktrees");
        var namespace = CandidateNamespace.forRun(Instant.parse("2026-09-05T14:32:01.123Z"));
        var baseline = new WorkflowGraph("toy", "v1", "old content");
        // The same mutation twice, which is what the default fixture proposer actually produces and
        // is therefore the case that must work. Before RISK-003 was closed the second call failed
        // outright, because nothing in the name distinguished one evaluation from another.
        var mutation = new Mutation("MUT-1", "tighten guard", WORKFLOW_DEFINITION, "new content");

        var first = workspaceFor(repository, worktrees, namespace.forCandidate(1))
                .createCommittedCandidate(baseline, mutation);
        var second = workspaceFor(repository, worktrees, namespace.forCandidate(2))
                .createCommittedCandidate(baseline, mutation);

        assertThat(second.worktreePath()).isNotEqualTo(first.worktreePath());
        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(second.branchName()).isNotEqualTo(first.branchName());
        assertThat(first.worktreePath()).exists();
        assertThat(second.worktreePath())
                .as("both candidates are live at once, which is what a generation needs")
                .exists();
        assertThat(runOutput(first.worktreePath(), "rev-parse", "HEAD")).isEqualTo(first.commitSha());
        assertThat(runOutput(second.worktreePath(), "rev-parse", "HEAD")).isEqualTo(second.commitSha());
    }

    private static GitCandidateWorkspace workspaceFor(Path repository, Path worktrees, String namespace) {
        return new GitCandidateWorkspace(
                repository,
                worktrees,
                new TextMutationRealizer("workflow.txt"),
                Optional::empty,
                Optional.of(namespace));
    }

    @Test
    void realizesMutationIntoCandidateCommit() throws IOException {
        Path repository = initRepositoryWithWorkflowFile();
        Path worktrees = tempDir.resolve("worktrees");

        var workspace = new GitCandidateWorkspace(
                repository,
                worktrees,
                new TextMutationRealizer("workflow.txt"));

        var candidate = workspace.createCommittedCandidate(
                new WorkflowGraph("toy", "v1", "old content"),
                new Mutation("MUT-1", "tighten guard", WORKFLOW_DEFINITION, "new content"));

        assertThat(candidate.worktreePath().resolve("workflow.txt")).hasContent("new content");

        String committed = runOutput(candidate.worktreePath(), "show", candidate.commitSha() + ":workflow.txt");
        assertThat(committed).isEqualTo("new content");
    }

    @Test
    void writesProposerEvidenceIntoCandidateBookkeeping() throws IOException {
        Path repository = initRepositoryWithWorkflowFile();
        Path worktrees = tempDir.resolve("worktrees");
        var attributes = new LinkedHashMap<String, String>();
        attributes.put("prompt_digest", "sha256:abc123");
        attributes.put("raw_response", "{\"id\":\"MUT-1\"}");

        var workspace = new GitCandidateWorkspace(
                repository,
                worktrees,
                new TextMutationRealizer("workflow.txt"),
                () -> Optional.of(ProposerEvidence.of("openai-compatible", attributes)));

        var candidate = workspace.createCommittedCandidate(
                new WorkflowGraph("toy", "v1", "old content"),
                new Mutation("MUT-1", "tighten guard", WORKFLOW_DEFINITION, "new content"));

        assertThat(Files.readString(candidate.worktreePath().resolve(".saaa/candidates/candidate-mut-1.toon")))
                .contains(
                        "proposer:",
                        "id: openai-compatible",
                        "prompt_digest: |",
                        "    sha256:abc123",
                        "raw_response: |",
                        "    {\"id\":\"MUT-1\"}");
    }

    @Test
    void scrubsAndCapsProposerEvidenceBeforeItIsCommitted() throws IOException {
        Path repository = initRepositoryWithWorkflowFile();
        Path worktrees = tempDir.resolve("worktrees");
        var attributes = new LinkedHashMap<String, String>();
        attributes.put("prompt", "prompt " + "x".repeat(ProposerEvidenceSanitizer.VALUE_LIMIT + 200));
        attributes.put(
                "raw_response",
                "Authorization: Bearer sk-super-secret-value echoed sk-super-secret-value");

        var workspace = new GitCandidateWorkspace(
                repository,
                worktrees,
                new TextMutationRealizer("workflow.txt"),
                () -> Optional.of(ProposerEvidence.of("openai-compatible", attributes)),
                new ProposerEvidenceSanitizer(() -> Optional.of("sk-super-secret-value")));

        var candidate = workspace.createCommittedCandidate(
                new WorkflowGraph("toy", "v1", "old content"),
                new Mutation("MUT-1", "tighten guard", WORKFLOW_DEFINITION, "new content"));

        String candidateToon = Files.readString(candidate.worktreePath().resolve(".saaa/candidates/candidate-mut-1.toon"));
        assertThat(candidateToon)
                .doesNotContain("sk-super-secret-value")
                .contains("Authorization: Bearer <redacted>")
                .contains("<redacted>");
        assertThat(candidateToon)
                .doesNotContain("x".repeat(ProposerEvidenceSanitizer.VALUE_LIMIT + 1));
        assertThat(runOutput(candidate.worktreePath(), "show", "HEAD:.saaa/candidates/candidate-mut-1.toon"))
                .doesNotContain("sk-super-secret-value");
    }

    private Path initRepositoryWithWorkflowFile() throws IOException {
        Path repository = tempDir.resolve("repo");
        Files.createDirectories(repository);
        run(repository, "init", "--initial-branch=main");
        Files.writeString(repository.resolve("workflow.txt"), "old content", StandardCharsets.UTF_8);
        run(repository, "add", "workflow.txt");
        run(repository, "-c", "user.name=Test User", "-c", "user.email=test@example.invalid", "commit", "-m", "baseline");
        return repository;
    }

    private Path initializedRepository() throws IOException {
        Path repository = tempDir.resolve("repo");
        Files.createDirectories(repository);
        run(repository, "init", "--initial-branch=main");
        Files.writeString(repository.resolve("README.md"), "baseline\n", StandardCharsets.UTF_8);
        run(repository, "add", "README.md");
        run(repository, "-c", "user.name=Test User", "-c", "user.email=test@example.invalid", "commit", "-m", "baseline");
        return repository;
    }

    private static void run(Path directory, String... arguments) {
        var result = git(directory, arguments);
        assertThat(result.exitCode())
                .as("git %s%n%s", String.join(" ", arguments), result.output())
                .isZero();
    }

    private static String runOutput(Path directory, String... arguments) {
        var result = git(directory, arguments);
        assertThat(result.exitCode())
                .as("git %s%n%s", String.join(" ", arguments), result.output())
                .isZero();
        return result.output().trim();
    }

    private static ProcessResult git(Path directory, String... arguments) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(directory.toString());
        command.addAll(List.of(arguments));
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return new ProcessResult(process.waitFor(), output);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to run git", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while running git", exception);
        }
    }

    private record ProcessResult(int exitCode, String output) {
    }
}
