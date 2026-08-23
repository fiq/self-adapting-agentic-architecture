package com.dreamthought.saaa.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

final class FitnessScoreTest {
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
