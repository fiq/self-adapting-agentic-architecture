package com.dreamthought.saaa.adapters.checks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.CheckStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CommandCheckRunnerIntegrationTest {
    @TempDir
    private Path tempDir;

    @Test
    void runsConfiguredCommandsInCandidateWorktreeAndRecordsExitStatus() throws Exception {
        Path worktree = tempDir.resolve("candidate");
        Files.createDirectories(worktree);
        Files.writeString(worktree.resolve("marker.txt"), "candidate-local file\n");
        var candidate = new Candidate(
                "candidate-mut-001",
                "mut-001",
                "candidate/baseline-mut-001",
                worktree,
                "0123456789abcdef0123456789abcdef01234567"
        );
        var runner = new CommandCheckRunner(List.of(
                new CommandCheckRunner.CommandCheck("read-marker", List.of("sh", "-c", "cat marker.txt")),
                new CommandCheckRunner.CommandCheck("failing-check", List.of("sh", "-c", "echo no >&2; exit 7")),
                new CommandCheckRunner.CommandCheck(
                        "verbose-check",
                        List.of("sh", "-c", "yes candidate-output | head -n 20000"),
                        Duration.ofSeconds(2)
                ),
                new CommandCheckRunner.CommandCheck(
                        "timeout-check",
                        List.of("sh", "-c", "sleep 1"),
                        Duration.ofMillis(50)
                )
        ));

        var evidence = runner.runChecks(candidate);

        assertThat(evidence).hasSize(4);
        assertThat(evidence.get(0).name()).isEqualTo("read-marker");
        assertThat(evidence.get(0).status()).isEqualTo(CheckStatus.PASSED);
        assertThat(evidence.get(0).summary()).contains("exit=0", "candidate-local file");
        assertThat(evidence.get(1).name()).isEqualTo("failing-check");
        assertThat(evidence.get(1).status()).isEqualTo(CheckStatus.FAILED);
        assertThat(evidence.get(1).summary()).contains("exit=7", "no");
        assertThat(evidence.get(2).name()).isEqualTo("verbose-check");
        assertThat(evidence.get(2).status()).isEqualTo(CheckStatus.PASSED);
        assertThat(evidence.get(2).summary()).contains("exit=0", "candidate-output", "...");
        assertThat(evidence.get(3).name()).isEqualTo("timeout-check");
        assertThat(evidence.get(3).status()).isEqualTo(CheckStatus.FAILED);
        assertThat(evidence.get(3).summary()).contains("timed out");
    }

    /**
     * A check command that is a path must name something inside the candidate. A tracked symlink
     * pointing out of the tree is recreated faithfully by {@code git worktree add}, so without this
     * guard a committed symlink lets a script that is not in the candidate satisfy a required
     * behaviour and the candidate promotes on evidence about the wrong file.
     */
    @Test
    void refusesToRunAPathCommandThatResolvesOutsideTheCandidate() throws Exception {
        Path worktree = tempDir.resolve("escaping");
        Files.createDirectories(worktree);
        Path outside = tempDir.resolve("outside.sh");
        Files.writeString(outside, "#!/usr/bin/env bash\nexit 0\n");
        outside.toFile().setExecutable(true);
        Files.createSymbolicLink(worktree.resolve("check.sh"), outside);
        var candidate = new Candidate(
                "candidate-mut-002", "mut-002", "candidate/baseline-mut-002", worktree,
                "0123456789abcdef0123456789abcdef01234567");
        var runner = new CommandCheckRunner(List.of(
                new CommandCheckRunner.CommandCheck("escaping-check", List.of("./check.sh"))));

        assertThatThrownBy(() -> runner.runChecks(candidate))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("escaping-check")
                .hasMessageContaining("outside the candidate");
    }

    @Test
    void runsAPathCommandThatStaysInsideTheCandidate() throws Exception {
        Path worktree = tempDir.resolve("contained");
        Files.createDirectories(worktree.resolve("toy"));
        Path script = worktree.resolve("toy/check.sh");
        Files.writeString(script, "#!/usr/bin/env bash\nexit 0\n");
        script.toFile().setExecutable(true);
        var candidate = new Candidate(
                "candidate-mut-003", "mut-003", "candidate/baseline-mut-003", worktree,
                "0123456789abcdef0123456789abcdef01234567");
        var runner = new CommandCheckRunner(List.of(
                new CommandCheckRunner.CommandCheck("contained-check", List.of("./toy/check.sh"))));

        assertThat(runner.runChecks(candidate).get(0).status()).isEqualTo(CheckStatus.PASSED);
    }

    /**
     * At Layer 3 a check invokes the real Java toolchain against candidate source. A mutation that
     * does not compile must not crash the run — it must be recorded as a failed check with the
     * compiler diagnostic in the summary, so the loop takes an ordinary {@code DISCARD} decision
     * rather than surfacing an unhelpful stack trace. Covers `S8` on `CHG-004`.
     */
    @Test
    void recordsCompilerFailureAsAFailedCheckRatherThanCrashing() throws Exception {
        Path worktree = tempDir.resolve("uncompilable");
        Files.createDirectories(worktree.resolve("src"));
        Files.writeString(
                worktree.resolve("src/Broken.java"),
                "class Broken { void x() { doesNotExist(); } }\n");
        var candidate = new Candidate(
                "candidate-mut-004", "mut-004", "candidate/baseline-mut-004", worktree,
                "0123456789abcdef0123456789abcdef01234567");
        var runner = new CommandCheckRunner(List.of(
                new CommandCheckRunner.CommandCheck(
                        "compile-check",
                        List.of("sh", "-c", "javac -d out src/Broken.java")
                )
        ));

        var evidence = runner.runChecks(candidate);

        assertThat(evidence).hasSize(1);
        assertThat(evidence.get(0).name()).isEqualTo("compile-check");
        assertThat(evidence.get(0).status()).isEqualTo(CheckStatus.FAILED);
        assertThat(evidence.get(0).summary())
                .contains("cannot find symbol")
                .contains("Broken.java");
    }

    @Test
    void behaviourCaseCannotReadTheModelProviderApiKey() throws Exception {
        Path worktree = tempDir.resolve("scrubbed-env");
        Files.createDirectories(worktree);
        var candidate = new Candidate(
                "candidate-mut-005",
                "mut-005",
                "candidate/baseline-mut-005",
                worktree,
                "0123456789abcdef0123456789abcdef01234567");
        var runner = new CommandCheckRunner(
                List.of(new CommandCheckRunner.CommandCheck(
                        "credential-scrub",
                        List.of("sh", "-c", """
                                test -z "${SAAA_MODEL_API_KEY:-}" \
                                && test -z "${OPENAI_API_KEY:-}" \
                                && test "$PATH" = /usr/bin
                                """),
                        Duration.ofSeconds(5),
                        List.of("PATH", "SAAA_MODEL_*", "OPENAI_API_KEY"))),
                () -> Map.of(
                        "PATH", "/usr/bin",
                        "SAAA_MODEL_API_KEY", "sk-super-secret",
                        "OPENAI_API_KEY", "sk-openai-secret",
                        "LC_ALL", "C"));

        var evidence = runner.runChecks(candidate);

        assertThat(evidence).hasSize(1);
        assertThat(evidence.get(0).status()).isEqualTo(CheckStatus.PASSED);
        assertThat(evidence.get(0).summary()).contains("exit=0");
    }

    @Test
    void rejectsTimeoutsBelowProcessWaitGranularity() {
        assertThatThrownBy(() -> new CommandCheckRunner.CommandCheck(
                "too-fast",
                List.of("sh", "-c", "true"),
                Duration.ofNanos(1)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("timeout must be at least one millisecond");
    }
}
