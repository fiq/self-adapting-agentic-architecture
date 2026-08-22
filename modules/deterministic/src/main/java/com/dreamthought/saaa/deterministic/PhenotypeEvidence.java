package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.CheckEvidence;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.RealizationSummary;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Everything deterministic scoring is allowed to see: build and benchmark evidence, the required
 * behavior cases, graded objective measurements, and how much the candidate actually changed. No
 * approval, score or promotion field from the model may enter here.
 *
 * <p>{@code evidence} is the complete record of what ran and is what gets persisted, transported
 * and audited. {@code nonGatingCheckNames} narrows only the gate: a check named there is still
 * evidence, still stored and still visible, but it grades an objective instead of discarding the
 * candidate. Withholding is expressed as a name set rather than by removing the check, because a
 * check deleted to spare the gate would also vanish from the audit trail that has to show it ran.
 */
public record PhenotypeEvidence(
        EvaluationEvidence evidence,
        List<BehaviorCaseEvidence> behaviorCases,
        Map<String, Double> objectiveScores,
        RealizationSummary realization,
        Set<String> nonGatingCheckNames
) {
    public PhenotypeEvidence {
        evidence = Objects.requireNonNull(evidence, "evidence");
        behaviorCases = List.copyOf(Objects.requireNonNull(behaviorCases, "behaviorCases"));
        objectiveScores = Map.copyOf(Objects.requireNonNull(objectiveScores, "objectiveScores"));
        realization = Objects.requireNonNull(realization, "realization");
        nonGatingCheckNames = Set.copyOf(Objects.requireNonNull(nonGatingCheckNames, "nonGatingCheckNames"));
    }

    /** Every check gates. */
    public PhenotypeEvidence(
            EvaluationEvidence evidence,
            List<BehaviorCaseEvidence> behaviorCases,
            Map<String, Double> objectiveScores,
            RealizationSummary realization) {
        this(evidence, behaviorCases, objectiveScores, realization, Set.of());
    }

    /** The checks the deterministic-checks gate judges: everything not withheld for grading. */
    public List<CheckEvidence> gatingChecks() {
        if (nonGatingCheckNames.isEmpty()) {
            return evidence.checks();
        }
        return evidence.checks().stream()
                .filter(check -> !nonGatingCheckNames.contains(check.name()))
                .toList();
    }
}
