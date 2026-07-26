package io.github.selfadaptingagenticarchitecture.benchmarks;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.selfadaptingagenticarchitecture.core.Candidate;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class JmhBenchmarkRunnerIntegrationTest {
    @Test
    void convertsJmhPrimaryResultToBenchmarkEvidence() {
        var candidate = new Candidate(
                "candidate-mut-001",
                "mut-001",
                "candidate/baseline-mut-001",
                Path.of(".worktrees/candidate-baseline-mut-001"),
                "0123456789abcdef0123456789abcdef01234567"
        );
        var runner = new JmhBenchmarkRunner(List.of(
                new JmhBenchmarkRunner.BenchmarkDefinition(
                        "workflow-graph-create",
                        WorkflowGraphBenchmark.class.getName() + ".createWorkflowGraph"
                )
        ));

        var evidence = runner.runBenchmarks(candidate);

        assertThat(evidence).hasSize(1);
        assertThat(evidence.getFirst().name()).isEqualTo("workflow-graph-create");
        assertThat(evidence.getFirst().value()).isFinite().isGreaterThanOrEqualTo(0.0);
        assertThat(evidence.getFirst().unit()).isNotBlank();
    }
}
