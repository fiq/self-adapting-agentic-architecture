package com.dreamthought.saaa.domain;

import java.util.Objects;
import java.util.Optional;

public record EvolutionaryMemoryProjectionStatus(Optional<String> policyId, int activeEvaluations) {
    public EvolutionaryMemoryProjectionStatus {
        policyId = Objects.requireNonNull(policyId, "policyId");
        if (activeEvaluations < 0) throw new IllegalArgumentException("activeEvaluations must not be negative");
    }
}
