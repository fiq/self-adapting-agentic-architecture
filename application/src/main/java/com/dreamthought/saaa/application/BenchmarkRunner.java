package com.dreamthought.saaa.application;

import com.dreamthought.saaa.core.BenchmarkEvidence;
import com.dreamthought.saaa.core.Candidate;
import java.util.List;

@FunctionalInterface
public interface BenchmarkRunner {
    List<BenchmarkEvidence> runBenchmarks(Candidate candidate);
}
