package com.dreamthought.saaa.deterministic;

import static com.dreamthought.saaa.domain.CheckEvidence.passed;
import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.domain.BenchmarkEvidence;
import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.FitnessDecision;
import com.dreamthought.saaa.domain.RealizationSummary;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * CHG-021. CON-002 says an invariant is binary for promote-or-discard but carries a magnitude, so
 * among candidates that already failed a near miss stays distinguishable from a total miss. Zeroing
 * the score on gate failure destroyed that magnitude, which is the information a population needs to
 * choose which failure to mutate from next.
 */
final class FailedCandidateMagnitudeTest {
    @Test
    void aNearMissOutscoresATotalMissAlthoughBothDiscard() {
        var nearMiss = score(3, 4);
        var totalMiss = score(1, 4);

        assertThat(nearMiss.decision()).isEqualTo(FitnessDecision.DISCARD);
        assertThat(totalMiss.decision()).isEqualTo(FitnessDecision.DISCARD);
        assertThat(nearMiss.fitnessScore().rawMagnitude())
                .as("three of four passing must outrank one of four, or a population cannot choose")
                .isGreaterThan(totalMiss.fitnessScore().rawMagnitude());
    }

    @Test
    void theDecisionIsStillBinaryAndUntradeable() {
        var nearMiss = score(3, 4);

        assertThat(nearMiss.fitnessScore().rawMagnitude().doubleValue())
                .as("a failing candidate can score above the promotion threshold")
                .isGreaterThan(PhenotypeFitnessScorer.PROMOTION_THRESHOLD);
        assertThat(nearMiss.decision())
                .as("and must still discard: a magnitude never buys a promotion")
                .isEqualTo(FitnessDecision.DISCARD);
    }

    /**
     * Retaining the magnitude made an unreachable path reachable. Zeroing on gate failure used to
     * discard a non-finite weighted sum before it was recorded; now the sum is always computed, and
     * {@code Math.round} launders it into something that passes every finiteness guard:
     * {@code round(+Inf)} is {@code 9.22e16} and finite, {@code round(NaN)} is {@code 0.0}. A
     * candidate carrying an infinite objective would therefore be stored with an enormous score and
     * would sort first among failures — the ranking this change exists to make trustworthy.
     *
     * <p>A value that is not a fraction is not a measurement, so it contributes nothing, which is the
     * same rule the required-objective-scores gate applies. The gate still fails and the candidate is
     * still discarded; the point is that the recorded magnitude stays meaningful.
     */
    @Test
    void aNonFiniteObjectiveCannotBecomeAnEnormousRecordedScore() {
        var result = scoreWith(Double.POSITIVE_INFINITY);

        assertThat(result.decision())
                .as("a non-finite objective fails the required-objective-scores gate")
                .isEqualTo(FitnessDecision.DISCARD);
        assertThat(result.fitnessScore().rawMagnitude())
                .as("the recorded magnitude must stay within the range a score can occupy")
                .isBetween(java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE);
    }

    @Test
    void aNotANumberObjectiveCannotPoisonTheRecordedScore() {
        var result = scoreWith(Double.NaN);

        assertThat(result.decision()).isEqualTo(FitnessDecision.DISCARD);
        assertThat(result.fitnessScore().rawMagnitude())
                .isBetween(java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE);
    }

    private static com.dreamthought.saaa.domain.FitnessResult scoreWith(double taskSuccess) {
        return new PhenotypeFitnessScorer().score(
                new Candidate("c-1", "MUT-1", "candidate/MUT-1", Path.of(".worktrees/c"), "abc1234"),
                new PhenotypeEvidence(
                        new EvaluationEvidence(List.of(passed("build", "ok")),
                                List.<BenchmarkEvidence>of(), Instant.parse("2026-08-23T00:00:00Z")),
                        List.of(BehaviorCaseEvidence.passed("case-0", "ok")),
                        Map.of("subject.objective.task_success", taskSuccess,
                                "subject.objective.reliability", 1.0,
                                "subject.objective.cost_latency_budget", 1.0,
                                "subject.objective.behavioral_safety", 1.0,
                                "subject.objective.parsimony", 0.975),
                        new RealizationSummary(1, 8)));
    }

    private static com.dreamthought.saaa.domain.FitnessResult score(int passing, int declared) {
        var cases = new java.util.ArrayList<BehaviorCaseEvidence>();
        for (int i = 0; i < declared; i++) {
            cases.add(i < passing
                    ? BehaviorCaseEvidence.passed("case-" + i, "ok")
                    : BehaviorCaseEvidence.failed("case-" + i, "failed"));
        }
        return new PhenotypeFitnessScorer().score(
                new Candidate("c-1", "MUT-1", "candidate/MUT-1", Path.of(".worktrees/c"), "abc1234"),
                new PhenotypeEvidence(
                        new EvaluationEvidence(List.of(passed("build", "ok")),
                                List.<BenchmarkEvidence>of(), Instant.parse("2026-08-23T00:00:00Z")),
                        cases,
                        Map.of("subject.objective.task_success", (double) passing / declared,
                                "subject.objective.reliability", 1.0,
                                "subject.objective.cost_latency_budget", 1.0,
                                "subject.objective.behavioral_safety", 1.0,
                                "subject.objective.parsimony", 0.975),
                        new RealizationSummary(1, 8)));
    }
}
