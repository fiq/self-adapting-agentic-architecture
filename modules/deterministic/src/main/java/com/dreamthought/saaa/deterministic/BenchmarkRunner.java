package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.BenchmarkEvidence;
import com.dreamthought.saaa.domain.Candidate;
import java.util.List;

@FunctionalInterface
public interface BenchmarkRunner {
    /**
     * Separator between a benchmark definition's name and the specific benchmark a multi-result
     * definition produced. An implementation that measures several benchmarks under one definition
     * names its evidence {@code definition + DERIVED_NAME_SEPARATOR + qualifiedBenchmarkName};
     * scoring falls back to the text before the last separator to find the declared budget.
     *
     * <p>This lives on the port because the producer is in {@code :benchmarks} and the consumer is
     * in {@code :deterministic}, with no dependency between them. Before it was stated here the two
     * agreed by coincidence, and changing the separator on one side silently stopped every budget
     * from matching, leaving {@code cost_latency_budget} at 1.0 for a run that appeared budgeted.
     *
     * <p>Measured values must be ones where lower is better, such as latency or cost, because
     * scoring treats a declared budget as an upper bound.
     */
    String DERIVED_NAME_SEPARATOR = ":";

    List<BenchmarkEvidence> runBenchmarks(Candidate candidate);
}
