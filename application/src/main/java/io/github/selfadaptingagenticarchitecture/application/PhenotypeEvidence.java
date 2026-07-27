package io.github.selfadaptingagenticarchitecture.application;

import io.github.selfadaptingagenticarchitecture.core.EvaluationEvidence;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Everything deterministic scoring is allowed to see: build and benchmark evidence, the required
 * behavior cases, and graded objective measurements. No approval, score or promotion field from the
 * model may enter here.
 */
public record PhenotypeEvidence(
        EvaluationEvidence evidence,
        List<BehaviorCaseEvidence> behaviorCases,
        Map<String, Double> objectiveScores
) {
    public PhenotypeEvidence {
        evidence = Objects.requireNonNull(evidence, "evidence");
        behaviorCases = List.copyOf(Objects.requireNonNull(behaviorCases, "behaviorCases"));
        objectiveScores = Map.copyOf(Objects.requireNonNull(objectiveScores, "objectiveScores"));
    }
}
