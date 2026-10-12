package com.dreamthought.saaa.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/** Provider-neutral usage evidence captured after an invocation. */
public record AgentUsage(
        OptionalLong inputTokens,
        OptionalLong outputTokens,
        Optional<BigDecimal> creditsConsumed,
        OptionalLong wallClockMillis,
        OptionalInt retries,
        Optional<PriceIdentity> priceIdentity
) {
    public AgentUsage {
        inputTokens = Objects.requireNonNull(inputTokens, "inputTokens");
        outputTokens = Objects.requireNonNull(outputTokens, "outputTokens");
        creditsConsumed = Objects.requireNonNull(creditsConsumed, "creditsConsumed");
        wallClockMillis = Objects.requireNonNull(wallClockMillis, "wallClockMillis");
        retries = Objects.requireNonNull(retries, "retries");
        priceIdentity = Objects.requireNonNull(priceIdentity, "priceIdentity");
        inputTokens.ifPresent(value -> requireNonNegative(value, "usage values"));
        outputTokens.ifPresent(value -> requireNonNegative(value, "usage values"));
        creditsConsumed.ifPresent(value -> requireNonNegative(value, "creditsConsumed"));
        wallClockMillis.ifPresent(value -> requireNonNegative(value, "usage values"));
        retries.ifPresent(value -> requireNonNegative(value, "usage values"));
    }

    public static AgentUsage none() {
        return new AgentUsage(
                OptionalLong.empty(),
                OptionalLong.empty(),
                Optional.empty(),
                OptionalLong.empty(),
                OptionalInt.empty(),
                Optional.empty());
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }

    private static void requireNonNegative(BigDecimal value, String name) {
        if (value.signum() < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
