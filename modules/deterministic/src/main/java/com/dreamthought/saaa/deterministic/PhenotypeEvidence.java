package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.RealizationSummary;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Everything deterministic scoring is allowed to see: build and benchmark evidence, the required
 * behavior cases, graded objective measurements, and how much the candidate actually changed. No
 * approval, score or promotion field from the model may enter here.
 */
public record PhenotypeEvidence(
        EvaluationEvidence evidence,
        List<BehaviorCaseEvidence> behaviorCases,
        Map<String, Double> objectiveScores,
        RealizationSummary realization
) {
    public PhenotypeEvidence {
        evidence = Objects.requireNonNull(evidence, "evidence");
        behaviorCases = List.copyOf(Objects.requireNonNull(behaviorCases, "behaviorCases"));
        objectiveScores = Map.copyOf(Objects.requireNonNull(objectiveScores, "objectiveScores"));
        realization = Objects.requireNonNull(realization, "realization");
    }
}
