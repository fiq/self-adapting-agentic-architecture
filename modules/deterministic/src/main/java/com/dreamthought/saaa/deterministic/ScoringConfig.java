package com.dreamthought.saaa.deterministic;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Policy inputs the bridge needs that do not come from evidence.
 *
 * @param behaviorCaseNames checks that are required behaviour rather than build health; these
 *                          hard-gate promotion
 * @param maxLinesChanged   the change budget parsimony is measured against
 * @param benchmarkBudgets  benchmark name to its budget in the benchmark's own unit
 */
public record ScoringConfig(Set<String> behaviorCaseNames, int maxLinesChanged, Map<String, Double> benchmarkBudgets) {
    public ScoringConfig {
        behaviorCaseNames = Set.copyOf(Objects.requireNonNull(behaviorCaseNames, "behaviorCaseNames"));
        benchmarkBudgets = Map.copyOf(Objects.requireNonNull(benchmarkBudgets, "benchmarkBudgets"));
        if (behaviorCaseNames.isEmpty()) {
            throw new IllegalArgumentException("at least one check must be declared a behaviour case");
        }
        // A blank name cannot be matched against any check evidence, so it would be a declared
        // required behaviour that no check can ever satisfy.
        if (behaviorCaseNames.stream().anyMatch(name -> name == null || name.isBlank())) {
            throw new IllegalArgumentException("behaviour case names must not be blank");
        }
        if (maxLinesChanged <= 0) {
            throw new IllegalArgumentException("maxLinesChanged must be positive");
        }
    }
}
