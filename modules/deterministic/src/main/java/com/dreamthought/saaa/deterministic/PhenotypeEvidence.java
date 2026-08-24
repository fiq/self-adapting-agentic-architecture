package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.CheckEvidence;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.RealizationSummary;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Everything deterministic scoring is allowed to see: build and benchmark evidence, the required
 * behavior cases, graded objective measurements, and how much the candidate actually changed. No
 * approval, score or promotion field from the model may enter here.
 *
 * <p>{@code evidence} is the complete record of what ran and is what gets persisted, transported
 * and audited. {@code nonGatingCheckNames} narrows only the gate: a check named there is still
 * evidence, still stored and still visible, but it grades an objective instead of discarding the
 * candidate. Withholding is expressed as a name set rather than by removing the check, because a
 * check deleted to spare the gate would also vanish from the audit trail that has to show it ran.
 */
public record PhenotypeEvidence(
        EvaluationEvidence evidence,
        List<BehaviorCaseEvidence> behaviorCases,
        Map<String, Double> objectiveScores,
        RealizationSummary realization,
        Set<String> nonGatingCheckNames,
        Set<String> heldOutCaseNames,
        // Configuration the scorer cannot otherwise see, carried here so the scoring context can
        // fingerprint everything that changes what a magnitude means. The scorer knows the objective
        // set and the threshold; only the bridge knows these. See ScoringContext.
        Set<String> gatingCaseNames,
        int maxLinesChanged,
        Map<String, Double> benchmarkBudgets
) {
    public PhenotypeEvidence {
        evidence = Objects.requireNonNull(evidence, "evidence");
        behaviorCases = List.copyOf(Objects.requireNonNull(behaviorCases, "behaviorCases"));
        objectiveScores = Map.copyOf(Objects.requireNonNull(objectiveScores, "objectiveScores"));
        realization = Objects.requireNonNull(realization, "realization");
        nonGatingCheckNames = Set.copyOf(Objects.requireNonNull(nonGatingCheckNames, "nonGatingCheckNames"));
        heldOutCaseNames = Set.copyOf(Objects.requireNonNull(heldOutCaseNames, "heldOutCaseNames"));
        gatingCaseNames = Set.copyOf(Objects.requireNonNull(gatingCaseNames, "gatingCaseNames"));
        benchmarkBudgets = Map.copyOf(Objects.requireNonNull(benchmarkBudgets, "benchmarkBudgets"));
        if (maxLinesChanged <= 0) {
            throw new IllegalArgumentException("maxLinesChanged must be positive");
        }
    }

    /**
     * Held-out cases declared, but no scoring configuration carried. Suitable for tests that assert
     * gate behaviour rather than comparability; a scoring context built from this cannot fingerprint
     * the configuration, so production must use the canonical constructor.
     */
    public PhenotypeEvidence(
            EvaluationEvidence evidence,
            List<BehaviorCaseEvidence> behaviorCases,
            Map<String, Double> objectiveScores,
            RealizationSummary realization,
            Set<String> nonGatingCheckNames,
            Set<String> heldOutCaseNames) {
        this(evidence, behaviorCases, objectiveScores, realization, nonGatingCheckNames,
                heldOutCaseNames, Set.of(), 1, Map.of());
    }

    /** No held-out cases: every behaviour case both gates and scores, as before CHG-024. */
    public PhenotypeEvidence(
            EvaluationEvidence evidence,
            List<BehaviorCaseEvidence> behaviorCases,
            Map<String, Double> objectiveScores,
            RealizationSummary realization,
            Set<String> nonGatingCheckNames) {
        this(evidence, behaviorCases, objectiveScores, realization, nonGatingCheckNames, Set.of(),
                Set.of(), 1, Map.of());
    }

    /** Every check gates. */
    public PhenotypeEvidence(
            EvaluationEvidence evidence,
            List<BehaviorCaseEvidence> behaviorCases,
            Map<String, Double> objectiveScores,
            RealizationSummary realization) {
        this(evidence, behaviorCases, objectiveScores, realization, Set.of(), Set.of(),
                Set.of(), 1, Map.of());
    }

    /**
     * The behaviour cases the {@code required_behavior_cases} gate judges.
     *
     * <p>This exists because {@code nonGatingCheckNames} cannot do the job. That set narrows
     * {@link #gatingChecks()}, which filters {@code evidence.checks()}; the behaviour-case gate reads
     * {@link #behaviorCases()}, a different collection that {@code task_success} is also computed
     * from. Safety probes and reliability repeats never collide with this because they are never
     * behaviour cases at all — they grade from the check evidence directly.
     *
     * <p>A held-out case is the one kind of check that must feed the objective and withhold from the
     * gate at the same time, so the two views of the same list have to be separated here. Held-out
     * cases stay in {@code behaviorCases} so they still lower {@code task_success}; they are removed
     * only from what the gate sees. See CHG-024.
     */
    public List<BehaviorCaseEvidence> gatingBehaviorCases() {
        if (heldOutCaseNames.isEmpty()) {
            return behaviorCases;
        }
        return behaviorCases.stream()
                .filter(behaviorCase -> !heldOutCaseNames.contains(behaviorCase.id()))
                .toList();
    }

    /** The checks the deterministic-checks gate judges: everything not withheld for grading. */
    public List<CheckEvidence> gatingChecks() {
        if (nonGatingCheckNames.isEmpty()) {
            return evidence.checks();
        }
        return evidence.checks().stream()
                .filter(check -> !nonGatingCheckNames.contains(check.name()))
                .toList();
    }
}
