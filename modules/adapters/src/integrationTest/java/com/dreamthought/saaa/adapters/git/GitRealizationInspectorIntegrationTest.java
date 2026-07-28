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
