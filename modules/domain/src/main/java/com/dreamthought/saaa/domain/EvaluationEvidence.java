package com.dreamthought.saaa.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record EvaluationEvidence(List<CheckEvidence> checks, List<BenchmarkEvidence> benchmarks, Instant evaluatedAt) {
    public EvaluationEvidence {
        checks = List.copyOf(Objects.requireNonNull(checks, "checks"));
        benchmarks = List.copyOf(Objects.requireNonNull(benchmarks, "benchmarks"));
        evaluatedAt = Objects.requireNonNull(evaluatedAt, "evaluatedAt");
    }

    public boolean checksPassed() {
        return checks.stream().allMatch(check -> check.status() == CheckStatus.PASSED);
    }
}
