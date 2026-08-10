package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.CheckStatus;
import com.dreamthought.saaa.domain.FitnessDecision;
import com.dreamthought.saaa.domain.FitnessObjective;
import com.dreamthought.saaa.domain.FitnessResult;
import com.dreamthought.saaa.domain.FitnessSignalId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic, evidence-only scoring of a candidate phenotype.
 *
 * <p>Hard gates run first and are not tradeable: a candidate that fails one scores {@code 0.0} and
 * is discarded no matter how good its weighted objectives look. Only after every gate passes do the
 * weighted objectives decide promotion.
 */
public final class PhenotypeFitnessScorer {
    public static final double PROMOTION_THRESHOLD = 0.80;

    public static final FitnessSignalId DETERMINISTIC_CHECKS_GATE =
            FitnessSignalId.invariant("deterministic_checks");
    public static final FitnessSignalId REQUIRED_BEHAVIOR_CASES_GATE =
            FitnessSignalId.invariant("required_behavior_cases");
    public static final FitnessSignalId REQUIRED_OBJECTIVE_SCORES_GATE =
            FitnessSignalId.invariant("required_objective_scores");
    public static final FitnessSignalId NON_EMPTY_REALIZATION_GATE =
            FitnessSignalId.invariant("non_empty_realization");

    private static final double GATE_PASSED = 1.0;
    private static final double GATE_FAILED = 0.0;

    public FitnessResult score(Candidate candidate, PhenotypeEvidence phenotype) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(phenotype, "phenotype");

        // Absent evidence is not passing evidence: an empty check or behavior-case list fails its gate.
        boolean checksPassed = !phenotype.evidence().checks().isEmpty() && phenotype.evidence().checksPassed();
        boolean behaviorCasesPassed = !phenotype.behaviorCases().isEmpty()
                && phenotype.behaviorCases().stream().allMatch(c -> c.status() == CheckStatus.PASSED);
        boolean objectiveScoresPresent = hasEveryObjectiveScore(phenotype);
        // A candidate that changed no file has no behavioral variation to evaluate: its passing
        // checks are evidence about the baseline, and parsimony rewards the empty diff with 1.0.
        // Measured in files rather than lines, so a mode-only change still counts as a realization.
        boolean realizationNonEmpty = phenotype.realization().filesChanged() > 0;
        boolean gatesPassed =
                checksPassed && behaviorCasesPassed && objectiveScoresPresent && realizationNonEmpty;

        double rawScore = gatesPassed ? weightedScore(phenotype) : 0.0;
        FitnessDecision decision = gatesPassed && rawScore >= PROMOTION_THRESHOLD
                ? FitnessDecision.PROMOTE
                : FitnessDecision.DISCARD;

        // Gate outcomes are written after the measured scores so evidence content can never overwrite
        // a recorded gate result in the audit trail.
        Map<String, Double> objectives = new LinkedHashMap<>(phenotype.objectiveScores());
        objectives.put(DETERMINISTIC_CHECKS_GATE.canonical(), gateValue(checksPassed));
        objectives.put(REQUIRED_BEHAVIOR_CASES_GATE.canonical(), gateValue(behaviorCasesPassed));
        objectives.put(REQUIRED_OBJECTIVE_SCORES_GATE.canonical(), gateValue(objectiveScoresPresent));
        objectives.put(NON_EMPTY_REALIZATION_GATE.canonical(), gateValue(realizationNonEmpty));

        return new FitnessResult(candidate, phenotype.evidence(), objectives, round(rawScore), decision);
    }

    /**
     * Reads {@code DEFAULT_OBJECTIVES} rather than the operator's defaults because {@link #score} never
     * receives the contract, so it cannot know the operator. Safe only while every operator shares one
     * objective set, which {@code PhenotypeFitnessScorerTest} asserts. See RISK-002 and task T4b.
     */
    private static boolean hasEveryObjectiveScore(PhenotypeEvidence phenotype) {
        return MutationOperatorPolicy.DEFAULT_OBJECTIVES.stream()
                .map(FitnessObjective::id)
                .allMatch(id -> isFraction(phenotype.objectiveScores().get(id)));
    }

    private static boolean isFraction(Double score) {
        return score != null && Double.isFinite(score) && score >= 0.0 && score <= 1.0;
    }

    /** Weights come from {@code DEFAULT_OBJECTIVES} for the same reason as {@link #hasEveryObjectiveScore}. */
    private static double weightedScore(PhenotypeEvidence phenotype) {
        return MutationOperatorPolicy.DEFAULT_OBJECTIVES.stream()
                .mapToDouble(objective -> objective.weight() * phenotype.objectiveScores().get(objective.id()))
                .sum();
    }

    /** Rounds for reporting only. The threshold comparison uses the raw sum so 0.7950 cannot promote. */
    private static double round(double score) {
        return Math.round(score * 100.0) / 100.0;
    }

    private static double gateValue(boolean passed) {
        return passed ? GATE_PASSED : GATE_FAILED;
    }
}
