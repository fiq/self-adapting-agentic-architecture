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

        var candidate = new Candidate("cand-1", "MUT-1", "candidate/toy-MUT-1", repo, sha);
        var summary = new GitRealizationInspector().inspect(candidate);

        assertThat(summary.filesChanged()).isEqualTo(1);
        assertThat(summary.linesChanged()).isEqualTo(2);
        assertThat(new JGitChangedPathInspector().inspect(candidate)).containsExactly("workflow.txt");
    }

    @Test
    void ignoresBookkeepingFilesUnderSaaaDirectory(@TempDir Path tempDir) throws Exception {
        Path repo = tempDir.resolve("repo");
        Files.createDirectories(repo);
        git(repo, "init", "--initial-branch=main");
        git(repo, "config", "user.name", "Test");
        git(repo, "config", "user.email", "test@example.invalid");
        Files.writeString(repo.resolve("workflow.txt"), "line one\nline two\n");
        git(repo, "add", "-A");
        git(repo, "commit", "-m", "baseline");

        // A single commit that both realizes a mutation AND writes candidate bookkeeping, exactly
        // as GitCandidateWorkspace does: it commits .saaa/candidates/<id>.toon alongside the real
        // change in the same commit. Only the workflow.txt change should count toward linesChanged.
        Files.writeString(repo.resolve("workflow.txt"), "line one\nline changed\n");
        Files.createDirectories(repo.resolve(".saaa/candidates"));
        Files.writeString(repo.resolve(".saaa/candidates/cand-1.toon"),
                "candidate:\n  id: cand-1\n  mutation_id: MUT-1\n  branch_name: candidate/toy-MUT-1\n"
                        + "baseline:\n  workflow_id: toy\n  version: baseline\n  definition: |\n"
                        + "    line one\n    line two\n"
                        + "mutation:\n  id: MUT-1\n  scope: MODIFY\n  summary: |\n    a summary\n"
                        + "  patch: |\n    line one\n    line changed\n");
        git(repo, "add", "-A");
        git(repo, "commit", "-m", "candidate");
        String sha = git(repo, "rev-parse", "HEAD").trim();

        var candidate = new Candidate("cand-1", "MUT-1", "candidate/toy-MUT-1", repo, sha);
        var summary = new GitRealizationInspector().inspect(candidate);

        assertThat(summary.filesChanged()).isEqualTo(1);
        assertThat(summary.linesChanged()).isEqualTo(2);
        assertThat(new JGitChangedPathInspector().inspect(candidate)).containsExactly("workflow.txt");
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
