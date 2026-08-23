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
        Set<String> safetyProbeNames,
        int reliabilityRuns) {

    /**
     * Separates a behaviour case from the index of a repeated run of it, as in
     * {@code unit_tests_pass.run2}. Repeated runs carry the same command and a distinct name so each
     * result is separately attributable, and they are withheld from the deterministic-checks gate:
     * the canonical run decides whether the candidate is eligible, the repeats grade how reliably it
     * holds. Without the withholding a single flaky run would discard rather than lower a score,
     * which is the trap that kept reliability pinned at 1.0 for every candidate that promoted.
     */
    public static final String REPEAT_RUN_SEPARATOR = ".run";

    /** The behaviour case a check result belongs to, collapsing any repeated-run suffix. */
    public static String baseCaseName(String checkName) {
        int separator = checkName.lastIndexOf(REPEAT_RUN_SEPARATOR);
        if (separator <= 0) {
            return checkName;
        }
        String suffix = checkName.substring(separator + REPEAT_RUN_SEPARATOR.length());
        return !suffix.isEmpty() && suffix.chars().allMatch(Character::isDigit)
                ? checkName.substring(0, separator)
                : checkName;
    }

    /** Every check gates and each behaviour case runs once. */
    public ScoringConfig(Set<String> behaviorCaseNames, int maxLinesChanged,
            Map<String, Double> benchmarkBudgets, Set<String> safetyProbeNames) {
        this(behaviorCaseNames, maxLinesChanged, benchmarkBudgets, safetyProbeNames, 1);
    }

    /** Every prior caller keeps its behaviour: no probes declared means the objective stays 1.0. */
    public ScoringConfig(Set<String> behaviorCaseNames, int maxLinesChanged, Map<String, Double> benchmarkBudgets) {
        this(behaviorCaseNames, maxLinesChanged, benchmarkBudgets, Set.of(), 1);
    }

    public ScoringConfig {
        safetyProbeNames = Set.copyOf(Objects.requireNonNull(safetyProbeNames, "safetyProbeNames"));
        if (safetyProbeNames.stream().anyMatch(name -> name == null || name.isBlank())) {
            throw new IllegalArgumentException("safety probe names must not be blank");
        }
        // A probe named for a structural gate is withheld from that gate while the gate's own signal
        // still records its outcome, so the audit trail would show a failed probe beside a passing
        // gate of the same name. Declared required evidence already rejects these reserved names.
        var reserved = safetyProbeNames.stream()
                .filter(PhenotypeFitnessScorer.STRUCTURAL_GATE_NAMES::contains)
                .toList();
        if (!reserved.isEmpty()) {
            throw new IllegalArgumentException(
                    "safety probe name collides with a structural gate: " + reserved);
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
        if (reliabilityRuns < 1) {
            throw new IllegalArgumentException("reliabilityRuns must be at least 1");
        }
    }
}
