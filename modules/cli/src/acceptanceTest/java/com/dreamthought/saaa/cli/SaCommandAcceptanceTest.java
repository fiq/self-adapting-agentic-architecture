package com.dreamthought.saaa.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.adapters.evolve.EvolveRunner;
import com.dreamthought.saaa.adapters.evolve.ProposerProfileRegistry;
import com.dreamthought.saaa.adapters.fixture.FixtureMutationProposer;
import com.dreamthought.saaa.deterministic.AgentHarness;
import com.dreamthought.saaa.deterministic.MutationProposer;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

final class SaCommandAcceptanceTest {
    @Test
    void startsAnInspectableSessionWithoutInvokingAnAgent() {
        var resolvedRoutes = new ArrayList<String>();

        var transcript = run("status\ncapabilities\nskills\nquit\n", resolvedRoutes);

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
        assertThat(resolvedRoutes)
                .as("inspecting status, capabilities and skills must not resolve an agent-backed proposer")
                .isEmpty();
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
    void passesTheExplicitlySelectedRouteRatherThanTheSessionDefaultToTheLoop(@TempDir Path tempDir)
            throws Exception {
        Path repo = tempDir.resolve("repo");
        Path target = repo.resolve("workflow");
        writeFixture(target);
        writeCheck(target, "workflow-check", """
                #!/usr/bin/env bash
                set -euo pipefail
                grep -q '^draft-check: enforce$' "$(dirname "$0")/workflow.txt"
                """);
        initRepo(repo);
        var resolvedRoutes = new ArrayList<String>();

        // `fixture` is the session default (first registered profile), so selecting it could not
        // distinguish a passed-through route from an ignored one. `openai-compatible` can.
        var transcript = run("target HARNESS_WORKFLOW " + target + "\n"
                + "route openai-compatible\n"
                + "evolve workflow.txt workflow-check\n"
                + "quit\n", resolvedRoutes);

        assertThat(resolvedRoutes)
                .as("the route selected in the session must be the profile the loop resolves")
                .containsExactly("openai-compatible");
        assertThat(transcript).contains("PROMOTE").contains("session decision PROMOTE");
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
        return run(input, new EvolveRunner(), new ProposerProfileRegistry().knownNames());
    }

    /**
     * Runs the session with the agent-backed profiles replaced by recorders, so a test can assert
     * which route the loop actually resolved rather than trusting the printed selection.
     */
    private static String run(String input, List<String> resolvedRoutes) {
        Function<Path, MutationProposer> openAiCompatible = folder -> {
            resolvedRoutes.add("openai-compatible");
            return new FixtureMutationProposer(folder.resolve(".saaa/fixture-mutation.txt"));
        };
        Function<Path, AgentHarness> acp = folder -> {
            resolvedRoutes.add("acp");
            throw new UnsupportedOperationException("no ACP subprocess is started in this acceptance test");
        };
        var registry = new ProposerProfileRegistry(openAiCompatible, acp);
        return run(input, new EvolveRunner(registry, Clock.systemUTC()), registry.knownNames());
    }

    private static String run(String input, EvolveRunner evolveRunner, List<String> routes) {
        var output = new StringWriter();
        int exitCode = new CommandLine(new SaCommand(
                new BufferedReader(new StringReader(input)), new PrintWriter(output, true), evolveRunner, routes))
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
