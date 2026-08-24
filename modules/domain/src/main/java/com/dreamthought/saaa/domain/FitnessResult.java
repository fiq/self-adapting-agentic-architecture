package com.dreamthought.saaa.domain;

import java.util.Map;
import java.util.Objects;

public record FitnessResult(
        Candidate candidate,
        EvaluationEvidence evidence,
        Map<String, Double> objectives,
        FitnessScore fitnessScore,
        // What the magnitude was measured against. Two results are comparable only when these agree;
        // see ScoringContext and RISK-007. Mandatory rather than optional: a result that could omit
        // it would let production silently produce an uncomparable score, which is the trap an
        // independent review flagged when this was defaulted.
        ScoringContext scoringContext
) {
    public FitnessResult {
        candidate = Objects.requireNonNull(candidate, "candidate");
        evidence = Objects.requireNonNull(evidence, "evidence");
        objectives = Map.copyOf(Objects.requireNonNull(objectives, "objectives"));
        fitnessScore = Objects.requireNonNull(fitnessScore, "fitnessScore");
        scoringContext = Objects.requireNonNull(scoringContext, "scoringContext");
    }

    /** The fingerprint this result may be compared against. */
    public String scoringFingerprint() {
        return scoringContext.fingerprint();
    }

    public FitnessDecision decision() {
        return fitnessScore.decision();
    }
}
