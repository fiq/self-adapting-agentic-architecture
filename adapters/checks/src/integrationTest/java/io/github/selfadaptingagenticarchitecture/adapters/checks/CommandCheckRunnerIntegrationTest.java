package io.github.selfadaptingagenticarchitecture.adapters.checks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.selfadaptingagenticarchitecture.core.Candidate;
import io.github.selfadaptingagenticarchitecture.core.CheckStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
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
