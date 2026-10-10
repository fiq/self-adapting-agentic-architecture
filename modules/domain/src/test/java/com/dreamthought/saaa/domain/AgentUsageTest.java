package com.dreamthought.saaa.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

final class AgentUsageTest {
    @Test
    void aMeasuredZeroAndAnUnobservedFieldAreDifferentValues() {
        var measuredZero = new AgentUsage(
                OptionalLong.of(0),
                OptionalLong.of(0),
                Optional.of(BigDecimal.ZERO),
                OptionalLong.of(0),
                OptionalInt.of(0),
                Optional.empty());

        assertThat(measuredZero).isNotEqualTo(AgentUsage.none());
    }

    @Test
    void rejectsNegativePresentUsageValues() {
        assertThatThrownBy(() -> usage(OptionalLong.of(-1), OptionalLong.empty(), Optional.empty(),
                OptionalLong.empty(), OptionalInt.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> usage(OptionalLong.empty(), OptionalLong.of(-1), Optional.empty(),
                OptionalLong.empty(), OptionalInt.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> usage(OptionalLong.empty(), OptionalLong.empty(), Optional.of(BigDecimal.valueOf(-1)),
                OptionalLong.empty(), OptionalInt.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> usage(OptionalLong.empty(), OptionalLong.empty(), Optional.empty(),
                OptionalLong.of(-1), OptionalInt.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> usage(OptionalLong.empty(), OptionalLong.empty(), Optional.empty(),
                OptionalLong.empty(), OptionalInt.of(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static AgentUsage usage(
            OptionalLong inputTokens,
            OptionalLong outputTokens,
            Optional<BigDecimal> creditsConsumed,
            OptionalLong wallClockMillis,
            OptionalInt retries) {
        return new AgentUsage(inputTokens, outputTokens, creditsConsumed, wallClockMillis, retries, Optional.empty());
    }
}
