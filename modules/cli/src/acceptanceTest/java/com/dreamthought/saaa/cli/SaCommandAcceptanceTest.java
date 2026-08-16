package com.dreamthought.saaa.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.adapters.evolve.EvolveRunner;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

final class SaCommandAcceptanceTest {
    @Test
    void startsAnInspectableSessionWithoutInvokingAnAgent() {
        var transcript = run("status\ncapabilities\nskills\nquit\n");

        assertThat(transcript)
                .contains("state ACTIVE")
                .contains("route fixture")
                .contains("evolve-harness-workflow")
                .contains("evolve-code")
                .contains("fixture")
                .contains("openai-compatible")
                .contains("acp")
                .contains("select-target")
                .contains("evolve-governed")
                .contains("state CLOSED");
    }

    @Test
    void recordsExplicitTargetAndRouteAndPreservesRouteAfterInvalidSelection(@TempDir Path tempDir) {
        var transcript = run("target HARNESS_WORKFLOW " + tempDir + "\n"
                + "route acp\nroute unknown\nstatus\nquit\n");

        assertThat(transcript)
                .contains("target HARNESS_WORKFLOW " + tempDir.toAbsolutePath())
                .contains("route acp")
                .contains("unknown route: unknown")
                .contains("state ACTIVE")
                .contains("route acp");
        assertThat(transcript.indexOf("unknown route: unknown"))
                .isLessThan(transcript.lastIndexOf("route acp"));
    }

    @Test
    void evolvesTheSelectedHarnessWorkflowThroughTheExistingLoop(@TempDir Path tempDir) throws Exception {
        Path repo = tempDir.resolve("repo");
        Path target = repo.resolve("workflow");
        writeFixture(target);
        writeCheck(target, "workflow-check", """
                #!/usr/bin/env bash
                set -euo pipefail
                grep -q '^draft-check: enforce$' "$(dirname "$0")/workflow.txt"
                """);
        initRepo(repo);

        var transcript = run("target HARNESS_WORKFLOW " + target + "\n"
                + "route fixture\n"
                + "evolve workflow.txt workflow-check\n"
                + "quit\n");

        assertThat(transcript).contains("PROMOTE").contains("session decision PROMOTE");
        assertThat(Files.readString(target.resolve("journal.md"))).contains("PROMOTE");
    }

    @Test
    void evolvesTheSelectedCodeTargetThroughTheExistingLoop(@TempDir Path tempDir) throws Exception {
        Path repo = tempDir.resolve("repo");
        Path target = repo.resolve("code");
        Files.createDirectories(target.resolve(".saaa"));
        Files.writeString(target.resolve("Example.java"), "class Example { String value() { return \"old\"; } }\n");
        Files.writeString(target.resolve(".saaa/fixture-mutation.txt"),
                "update the code value\nclass Example { String value() { return \"new\"; } }\n");
        writeCheck(target, "code-check", """
                #!/usr/bin/env bash
                set -euo pipefail
                grep -q 'return "new"' "$(dirname "$0")/Example.java"
                """);
        initRepo(repo);

        var transcript = run("target CODE " + target + "\nevolve Example.java code-check\nquit\n");

        assertThat(transcript).contains("target CODE " + target.toAbsolutePath())
                .contains("PROMOTE").contains("session decision PROMOTE");
        assertThat(Files.readString(target.resolve("journal.md"))).contains("PROMOTE");
    }

    private static String run(String input) {
        var output = new StringWriter();
        int exitCode = new CommandLine(new SaCommand(
                new BufferedReader(new StringReader(input)), new PrintWriter(output, true), new EvolveRunner()))
                .execute();
        assertThat(exitCode).isZero();
        return output.toString();
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

    private static void git(Path directory, String... arguments) throws Exception {
        var command = new java.util.ArrayList<String>();
        command.add("git");
        command.addAll(java.util.List.of(arguments));
        Files.createDirectories(directory);
        var process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        if (process.waitFor() != 0) {
            throw new IllegalStateException("git failed: " + String.join(" ", arguments) + "\n" + output);
        }
    }
}
