package com.dreamthought.saaa.benchmarks;

import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.deterministic.BenchmarkRunner;
import com.dreamthought.saaa.domain.Candidate;
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
    /**
     * CHG-016. A definition matching more than one benchmark names each result
     * {@code definition + DERIVED_NAME_SEPARATOR + qualifiedName}. Scoring depends on that shape to
     * find the declared budget, and the two modules have no dependency between them, so without this
     * test a separator change here silently leaves cost_latency_budget at 1.0 for a budgeted run.
     */
    @Test
    void namesEveryResultOfAMultiMatchDefinitionAfterTheDefinition() {
        var candidate = new Candidate(
                "candidate-mut-002", "mut-002", "candidate/baseline-mut-002",
                Path.of(".worktrees/candidate-mut-002"), "def5678");
        var runner = new JmhBenchmarkRunner(List.of(
                new JmhBenchmarkRunner.BenchmarkDefinition(
                        "workflow-graph", WorkflowGraphBenchmark.class.getName() + ".*")));

        var evidence = runner.runBenchmarks(candidate);

        assertThat(evidence).hasSizeGreaterThan(1);
        assertThat(evidence).allSatisfy(measured -> assertThat(measured.name())
                .startsWith("workflow-graph" + BenchmarkRunner.DERIVED_NAME_SEPARATOR));
        assertThat(evidence).extracting(e -> e.name()).doesNotHaveDuplicates();
    }

}
