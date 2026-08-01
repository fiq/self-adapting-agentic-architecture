package com.dreamthought.saaa.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.adapters.evolve.EvolveRunner;
import com.dreamthought.saaa.adapters.mcp.EvolveMcpServer;
import com.dreamthought.saaa.adapters.mcp.EvolveMcpTool;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class EvolveMcpAcceptanceTest {
    private static final Pattern COMMIT_SHA_FIELD = Pattern.compile("\"commitSha\":\"([^\"]+)\"");

    @Test
    void mcpResponseAndJournalEntryAgreeOnTheCandidateCommitSha(@TempDir Path tempDir) throws Exception {
        Path repo = tempDir.resolve("repo");
        Path target = repo.resolve("toy");
        writeFixture(target);
        writeCheck(target, "workflow-check", """
                #!/usr/bin/env bash
                set -euo pipefail
                grep -q '^draft-check: enforce$' "$(dirname "$0")/workflow.txt"
                """);
        initRepo(repo);

        var server = new EvolveMcpServer(new EvolveMcpTool(new EvolveRunner()));
        var response = server.callEvolve(Map.of(
                "targetFolder", target.toString(),
                "profile", "fixture",
                "behaviourCases", List.of("workflow-check"),
                "maxLines", 80));

        assertThat(response.error()).isFalse();
        String commitSha = commitSha(response.json());
        assertThat(Files.readString(target.resolve("journal.md")))
                .contains("| commit | " + commitSha + " |")
                .contains("PROMOTE");
    }

    private static String commitSha(String json) {
        var matcher = COMMIT_SHA_FIELD.matcher(json);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private static void writeFixture(Path target) throws Exception {
        Files.createDirectories(target.resolve(".saaa"));
        Files.writeString(target.resolve("workflow.txt"), "draft-check: skip\n");
        Files.writeString(target.resolve(".saaa/fixture-mutation.txt"),
                "enforce the draft check\ndraft-check: enforce\n");
    }

    private static void writeCheck(Path target, String caseName, String script) throws Exception {
        Path check = target.resolve(caseName + ".sh");
        Files.writeString(check, script);
        check.toFile().setExecutable(true);
    }

    private static void initRepo(Path repo) throws Exception {
        git(repo, "init", "--initial-branch=main");
        git(repo, "config", "user.name", "Test");
        git(repo, "config", "user.email", "test@example.invalid");
        git(repo, "add", "-A");
        git(repo, "commit", "-m", "baseline");
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
