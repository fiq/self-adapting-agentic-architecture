package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.FitnessObjective;
import com.dreamthought.saaa.domain.MutationBounds;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic defaults an operator contributes to the next loop. Model output may fill in a
 * contract, but it may not redefine these.
 */
public record MutationOperatorDefaults(
        MutationBounds bounds,
        List<String> requiredEvidence,
        List<String> hardGates,
        List<FitnessObjective> objectives
) {
    public MutationOperatorDefaults {
        bounds = Objects.requireNonNull(bounds, "bounds");
        requiredEvidence = List.copyOf(Objects.requireNonNull(requiredEvidence, "requiredEvidence"));
        hardGates = List.copyOf(Objects.requireNonNull(hardGates, "hardGates"));
        objectives = List.copyOf(Objects.requireNonNull(objectives, "objectives"));
        if (requiredEvidence.isEmpty()) {
            throw new IllegalArgumentException("requiredEvidence must not be empty");
        }
        if (hardGates.isEmpty()) {
            throw new IllegalArgumentException("hardGates must not be empty");
        }
    }
}
