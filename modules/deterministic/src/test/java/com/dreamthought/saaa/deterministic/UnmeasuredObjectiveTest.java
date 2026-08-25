package com.dreamthought.saaa.deterministic;

import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.CheckEvidence;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.FitnessDecision;
import com.dreamthought.saaa.domain.RealizationSummary;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * An objective with no evidence source contributes nothing, rather than contributing full marks.
 *
 * <p>Before this, a run declaring no safety probes and no benchmark budgets banked {@code 0.30} of
 * the weighted sum for measuring nothing: {@code safetyScore} returned {@code 1.0} with no probes
 * declared and {@code budgetScore} returned its {@code 1.0} starting point with no budgets. Against
 * a {@code 0.80} threshold that is more than a third of the way to a promotion awarded for absence.
 *
 * <p>The fix renormalises over the objectives a run actually measured. This changes the meaning of
 * every score, which is why it waited for {@code ScoringContext}: probes and budgets are both
 * fingerprinted, so scores produced under different measurement configurations are already refused
 * for comparison rather than silently mixed.
 */
final class UnmeasuredObjectiveTest {
    private static final PhenotypeFitnessScorer SCORER = new PhenotypeFitnessScorer();

    /**
     * The load-bearing case. With every measured objective perfect but parsimony at zero, the old
     * sum reached exactly the promotion threshold on the strength of two objectives nothing
     * measured. Renormalised, the same candidate is judged only on what was measured and discards.
     */
    @Test
    void aCandidateCannotReachTheThresholdOnUnmeasuredObjectives() {
        var result = SCORER.score(candidate(), phenotype(0.75, 0.0));

        assertThat(result.decision())
                .as("0.30 of weight awarded for measuring nothing must not carry a candidate")
                .isEqualTo(FitnessDecision.DISCARD);
    }

    /** A run that measures everything well still promotes; renormalising must not punish success. */
    @Test
    void aCandidateStrongOnEveryMeasuredObjectiveStillPromotes() {
        assertThat(SCORER.score(candidate(), phenotype(1.0, 0.975)).decision())
                .isEqualTo(FitnessDecision.PROMOTE);
    }

    /**
     * The renormalised magnitude is a fraction of the measured weight, not of the whole weight, so a
     * perfect measured run reads as 1.0 rather than as the 0.70 its raw contributions sum to.
     */
    @Test
    void aPerfectMeasuredRunScoresOneRatherThanTheMeasuredWeightTotal() {
        assertThat(SCORER.score(candidate(), phenotype(1.0, 1.0)).fitnessScore().rawMagnitude())
                .isEqualByComparingTo(new java.math.BigDecimal("1"));
    }

    private static PhenotypeEvidence phenotype(double taskSuccess, double parsimony) {
        var objectives = Map.of(
                "subject.objective.task_success", taskSuccess,
                "subject.objective.reliability", 1.0,
                "subject.objective.parsimony", parsimony);
        return new PhenotypeEvidence(
                new EvaluationEvidence(List.of(CheckEvidence.passed("gating", "held")), List.of(),
                        Instant.EPOCH),
                List.of(BehaviorCaseEvidence.passed("gating", "held")),
                objectives,
                new RealizationSummary(1, 1),
                Set.of(), Set.of(), Set.of("gating"), 80, Map.of(),
                // No probes and no budgets were declared, so these two measured nothing.
                Set.of("subject.objective.behavioral_safety", "subject.objective.cost_latency_budget"));
    }

    private static Candidate candidate() {
        return new Candidate("cand", "mut", "candidate/mut", Path.of(".worktrees/cand"), "abc1234");
    }

    /**
     * Found by an independent review: the first fix keyed on whether a budget was <em>declared</em>,
     * which left the defect alive in a narrower shape. {@code budgetScore} starts at {@code 1.0} and
     * only moves when a benchmark actually ran and matched a budget, so declaring a budget and
     * running no benchmark still scored full marks for measuring nothing.
     *
     * <p>Driven through the bridge rather than the scorer, because the bridge is what decides which
     * objectives had an evidence source.
     */
    @Test
    void aDeclaredBudgetWithNoBenchmarkThatRanIsStillUnmeasured() {
        var bridge = new PhenotypeBridgeScorer(
                ignored -> new RealizationSummary(1, 80),
                new ScoringConfig(Set.of("gating"), 80, Map.of("publish-latency", 50.0)));

        var result = bridge.score(candidate(), new EvaluationEvidence(
                List.of(CheckEvidence.passed("gating", "ok")), List.of(), Instant.EPOCH));

        // Nothing ran against the budget and the diff exhausts the line budget, so the measured
        // objectives are task_success, reliability and parsimony: 0.60 over a divisor of 0.70.
        // Counting the unmeasured budget would add 0.20 to both, giving 0.80 over 0.90 - a higher
        // score for having measured strictly less.
        assertThat(result.fitnessScore().rawMagnitude())
                .as("a budget nothing was measured against must not inflate the score")
                .isLessThan(new java.math.BigDecimal("0.87"));
    }
}
