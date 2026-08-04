package com.dreamthought.saaa.domain;

import java.util.Objects;

public record RetrievalTreatmentSummary(
        RetrievalMode mode,
        int attempts,
        int accepted,
        double acceptancePerAttempt,
        double meanAttemptsToFirstAccepted,
        double bestFitness,
        double acceptedFitnessImprovement,
        double mutationCost,
        double acceptedImprovementPerCost,
        double contextTokensPerAcceptedCandidate
) {
    public RetrievalTreatmentSummary {
        mode = Objects.requireNonNull(mode, "mode");
    }
}
