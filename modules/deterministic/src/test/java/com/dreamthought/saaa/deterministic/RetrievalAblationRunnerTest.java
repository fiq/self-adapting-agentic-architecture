package com.dreamthought.saaa.deterministic;

import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.domain.RetrievalAblationTask;
import com.dreamthought.saaa.domain.RetrievalAttemptMetrics;
import com.dreamthought.saaa.domain.RetrievalMode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class RetrievalAblationRunnerTest {
    @Test
    void comparesIdenticalTasksAcrossAllRetrievalModes() {
        var calls = new ArrayList<String>();
        var runner = new RetrievalAblationRunner((task, mode, attempt) -> {
            calls.add(task.id() + ":" + mode + ":" + attempt);
            boolean accepted = mode == RetrievalMode.HYBRID;
            return new RetrievalAttemptMetrics(
                    task.id(), mode, attempt, accepted, accepted ? 0.8 : 0.4,
                    accepted ? 0.3 : -0.1, accepted ? 0 : 1, 0, accepted ? 0 : 1,
                    1, 0, 100, 20, 0, 1000, mode == RetrievalMode.NONE ? 0 : 10,
                    mode == RetrievalMode.GRAPH || mode == RetrievalMode.HYBRID ? 4 : 0,
                    mode == RetrievalMode.NONE ? 0 : 2, mode == RetrievalMode.NONE ? 0 : 40,
                    0, 2, "retrieval-config-v1", "lineage-novelty-v1");
        });
        var tasks = List.of(
                new RetrievalAblationTask("task-a", "first", 0.5),
                new RetrievalAblationTask("task-b", "second", 0.5));
        var modes = List.of(RetrievalMode.NONE, RetrievalMode.VECTOR, RetrievalMode.GRAPH, RetrievalMode.HYBRID);

        var report = runner.run("ablation-1", tasks, modes, 2);

        assertThat(calls).hasSize(16);
        assertThat(modes).allSatisfy(mode -> assertThat(calls)
                .contains("task-a:" + mode + ":1", "task-b:" + mode + ":2"));
        assertThat(report.treatments()).filteredOn(summary -> summary.mode() == RetrievalMode.HYBRID)
                .singleElement().satisfies(summary -> {
                    assertThat(summary.accepted()).isEqualTo(4);
                    assertThat(summary.acceptancePerAttempt()).isEqualTo(1.0);
                    assertThat(summary.acceptedImprovementPerCost()).isGreaterThan(0);
                });
        assertThat(report.treatments()).filteredOn(summary -> summary.mode() == RetrievalMode.NONE)
                .singleElement().satisfies(summary -> assertThat(summary.accepted()).isZero());
    }
}
