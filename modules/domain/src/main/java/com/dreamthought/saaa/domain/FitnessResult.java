package com.dreamthought.saaa.domain;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record FitnessResult(
        Candidate candidate,
        EvaluationEvidence evidence,
        Map<String, Double> objectives,
        FitnessScore fitnessScore,
        // What the magnitude was measured against. Two results are comparable only when these agree;
        // see ScoringContext and RISK-007. Absent for a result built before CHG-024, which stays
        // readable as history and never becomes comparable.
        Optional<ScoringContext> scoringContext
) {
    public FitnessResult {
        candidate = Objects.requireNonNull(candidate, "candidate");
        evidence = Objects.requireNonNull(evidence, "evidence");
        objectives = Map.copyOf(Objects.requireNonNull(objectives, "objectives"));
        fitnessScore = Objects.requireNonNull(fitnessScore, "fitnessScore");
        scoringContext = Objects.requireNonNull(scoringContext, "scoringContext");
    }

    /** A result whose scoring context was not captured, such as one built by an older caller. */
    public FitnessResult(
            Candidate candidate,
            EvaluationEvidence evidence,
            Map<String, Double> objectives,
            FitnessScore fitnessScore) {
        this(candidate, evidence, objectives, fitnessScore, Optional.empty());
    }

    /**
     * The fingerprint this result may be compared against, or {@code legacy-unversioned} when the
     * context was never captured. Never absent, so a caller cannot forget to handle the legacy case
     * and silently compare across a semantics boundary.
     */
    public String scoringFingerprint() {
        return scoringContext.map(ScoringContext::fingerprint).orElse(ScoringContext.LEGACY_UNVERSIONED);
    }

    public FitnessDecision decision() {
        return fitnessScore.decision();
    }
}
