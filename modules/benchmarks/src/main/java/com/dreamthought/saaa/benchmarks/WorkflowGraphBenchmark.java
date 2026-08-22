package com.dreamthought.saaa.benchmarks;

import com.dreamthought.saaa.domain.WorkflowGraph;
import org.openjdk.jmh.annotations.Benchmark;

public class WorkflowGraphBenchmark {
    @Benchmark
    public WorkflowGraph createWorkflowGraph() {
        return new WorkflowGraph("baseline", "v1", "agent -> tool -> answer");
    }
    /**
     * A second measured method, so one definition can match more than one benchmark. That is the
     * case whose evidence carries a derived name, and without it the naming contract between
     * {@code :benchmarks} and {@code :deterministic} has no coverage at all.
     */
    @Benchmark
    public WorkflowGraph createLongerWorkflowGraph() {
        return new WorkflowGraph("baseline", "v1", "agent -> retrieval -> tool -> critic -> answer");
    }

}
