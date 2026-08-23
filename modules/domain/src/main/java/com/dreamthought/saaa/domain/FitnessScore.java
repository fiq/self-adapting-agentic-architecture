package com.dreamthought.saaa.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * The raw weighted magnitude and the gate decision for one candidate.
 *
 * <p>The natural order is worst to best: a promotion outranks a discard, then a larger magnitude
 * breaks ties. Consumers that need the best candidate therefore use this value's natural order in
 * reverse rather than constructing a score-only comparator.
 *
 * <p>The magnitude is held at a normalised scale so that {@code equals} and {@code compareTo} agree,
 * which {@link Comparable} requires and which a raw {@code BigDecimal} component would violate.
 *
 * <p>This type does not make a score-only ordering impossible, and claiming so would be wrong:
 * {@code rawMagnitude()} is public because arithmetic on the magnitude is legitimate — a delta
 * against a baseline, an average across attempts. What it does is make the correct ordering the
 * easy one and the incorrect one visible at the call site, where {@code comparing(x::rawMagnitude)}
 * reads as the deliberate act it is. Ordering that ignores {@code decision} remains a review
 * concern, not a compile error.
 */
public record FitnessScore(BigDecimal rawMagnitude, FitnessDecision decision)
        implements Comparable<FitnessScore> {
    public FitnessScore {
        rawMagnitude = Objects.requireNonNull(rawMagnitude, "rawMagnitude");
        // Normalise the scale so equals agrees with compareTo. BigDecimal.equals distinguishes 0.5
        // from 0.50 while compareTo calls them equal, and this is a record, so its generated equals
        // inherits that. A Comparable whose equals disagrees with its ordering breaks quietly: a
        // TreeSet keeps one of the pair, a HashSet keeps both, and a magnitude read back from storage
        // at a different scale stops equalling the one that was written.
        rawMagnitude = rawMagnitude.stripTrailingZeros();
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
