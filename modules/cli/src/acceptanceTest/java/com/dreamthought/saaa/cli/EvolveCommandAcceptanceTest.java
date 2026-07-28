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
