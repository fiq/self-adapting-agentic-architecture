package com.dreamthought.saaa.deterministic;

import static com.dreamthought.saaa.domain.CheckEvidence.failed;
import static com.dreamthought.saaa.domain.CheckEvidence.passed;
import static com.dreamthought.saaa.domain.FitnessDecision.DISCARD;
import static com.dreamthought.saaa.domain.FitnessDecision.PROMOTE;
import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.domain.BenchmarkEvidence;
import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.RealizationSummary;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class PhenotypeBridgeScorerTest {
    private static final Candidate CANDIDATE =
            new Candidate("cand-1", "MUT-1", "candidate/toy-MUT-1", Path.of("/tmp/wt"), "abc1234");

    @Test
    void derivesPhenotypeEvidenceAndHardGatesBeforeWeighting() {
        var scorer = scorer(new RealizationSummary(1, 8), 80);

        var result = scorer.score(CANDIDATE, new EvaluationEvidence(
                List.of(passed("build", "ok"), failed("publish-guard", "regressed")),
                List.of(),
                Instant.parse("2026-07-28T00:00:00Z")));

        assertThat(result.decision()).isEqualTo(DISCARD);
        assertThat(result.aggregateScore()).isZero();
        assertThat(result.objectives())
                .containsEntry(PhenotypeFitnessScorer.REQUIRED_BEHAVIOR_CASES_GATE.canonical(), 0.0);
    }

    @Test
    void promotesWhenBehaviourCasesPassAndTheChangeIsSmall() {
        var scorer = scorer(new RealizationSummary(1, 8), 80);

        var result = scorer.score(CANDIDATE, new EvaluationEvidence(
                List.of(passed("build", "ok"), passed("publish-guard", "ok")),
                List.of(),
                Instant.parse("2026-07-28T00:00:00Z")));

        assertThat(result.decision()).isEqualTo(PROMOTE);
        assertThat(result.objectives()).containsEntry("subject.objective.task_success", 1.0);
    }

    /**
     * The bridge filters evidence down to the declared behaviour case names, so a declared case
     * that produced no evidence at all would simply vanish and the gate would pass on the remaining
     * cases. The gate must fail closed instead: a required behaviour with no evidence has not been
     * shown to hold, whatever the caller wired up.
     */
    @Test
    void failsTheGateWhenADeclaredBehaviourCaseProducedNoEvidence() {
        var scorer = new PhenotypeBridgeScorer(
                candidate -> new RealizationSummary(1, 8),
                new ScoringConfig(Set.of("publish-guard", "never-ran"), 80, Map.of()));

        var result = scorer.score(CANDIDATE, new EvaluationEvidence(
                List.of(passed("build", "ok"), passed("publish-guard", "ok")),
                List.of(),
                Instant.parse("2026-07-28T00:00:00Z")));

        assertThat(result.decision()).isEqualTo(DISCARD);
        assertThat(result.objectives())
                .containsEntry(PhenotypeFitnessScorer.REQUIRED_BEHAVIOR_CASES_GATE.canonical(), 0.0);
    }

    /**
     * Two check entries can share a name. Keeping the last one seen would let a passing entry
     * overwrite a failing one, recording the behaviour gate as satisfied for a case that failed.
     * A failure for a name must win regardless of the order the evidence arrives in.
     */
    @Test
    void failsTheGateWhenADeclaredBehaviourCaseFailedInAnyOfItsCheckEntries() {
        var scorer = new PhenotypeBridgeScorer(
                candidate -> new RealizationSummary(1, 8),
                new ScoringConfig(Set.of("publish-guard"), 80, Map.of()));

        var result = scorer.score(CANDIDATE, new EvaluationEvidence(
                List.of(failed("publish-guard", "regressed"), passed("publish-guard", "ok")),
                List.of(),
                Instant.parse("2026-07-28T00:00:00Z")));

        assertThat(result.objectives())
                .containsEntry(PhenotypeFitnessScorer.REQUIRED_BEHAVIOR_CASES_GATE.canonical(), 0.0);
        assertThat(result.decision()).isEqualTo(DISCARD);
    }

    /**
     * Parsimony rewards a smaller diff, so a realization that changed nothing scores 1.0 on that
     * objective and every other objective is blind to it. The candidate must be gated out instead:
     * its passing checks are evidence about the baseline, not about a mutation.
     */
    @Test
    void discardsACandidateWhoseRealizationChangedNothing() {
        var scorer = scorer(new RealizationSummary(0, 0), 80);

        var result = scorer.score(CANDIDATE, new EvaluationEvidence(
                List.of(passed("build", "ok"), passed("publish-guard", "ok")),
                List.of(),
                Instant.parse("2026-07-28T00:00:00Z")));

        assertThat(result.objectives())
                .containsEntry(PhenotypeFitnessScorer.NON_EMPTY_REALIZATION_GATE.canonical(), 0.0);
        assertThat(result.decision()).isEqualTo(DISCARD);
    }

    @Test
    void scoresParsimonyFromRealizedDiffSizeAgainstBounds() {
        var tight = scorer(new RealizationSummary(1, 8), 80);
        var sprawling = scorer(new RealizationSummary(1, 72), 80);

        var evidence = new EvaluationEvidence(
                List.of(passed("build", "ok"), passed("publish-guard", "ok")),
                List.of(),
                Instant.parse("2026-07-28T00:00:00Z"));

        assertThat(tight.score(CANDIDATE, evidence).objectives())
                .containsEntry("subject.objective.parsimony", 0.9);
        // 1.0 - 72.0/80.0 is not exactly 0.1 in double arithmetic; 72.0/80.0 rounds to the nearest
        // double for 0.9, and 1.0 minus that is 0.09999999999999998, not 0.1.
        assertThat(sprawling.score(CANDIDATE, evidence).objectives())
                .containsEntry("subject.objective.parsimony", 0.09999999999999998);
        assertThat(tight.score(CANDIDATE, evidence).aggregateScore())
                .isGreaterThan(sprawling.score(CANDIDATE, evidence).aggregateScore());
    }

    @Test
    void scoresCostLatencyFromTheWorstBenchmarkAgainstItsBudget() {
        var scorer = new PhenotypeBridgeScorer(
                candidate -> new RealizationSummary(1, 8),
                new ScoringConfig(Set.of("publish-guard"), 80, Map.of("publish-latency", 50.0)));

        var result = scorer.score(CANDIDATE, new EvaluationEvidence(
                List.of(passed("build", "ok"), passed("publish-guard", "ok")),
                List.of(BenchmarkEvidence.measurement("publish-latency", 100.0, "ms")),
                Instant.parse("2026-07-28T00:00:00Z")));

        assertThat(result.objectives()).containsEntry("subject.objective.cost_latency_budget", 0.5);
    }

    @Test
    void doesNotTreatCandidateOutputContainingTimeoutWordsAsAnUnreliableCheck() {
        var scorer = scorer(new RealizationSummary(1, 8), 80);

        var result = scorer.score(CANDIDATE, new EvaluationEvidence(
                List.of(passed("publish-guard", "exit=0 output=timed out is mentioned here")),
                List.of(),
                Instant.parse("2026-07-28T00:00:00Z")));

        assertThat(result.objectives())
                .containsEntry("subject.objective.reliability", 1.0);
    }

    private static PhenotypeBridgeScorer scorer(RealizationSummary summary, int maxLinesChanged) {
        return new PhenotypeBridgeScorer(
                candidate -> summary,
                new ScoringConfig(Set.of("publish-guard"), maxLinesChanged, Map.of()));
    }
}
