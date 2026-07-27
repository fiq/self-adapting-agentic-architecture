package com.dreamthought.saaa.adapters.git;

import static com.dreamthought.saaa.domain.MutationScope.WORKFLOW_DEFINITION;
import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.domain.Mutation;
import com.dreamthought.saaa.domain.WorkflowGraph;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
