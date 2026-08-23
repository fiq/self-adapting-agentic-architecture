package com.dreamthought.saaa.domain;

import java.util.Map;
import java.util.Objects;

public record FitnessResult(
        Candidate candidate,
        EvaluationEvidence evidence,
        Map<String, Double> objectives,
        FitnessScore fitnessScore
) {
    public FitnessResult {
        candidate = Objects.requireNonNull(candidate, "candidate");
        evidence = Objects.requireNonNull(evidence, "evidence");
        objectives = Map.copyOf(Objects.requireNonNull(objectives, "objectives"));
        fitnessScore = Objects.requireNonNull(fitnessScore, "fitnessScore");
    }

    public FitnessDecision decision() {
        return fitnessScore.decision();
    }
}
