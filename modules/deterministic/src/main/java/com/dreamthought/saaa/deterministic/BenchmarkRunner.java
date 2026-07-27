package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.BenchmarkEvidence;
import com.dreamthought.saaa.domain.Candidate;
import java.util.List;

@FunctionalInterface
public interface BenchmarkRunner {
    List<BenchmarkEvidence> runBenchmarks(Candidate candidate);
}
