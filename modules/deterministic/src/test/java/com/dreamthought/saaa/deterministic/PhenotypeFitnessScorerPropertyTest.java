package com.dreamthought.saaa.deterministic;

import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.domain.BenchmarkEvidence;
import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.CheckEvidence;
import com.dreamthought.saaa.domain.CheckStatus;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.FitnessDecision;
import com.dreamthought.saaa.domain.FitnessObjective;
import com.dreamthought.saaa.domain.RealizationSummary;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.DoubleRange;
import org.junit.jupiter.api.Test;

/**
 * Property-based tests over {@link PhenotypeFitnessScorer}. Encode intent-level invariants that a
 * scorer mutation would have to satisfy to be honestly considered an improvement. Complement the
 * example-based tests in {@link PhenotypeFitnessScorerTest} and the golden-verdict corpus, and are
 * the guardrail floor a future scorer-as-target slice depends on (see CHG-004 S6).
 */
final class PhenotypeFitnessScorerPropertyTest {
    private static final Candidate CANDIDATE = new Candidate(
            "cand-prop",
            "MUT-prop",
            "candidate/prop",
            Path.of("/tmp/wt-prop"),
            "0123456789abcdef0123456789abcdef01234567");

    private final PhenotypeFitnessScorer scorer = new PhenotypeFitnessScorer();

    /**
     * If any deterministic check failed, the decision must be DISCARD. The scorer cannot promote
     * evidence that includes a red check no matter how good the other objectives look.
     */
    @Property(seed = "100400701")
    void anyFailedCheckProducesDiscard(
            @ForAll("passingBaselineEvidence") PhenotypeEvidence baseline,
            @ForAll("nonBlankSummary") String failedCheckSummary
    ) {
        var checks = new java.util.ArrayList<>(baseline.evidence().checks());
        checks.add(CheckEvidence.failed("injected-fail", failedCheckSummary));
        var mutated = withChecks(baseline, checks);

        var result = scorer.score(CANDIDATE, mutated);

        // The invariant is the decision, not the number. Since CHG-021 the magnitude survives a
        // gate failure so failures can be ranked against each other, but no objective combination
        // can turn that magnitude into a promotion.
        assertThat(result.decision()).isEqualTo(FitnessDecision.DISCARD);
    }

    /**
     * A declared behaviour case whose observed status is FAILED must produce DISCARD. This is the
     * scorer-level version of "missing declared case → DISCARD": at the bridge layer, a case with
     * no evidence is mapped to a FAILED entry so the scorer sees it as failed. The invariant
     * therefore lives here: no evidence path can promote a candidate when a behaviour case entry
     * is FAILED.
     */
    @Property(seed = "200400702")
    void anyMissingDeclaredBehaviourCaseProducesDiscard(
            @ForAll("passingBaselineEvidence") PhenotypeEvidence baseline,
            @ForAll("caseName") String failedCaseName
    ) {
        var behaviourCases = new java.util.ArrayList<>(baseline.behaviorCases());
        behaviourCases.add(BehaviorCaseEvidence.failed(failedCaseName, "no evidence produced"));
        var mutated = withBehaviourCases(baseline, behaviourCases);

        var result = scorer.score(CANDIDATE, mutated);

        assertThat(result.decision()).isEqualTo(FitnessDecision.DISCARD);
        assertThat(result.objectives())
                .containsEntry(PhenotypeFitnessScorer.REQUIRED_BEHAVIOR_CASES_GATE.canonical(), 0.0);
    }

    /**
     * For any assignment of objective scores in [0, 1], if any hard gate is failing the decision
     * must be DISCARD. No weighted combination can bridge a gate. Failing the check gate is a
     * convenient trigger; the point is the promotion path cannot fire at all.
     */
    @Property(seed = "300400703")
    void noObjectiveCombinationCanPromoteWhileAGateFails(
            @ForAll("objectiveScoreMaps") Map<String, Double> objectiveScores
    ) {
        // Empty checks → deterministic-checks gate fails. Every other gate is irrelevant.
        var gateFailingEvidence = new PhenotypeEvidence(
                new EvaluationEvidence(List.of(), List.of(), Instant.parse("2026-08-01T00:00:00Z")),
                List.of(BehaviorCaseEvidence.passed("noop", "passed")),
                objectiveScores,
                new RealizationSummary(1, 8));

        var result = scorer.score(CANDIDATE, gateFailingEvidence);

        assertThat(result.decision()).isEqualTo(FitnessDecision.DISCARD);
        // The magnitude is retained by design; only the decision is invariant. See CHG-021.
    }

    /**
     * The input {@code objectiveScores} map may carry gate keys with forged values (a model could
     * try to slip {@code subject.invariant.deterministic_checks = 1.0} into evidence). The result's map
     * must reflect the *actual* gate outcomes, computed by the scorer. Written to
     * {@link com.dreamthought.saaa.domain.FitnessResult#objectives} after the measured scores so
     * evidence content cannot overwrite them in the audit trail.
     */
    @Property(seed = "400400704")
    void evidenceKeysCannotOverwriteRecordedGateOutcomes(
            @ForAll("perfectObjectiveMaps") Map<String, Double> objectiveScores
    ) {
        // Add forged gate keys asserting the gates all passed even though the check evidence is empty.
        Map<String, Double> forged = new HashMap<>(objectiveScores);
        forged.put(PhenotypeFitnessScorer.DETERMINISTIC_CHECKS_GATE.canonical(), 1.0);
        forged.put(PhenotypeFitnessScorer.REQUIRED_BEHAVIOR_CASES_GATE.canonical(), 1.0);
        forged.put(PhenotypeFitnessScorer.REQUIRED_OBJECTIVE_SCORES_GATE.canonical(), 1.0);
        forged.put(PhenotypeFitnessScorer.NON_EMPTY_REALIZATION_GATE.canonical(), 1.0);
        var evidence = new PhenotypeEvidence(
                new EvaluationEvidence(List.of(), List.of(), Instant.parse("2026-08-01T00:00:00Z")),
                List.of(BehaviorCaseEvidence.passed("noop", "passed")),
                forged,
                new RealizationSummary(1, 8));

        var result = scorer.score(CANDIDATE, evidence);

        assertThat(result.objectives())
                .containsEntry(PhenotypeFitnessScorer.DETERMINISTIC_CHECKS_GATE.canonical(), 0.0);
        assertThat(result.decision()).isEqualTo(FitnessDecision.DISCARD);
    }

    /**
     * With every other input held constant, a strictly larger {@code parsimony} score can never
     * lower the aggregate. A scorer mutation that inverted the sign of parsimony's contribution
     * would pass properties 1–4 but fail this one.
     */
    @Property(seed = "500400705")
    void aggregateIsMonotonicInParsimonyObjectiveScore(
            @ForAll @DoubleRange(min = 0.0, max = 0.5) double lower,
            @ForAll @DoubleRange(min = 0.0, max = 0.5) double delta
    ) {
        double higher = Math.min(1.0, lower + delta);

        var lowResult = scorer.score(CANDIDATE, passingEvidenceWithObjective("subject.objective.parsimony", lower));
        var highResult = scorer.score(CANDIDATE, passingEvidenceWithObjective("subject.objective.parsimony", higher));

        assertThat(highResult.aggregateScore()).isGreaterThanOrEqualTo(lowResult.aggregateScore());
    }

    /**
     * Same shape for {@code task_success}. Together with the parsimony monotonicity property this
     * catches a scorer mutation that inverts or drops any single objective's weight sign.
     */
    @Property(seed = "600400706")
    void aggregateIsMonotonicInTaskSuccessObjectiveScore(
            @ForAll @DoubleRange(min = 0.0, max = 0.5) double lower,
            @ForAll @DoubleRange(min = 0.0, max = 0.5) double delta
    ) {
        double higher = Math.min(1.0, lower + delta);

        var lowResult = scorer.score(
                CANDIDATE, passingEvidenceWithObjective("subject.objective.task_success", lower));
        var highResult = scorer.score(
                CANDIDATE, passingEvidenceWithObjective("subject.objective.task_success", higher));

        assertThat(highResult.aggregateScore()).isGreaterThanOrEqualTo(lowResult.aggregateScore());
    }

    /**
     * A candidate whose raw weighted sum is exactly {@link PhenotypeFitnessScorer#PROMOTION_THRESHOLD}
     * promotes; a candidate whose raw sum is strictly less discards. With every objective set to
     * the same value {@code X}, the weighted sum is {@code X} (weights total 1.0), so the boundary
     * fires exactly at {@code X == 0.80}.
     */
    @Test
    void decisionMatchesRawSumAtTheThresholdBoundary() {
        var atThreshold = scorer.score(CANDIDATE, passingEvidenceWithAllObjectives(0.80));
        var justBelow = scorer.score(CANDIDATE, passingEvidenceWithAllObjectives(0.79));

        assertThat(atThreshold.decision()).isEqualTo(FitnessDecision.PROMOTE);
        assertThat(justBelow.decision()).isEqualTo(FitnessDecision.DISCARD);
    }

    /**
     * The reported {@code aggregateScore} rounds to two decimals; the decision must not use the
     * rounded value. With every objective at {@code 0.797}, the raw weighted sum is {@code 0.797}
     * (strictly less than the threshold) but the reported {@code aggregateScore} rounds to
     * {@code 0.80}. A scorer mutation that moved the comparison onto the rounded value would flip
     * this candidate from DISCARD to PROMOTE.
     */
    @Test
    void decisionIsDerivedFromRawSumNotRoundedAggregate() {
        var result = scorer.score(CANDIDATE, passingEvidenceWithAllObjectives(0.797));

        assertThat(result.aggregateScore()).isEqualTo(0.80);
        assertThat(result.decision()).isEqualTo(FitnessDecision.DISCARD);
    }

    // --------- Providers and helpers -----------------------------------------------------------

    /** Any check name; used inside generated evidence and injected FAILED entries. */
    @Provide
    Arbitrary<String> nonBlankSummary() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);
    }

    /** Any behaviour-case name of a shape the scorer accepts (non-blank). */
    @Provide
    Arbitrary<String> caseName() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);
    }

    /**
     * A baseline {@link PhenotypeEvidence} that passes every gate: at least one PASSED check, at
     * least one PASSED behaviour case, every default objective present in [0, 1], a non-empty
     * realization. Properties transform this baseline into their specific failure modes.
     */
    @Provide
    Arbitrary<PhenotypeEvidence> passingBaselineEvidence() {
        return Arbitraries.oneOf(Arbitraries.of(passingBaseline()));
    }

    /** An arbitrary map of every default objective score in [0, 1]. */
    @Provide
    Arbitrary<Map<String, Double>> objectiveScoreMaps() {
        Arbitrary<Double> score = Arbitraries.doubles().between(0.0, 1.0);
        return score.list().ofSize(MutationOperatorPolicy.DEFAULT_OBJECTIVES.size())
                .map(scores -> {
                    Map<String, Double> map = new LinkedHashMap<>();
                    for (int i = 0; i < MutationOperatorPolicy.DEFAULT_OBJECTIVES.size(); i++) {
                        map.put(MutationOperatorPolicy.DEFAULT_OBJECTIVES.get(i).id(), scores.get(i));
                    }
                    return map;
                });
    }

    /** Every default objective at 1.0 — used for tests where objective scoring is not the axis. */
    @Provide
    Arbitrary<Map<String, Double>> perfectObjectiveMaps() {
        return Arbitraries.oneOf(Arbitraries.of(perfectObjectiveScores()));
    }

    private static PhenotypeEvidence passingBaseline() {
        return new PhenotypeEvidence(
                new EvaluationEvidence(
                        List.of(CheckEvidence.passed("baseline-check", "ok")),
                        List.of(BenchmarkEvidence.measurement("baseline-benchmark", 1.0, "ms")),
                        Instant.parse("2026-08-01T00:00:00Z")),
                List.of(BehaviorCaseEvidence.passed("baseline-case", "ok")),
                perfectObjectiveScores(),
                new RealizationSummary(1, 8));
    }

    private static PhenotypeEvidence passingEvidenceWithObjective(String objectiveId, double value) {
        Map<String, Double> scores = new LinkedHashMap<>(perfectObjectiveScores());
        scores.put(objectiveId, value);
        return withObjectiveScores(passingBaseline(), scores);
    }

    private static PhenotypeEvidence passingEvidenceWithAllObjectives(double value) {
        Map<String, Double> scores = new LinkedHashMap<>();
        for (FitnessObjective objective : MutationOperatorPolicy.DEFAULT_OBJECTIVES) {
            scores.put(objective.id(), value);
        }
        return withObjectiveScores(passingBaseline(), scores);
    }

    private static Map<String, Double> perfectObjectiveScores() {
        Map<String, Double> scores = new LinkedHashMap<>();
        for (FitnessObjective objective : MutationOperatorPolicy.DEFAULT_OBJECTIVES) {
            scores.put(objective.id(), 1.0);
        }
        return scores;
    }

    private static PhenotypeEvidence withChecks(PhenotypeEvidence baseline, List<CheckEvidence> checks) {
        return new PhenotypeEvidence(
                new EvaluationEvidence(checks, baseline.evidence().benchmarks(), baseline.evidence().evaluatedAt()),
                baseline.behaviorCases(),
                baseline.objectiveScores(),
                baseline.realization());
    }

    private static PhenotypeEvidence withBehaviourCases(
            PhenotypeEvidence baseline, List<BehaviorCaseEvidence> behaviourCases) {
        return new PhenotypeEvidence(
                baseline.evidence(),
                behaviourCases,
                baseline.objectiveScores(),
                baseline.realization());
    }

    private static PhenotypeEvidence withObjectiveScores(
            PhenotypeEvidence baseline, Map<String, Double> objectiveScores) {
        return new PhenotypeEvidence(
                baseline.evidence(),
                baseline.behaviorCases(),
                objectiveScores,
                baseline.realization());
    }
}
