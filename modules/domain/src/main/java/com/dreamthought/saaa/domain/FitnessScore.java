package com.dreamthought.saaa.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * The raw weighted magnitude and the gate decision for one candidate.
 *
 * <p>The natural order is worst to best: a promotion outranks a discard, then a larger magnitude
 * breaks ties. Consumers that need the best candidate therefore use this value's natural order in
 * reverse rather than constructing a score-only comparator.
 */
public record FitnessScore(BigDecimal rawMagnitude, FitnessDecision decision)
        implements Comparable<FitnessScore> {
    public FitnessScore {
        rawMagnitude = Objects.requireNonNull(rawMagnitude, "rawMagnitude");
        decision = Objects.requireNonNull(decision, "decision");
    }

    public static FitnessScore of(double rawMagnitude, FitnessDecision decision) {
        if (!Double.isFinite(rawMagnitude)) {
            throw new IllegalArgumentException("rawMagnitude must be finite");
        }
        return new FitnessScore(BigDecimal.valueOf(rawMagnitude), decision);
    }

    @Override
    public int compareTo(FitnessScore other) {
        Objects.requireNonNull(other, "other");
        int decisionComparison = Integer.compare(rank(decision), rank(other.decision));
        return decisionComparison != 0
                ? decisionComparison
                : rawMagnitude.compareTo(other.rawMagnitude);
    }

    private static int rank(FitnessDecision decision) {
        return decision == FitnessDecision.PROMOTE ? 1 : 0;
    }
}
