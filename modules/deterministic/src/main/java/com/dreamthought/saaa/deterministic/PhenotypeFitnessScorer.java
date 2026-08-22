package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.CheckStatus;
import com.dreamthought.saaa.domain.FitnessDecision;
import com.dreamthought.saaa.domain.FitnessObjective;
import com.dreamthought.saaa.domain.FitnessResult;
import com.dreamthought.saaa.domain.FitnessSignalId;
import com.dreamthought.saaa.domain.MutationContract;
import com.dreamthought.saaa.domain.RequiredEvidenceResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Deterministic, evidence-only scoring of a candidate phenotype.
 *
 * <p>Hard gates run first and are not tradeable: a candidate that fails one is discarded no matter
 * how good its weighted objectives look. It still keeps its weighted score, so a near miss stays
 * distinguishable from a total failure in the record; the gate decides fate, the score only ranks.
 * Read {@code decision} to know what happened to a candidate, never the score alone.
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

    /**
     * The audit keys the structural gates own. An evidence id canonicalises through the same
     * {@link FitnessSignalId#invariant} scheme, so an id such as {@code deterministic_checks} would
     * land on exactly the key a structural gate already wrote. Silently merging would let evidence
     * report a passing structural gate for a candidate whose checks failed, which is the audit
     * corruption CON-002 exists to prevent, so a collision is rejected instead.
     */
    /** The bare names the structural gates own, so a caller can reject a colliding declared id. */
    public static final Set<String> STRUCTURAL_GATE_NAMES = Set.of(
            "deterministic_checks", "required_behavior_cases", "required_objective_scores",
            "non_empty_realization");

    private static final Set<String> STRUCTURAL_GATE_KEYS = Set.of(
            DETERMINISTIC_CHECKS_GATE.canonical(),
            REQUIRED_BEHAVIOR_CASES_GATE.canonical(),
            REQUIRED_OBJECTIVE_SCORES_GATE.canonical(),
            NON_EMPTY_REALIZATION_GATE.canonical());

    /**
     * Scores without a contract. This is the entry point the wired path reaches through
     * {@code FitnessScorer}, whose port carries no contract, so a declared {@code required_evidence}
     * id cannot be enforced here. Its behaviour is characterised by
     * {@code PhenotypeFitnessScorerTest.contractlessScoringPreservesTheExistingGates} and must not
     * drift while the contract-aware path exists alongside it. See RISK-002.
     */
    public FitnessResult score(Candidate candidate, PhenotypeEvidence phenotype) {
        return score(candidate, phenotype, null, List.of());
    }

    /**
     * Scores against the contract the candidate was accepted under. Declared
     * {@code required_evidence} ids gate <em>in addition to</em> the structural gates, never instead
     * of them: a candidate whose declared evidence all passes but which changed no file is still
     * discarded.
     *
     * <p>A declared id with no observed result is a discard, because absent evidence is not passing
     * evidence. Two results for one declared id resolve fail-wins, so a passing entry can never mask
     * a failing one. Results for ids the contract did not declare are recorded but cannot satisfy or
     * weaken a declared gate.
     */
    public FitnessResult score(
            Candidate candidate,
            PhenotypeEvidence phenotype,
            MutationContract contract,
            List<RequiredEvidenceResult> requiredEvidence) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(phenotype, "phenotype");
        Objects.requireNonNull(requiredEvidence, "requiredEvidence");

        // Absent evidence is not passing evidence: an empty check or behavior-case list fails its gate.
        // Checks withheld for grading are excluded here and only here; they stay in the recorded evidence.
        var gatingChecks = phenotype.gatingChecks();
        boolean checksPassed = !gatingChecks.isEmpty()
                && gatingChecks.stream().allMatch(check -> check.status() == CheckStatus.PASSED);
        boolean behaviorCasesPassed = !phenotype.behaviorCases().isEmpty()
                && phenotype.behaviorCases().stream().allMatch(c -> c.status() == CheckStatus.PASSED);
        List<FitnessObjective> objectiveSet = contract == null
                ? MutationOperatorPolicy.DEFAULT_OBJECTIVES
                : contract.objectives();
        boolean objectiveScoresPresent = hasEveryObjectiveScore(phenotype, objectiveSet);
        // A candidate that changed no file has no behavioral variation to evaluate: its passing
        // checks are evidence about the baseline, and parsimony rewards the empty diff with 1.0.
        // Measured in files rather than lines, so a mode-only change still counts as a realization.
        boolean realizationNonEmpty = phenotype.realization().filesChanged() > 0;
        List<String> declaredIds = contract == null ? List.of() : contract.requiredEvidence();
        Stream.concat(declaredIds.stream(), requiredEvidence.stream().map(RequiredEvidenceResult::evidenceId))
                .map(id -> FitnessSignalId.invariant(id).canonical())
                .filter(STRUCTURAL_GATE_KEYS::contains)
                .findFirst()
                .ifPresent(key -> {
                    throw new IllegalArgumentException(
                            "required evidence id collides with a structural gate: " + key);
                });

        // Fail wins for a declared id, mirroring how behaviour-case checks are merged: keeping the
        // last result seen would let a passing entry hide a failing one for the same declared id.
        Map<String, Boolean> observed = new LinkedHashMap<>();
        for (RequiredEvidenceResult result : requiredEvidence) {
            observed.merge(result.evidenceId(), result.passed(), (first, second) -> first && second);
        }
        List<String> declared = declaredIds;
        // Absent is not passing: a declared id with no observed result fails its gate.
        boolean declaredEvidencePassed = declared.stream()
                .allMatch(id -> Boolean.TRUE.equals(observed.get(id)));

        boolean gatesPassed = checksPassed && behaviorCasesPassed && objectiveScoresPresent
                && realizationNonEmpty && declaredEvidencePassed;

        // The magnitude survives a gate failure. CON-002 makes an invariant binary for the
        // promote-or-discard decision while still carrying a magnitude, so that among candidates
        // which already failed a near miss stays distinguishable from a total miss. Zeroing here
        // destroyed exactly that, which is the information a population needs to choose which
        // failure to mutate from next. The decision below is unchanged and still gated.
        double rawScore = weightedScore(phenotype, objectiveSet);
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
        // Undeclared results are recorded before declared gates so an observation the contract never
        // asked for can never overwrite a declared gate outcome.
        observed.forEach((id, passed) -> {
            if (!declared.contains(id)) {
                objectives.put(FitnessSignalId.invariant(id).canonical(), gateValue(passed));
            }
        });
        for (String id : declared) {
            objectives.put(FitnessSignalId.invariant(id).canonical(),
                    gateValue(Boolean.TRUE.equals(observed.get(id))));
        }

        return new FitnessResult(candidate, phenotype.evidence(), objectives, round(rawScore), decision);
    }

    /**
     * Reads the objective set the caller supplied: the contract's own on the contract-aware path, and
     * {@code DEFAULT_OBJECTIVES} on the contractless one, which cannot know the operator. The
     * contractless case is safe only while every operator shares one objective set, which
     * {@code PhenotypeFitnessScorerTest.everyOperatorSharesTheObjectiveSetTheScorerAssumes} asserts.
     * That tripwire stays until the wired path becomes contract-aware. See RISK-002 and CHG-002 T4b.
     *
     * <p>Presence and weighting read the same set deliberately. Taking one from the contract and the
     * other from the global would become incoherent the moment
     * {@code MutationContractValidator.requireDeterministicObjectives} is relaxed to permit a
     * per-operator objective set.
     */
    private static boolean hasEveryObjectiveScore(
            PhenotypeEvidence phenotype, List<FitnessObjective> objectiveSet) {
        return objectiveSet.stream()
                .map(FitnessObjective::id)
                .allMatch(id -> isFraction(phenotype.objectiveScores().get(id)));
    }

    private static boolean isFraction(Double score) {
        return score != null && Double.isFinite(score) && score >= 0.0 && score <= 1.0;
    }

    /** Weights come from the same objective set the presence gate used, for the reason given there. */
    private static double weightedScore(PhenotypeEvidence phenotype, List<FitnessObjective> objectiveSet) {
        return objectiveSet.stream()
                // A missing measurement contributes nothing rather than throwing. Before the
                // magnitude was retained, the missing-objective gate short-circuited to 0.0 and
                // this was never reached with an absent key; now it is.
                .mapToDouble(objective -> objective.weight()
                        * phenotype.objectiveScores().getOrDefault(objective.id(), 0.0))
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
