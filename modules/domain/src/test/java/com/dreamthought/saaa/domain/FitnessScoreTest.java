package com.dreamthought.saaa.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/** CHG-023. The magnitude and the decision are one value, and its ordering is the only ordering. */
final class FitnessScoreTest {
    /**
     * {@code BigDecimal.equals} distinguishes 0.5 from 0.50 while {@code compareTo} calls them equal,
     * so a record carrying a raw BigDecimal has an equals inconsistent with its own ordering. That is
     * a documented violation of the Comparable contract, and it does not fail loudly: a TreeSet keeps
     * one of the two, a HashSet keeps both, and a round-trip through storage that returns a different
     * scale stops comparing equal to the value that was written.
     */
    @Test
    void twoMagnitudesThatCompareEqualAreEqual() {
        var fromScorer = FitnessScore.of(1.0, FitnessDecision.PROMOTE);
        var fromStorage = new FitnessScore(new BigDecimal("1.00"), FitnessDecision.PROMOTE);

        assertThat(fromScorer.compareTo(fromStorage))
                .as("the same magnitude at a different scale is the same magnitude")
                .isZero();
        assertThat(fromScorer)
                .as("equals must agree with compareTo, or a set of scores silently holds duplicates")
                .isEqualTo(fromStorage);
        assertThat(new TreeSet<>(List.of(fromScorer, fromStorage)))
                .as("a sorted set must treat them as one element")
                .hasSize(1);
    }

    @Test
    void ordersPromotionAboveADiscardEvenWhenTheDiscardHasTheLargerMagnitude() {
        var promoted = FitnessScore.of(0.85, FitnessDecision.PROMOTE);
        var discarded = FitnessScore.of(0.95, FitnessDecision.DISCARD);

        assertThat(promoted).isGreaterThan(discarded);
    }

    @Test
    void preservesRawMagnitudeForOrderingWithinOneDecision() {
        var nearMiss = new FitnessScore(new BigDecimal("0.5949"), FitnessDecision.DISCARD);
        var totalMiss = new FitnessScore(new BigDecimal("0.5851"), FitnessDecision.DISCARD);

        assertThat(nearMiss).isGreaterThan(totalMiss);
        assertThat(nearMiss.rawMagnitude()).isEqualByComparingTo("0.5949");
    }
}
