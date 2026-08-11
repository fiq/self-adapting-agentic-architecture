package com.dreamthought.saaa.domain;

import java.math.BigDecimal;
import java.util.Objects;

/** Provider-neutral usage evidence captured after an invocation. */
public record AgentUsage(
        long inputTokens,
        long outputTokens,
        BigDecimal creditsConsumed,
        long wallClockMillis,
        int retries
) {
    public AgentUsage {
        if (inputTokens < 0 || outputTokens < 0 || wallClockMillis < 0 || retries < 0) {
            throw new IllegalArgumentException("usage values must not be negative");
        }
        creditsConsumed = Objects.requireNonNull(creditsConsumed, "creditsConsumed");
        if (creditsConsumed.signum() < 0) {
            throw new IllegalArgumentException("creditsConsumed must not be negative");
        }
    }

    public static AgentUsage none() {
        return new AgentUsage(0, 0, BigDecimal.ZERO, 0, 0);
    }
}
