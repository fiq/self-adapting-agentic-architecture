package io.github.selfadaptingagenticarchitecture.benchmarks;

import io.github.selfadaptingagenticarchitecture.core.WorkflowGraph;
import org.openjdk.jmh.annotations.Benchmark;

public class WorkflowGraphBenchmark {
    @Benchmark
    public WorkflowGraph createWorkflowGraph() {
        return new WorkflowGraph("baseline", "v1", "agent -> tool -> answer");
    }
}
