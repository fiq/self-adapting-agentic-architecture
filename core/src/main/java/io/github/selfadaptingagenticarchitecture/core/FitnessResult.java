package io.github.selfadaptingagenticarchitecture.core;

import java.util.Map;
import java.util.Objects;

public record FitnessResult(
        Candidate candidate,
        EvaluationEvidence evidence,
        Map<String, Double> objectives,
        double aggregateScore,
        FitnessDecision decision
) {
    public FitnessResult {
        candidate = Objects.requireNonNull(candidate, "candidate");
        evidence = Objects.requireNonNull(evidence, "evidence");
        objectives = Map.copyOf(Objects.requireNonNull(objectives, "objectives"));
        if (!Double.isFinite(aggregateScore)) {
            throw new IllegalArgumentException("aggregateScore must be finite");
        }
        decision = Objects.requireNonNull(decision, "decision");
    }
}
