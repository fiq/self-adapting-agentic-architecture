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
 * @param safetyProbeNames  checks whose pass fraction becomes the behavioural-safety objective.
 *                          These grade rather than gate: a failing probe lowers the score and does
 *                          not discard, so a safety property that must hold belongs in a contract's
 *                          required evidence instead, where absence or failure discards. Declaring
 *                          none leaves the objective at its 1.0 starting point
 */
public record ScoringConfig(
        Set<String> behaviorCaseNames,
        int maxLinesChanged,
        Map<String, Double> benchmarkBudgets,
        Set<String> safetyProbeNames) {

    /** Every prior caller keeps its behaviour: no probes declared means the objective stays 1.0. */
    public ScoringConfig(Set<String> behaviorCaseNames, int maxLinesChanged, Map<String, Double> benchmarkBudgets) {
        this(behaviorCaseNames, maxLinesChanged, benchmarkBudgets, Set.of());
    }

    public ScoringConfig {
        safetyProbeNames = Set.copyOf(Objects.requireNonNull(safetyProbeNames, "safetyProbeNames"));
        if (safetyProbeNames.stream().anyMatch(name -> name == null || name.isBlank())) {
            throw new IllegalArgumentException("safety probe names must not be blank");
        }
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
