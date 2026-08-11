package com.dreamthought.saaa.domain;

import java.math.BigDecimal;
import java.util.Objects;

/** Remaining resources available to one bounded agent invocation. */
public record ResourceBudget(
        long inputTokensRemaining,
        long outputTokensRemaining,
        BigDecimal creditsRemaining,
        long wallClockMillisRemaining,
        int retriesRemaining
) {
    public ResourceBudget {
        if (inputTokensRemaining < 0 || outputTokensRemaining < 0 || wallClockMillisRemaining < 0
                || retriesRemaining < 0) {
            throw new IllegalArgumentException("resource budget values must not be negative");
        }
        creditsRemaining = Objects.requireNonNull(creditsRemaining, "creditsRemaining");
        if (creditsRemaining.signum() < 0) {
            throw new IllegalArgumentException("creditsRemaining must not be negative");
        }
    }
}
