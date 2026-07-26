package io.github.selfadaptingagenticarchitecture.application;

import io.github.selfadaptingagenticarchitecture.core.BenchmarkEvidence;
import io.github.selfadaptingagenticarchitecture.core.Candidate;
import java.util.List;

@FunctionalInterface
public interface BenchmarkRunner {
    List<BenchmarkEvidence> runBenchmarks(Candidate candidate);
}
