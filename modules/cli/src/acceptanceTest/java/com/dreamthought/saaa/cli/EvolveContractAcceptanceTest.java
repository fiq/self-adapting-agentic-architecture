package com.dreamthought.saaa.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/**
 * CHG-019 S1 to S5. RISK-002 said a contract could declare evidence and a candidate could promote
 * without producing it. These drive the real CLI to show that it no longer can.
 */
final class EvolveContractAcceptanceTest {
    @Test
    void promotesWhenEveryDeclaredEvidenceCheckPasses(@TempDir Path tempDir) throws Exception {
        Path target = repositoryDeclaring(tempDir, true, true);

        assertThat(run(target, "--operator", "simplify"))
                .as("a candidate producing every declared check promotes")
                .contains("PROMOTE");
    }

    @Test
    void discardsWhenADeclaredEvidenceCheckIsAbsent(@TempDir Path tempDir) throws Exception {
        // behavior_cases_unchanged is declared by the simplify operator but never produced here.
        Path target = repositoryDeclaring(tempDir, true, false);

        String transcript = runDeclaring(
                target, java.util.List.of("unit_tests_pass"), "--operator", "simplify");

        assertThat(transcript)
                .as("absent evidence is not passing evidence, so the declared gate discards")
                .contains("DISCARD");
        assertThat(Files.readString(target.resolve("journal.md")))
                .as("the journal records the discard, so the outcome is auditable")
                .contains("DISCARD");
    }

    @Test
    void discardsWhenADeclaredEvidenceCheckFails(@TempDir Path tempDir) throws Exception {
        Path target = repositoryDeclaring(tempDir, false, true);

        assertThat(run(target, "--operator", "simplify"))
                .as("a declared check that fails discards the candidate")
                .contains("DISCARD");
    }

    @Test
    void aRunWithoutAContractIsUnchanged(@TempDir Path tempDir) throws Exception {
        // The same repository that discards under a contract promotes without one, because nothing
        // declared the missing evidence. That is the behaviour every existing caller keeps.
        Path target = repositoryDeclaring(tempDir, true, false);

        assertThat(runDeclaring(target, java.util.List.of("unit_tests_pass")))
                .contains("PROMOTE");
    }

    @Test
    void refusesAContractTheValidatorRejects(@TempDir Path tempDir) throws Exception {
        Path target = repositoryDeclaring(tempDir, true, true);

        int exitCode = new CommandLine(new MutationLoopCli()).execute(
                "saaa-evolve", target.toString(), "--behaviour-case", "unit_tests_pass",
                "--operator", "rewrite-everything");

        assertThat(exitCode).isNotZero();
        assertThat(Files.exists(target.resolve(".saaa/candidates")))
                .as("an invalid contract stops the run before any candidate is created")
                .isFalse();
    }

    /**
     * CHG-021. A failing safety probe lowers the behavioural-safety objective and must not discard,
     * because probes grade while declared required evidence gates. Driven through the real CLI so
     * the distinction holds end to end rather than only in the scorer.
     */
    @Test
    void aFailingSafetyProbeLowersTheScoreWithoutDiscarding(@TempDir Path tempDir) throws Exception {
        Path repo = tempDir.resolve("repo");
        Path target = repo.resolve("toy");
        Files.createDirectories(target.resolve(".saaa"));
        Files.writeString(target.resolve("workflow.txt"), "draft-check: skip\n");
        Files.writeString(target.resolve(".saaa/fixture-mutation.txt"),
                "enforce the draft check\ndraft-check: enforce\n");
        writeCheck(target, "unit_tests_pass", true);
        writeCheck(target, "no_network_call", false);
        git(repo, "init", "--initial-branch=main");
        git(repo, "config", "user.name", "Test");
        git(repo, "config", "user.email", "test@example.invalid");
        git(repo, "add", "-A");
        git(repo, "commit", "-m", "baseline");

        String transcript = runDeclaring(target, java.util.List.of("unit_tests_pass"),
                "--safety-probe", "no_network_call");

        // Asserting PROMOTE alone would stay green if the probe never ran at all: an absent probe
        // scores safety 0.0 exactly as a failing one does, and still clears the threshold. Only the
        // evidence line separates the two, so that is what this asserts.
        // Matched as a line rather than by fixed padding: the console pads check names to a column
        // width, and asserting that spacing would make a harmless formatting change fail here.
        assertThat(transcript.lines())
                .as("the probe ran, failed, and is visible in the evidence rather than filtered out")
                .anySatisfy(line -> assertThat(line)
                        .contains("no_network_call")
                        .contains("FAILED"));
        assertThat(transcript)
                .as("a failing probe grades; only declared required evidence gates")
                .contains("PROMOTE");
    }

    private static String run(Path target, String... contractFlags) {
        return runDeclaring(target, java.util.List.of("unit_tests_pass", "behavior_cases_unchanged"),
                contractFlags);
    }

    private static String runDeclaring(
            Path target, java.util.List<String> behaviourCases, String... contractFlags) {
        var out = new java.io.StringWriter();
        var command = new CommandLine(new MutationLoopCli());
        command.setOut(new java.io.PrintWriter(out, true));
        var arguments = new java.util.ArrayList<>(java.util.List.of("saaa-evolve", target.toString()));
        behaviourCases.forEach(name -> {
            arguments.add("--behaviour-case");
            arguments.add(name);
        });
        arguments.addAll(java.util.List.of(contractFlags));
        command.execute(arguments.toArray(String[]::new));
        return out.toString();
    }

    /** A fixture repository whose two declared checks pass or fail as asked. */
    private static Path repositoryDeclaring(
            Path tempDir, boolean unitTestsPass, boolean behaviourCasesUnchanged) throws Exception {
        Path repo = tempDir.resolve("repo");
        Path target = repo.resolve("toy");
        Files.createDirectories(target.resolve(".saaa"));
        Files.writeString(target.resolve("workflow.txt"), "draft-check: skip\n");
        Files.writeString(target.resolve(".saaa/fixture-mutation.txt"),
                "enforce the draft check\ndraft-check: enforce\n");
        writeCheck(target, "unit_tests_pass", unitTestsPass);
        if (behaviourCasesUnchanged) {
            writeCheck(target, "behavior_cases_unchanged", true);
        }
        git(repo, "init", "--initial-branch=main");
        git(repo, "config", "user.name", "Test");
        git(repo, "config", "user.email", "test@example.invalid");
        git(repo, "add", "-A");
        git(repo, "commit", "-m", "baseline");
        return target;
    }

    private static void writeCheck(Path target, String name, boolean passes) throws Exception {
        Path check = target.resolve(name + ".sh");
        Files.writeString(check, "#!/usr/bin/env bash\nexit " + (passes ? "0" : "1") + "\n");
        check.toFile().setExecutable(true);
    }

    private static void git(Path directory, String... arguments) throws Exception {
        var command = new java.util.ArrayList<String>();
        command.add("git");
        command.addAll(java.util.List.of(arguments));
        Files.createDirectories(directory);
        var process = new ProcessBuilder(command).directory(directory.toFile())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        if (process.waitFor() != 0) {
            throw new IllegalStateException("git failed: " + String.join(" ", arguments) + "\n" + output);
        }
    }
}
