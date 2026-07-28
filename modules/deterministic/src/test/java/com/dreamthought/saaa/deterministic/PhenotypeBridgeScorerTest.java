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
                .containsEntry(PhenotypeFitnessScorer.REQUIRED_BEHAVIOR_CASES_GATE, 0.0);
    }

    @Test
    void promotesWhenBehaviourCasesPassAndTheChangeIsSmall() {
        var scorer = scorer(new RealizationSummary(1, 8), 80);

        var result = scorer.score(CANDIDATE, new EvaluationEvidence(
                List.of(passed("build", "ok"), passed("publish-guard", "ok")),
                List.of(),
                Instant.parse("2026-07-28T00:00:00Z")));

        assertThat(result.decision()).isEqualTo(PROMOTE);
        assertThat(result.objectives()).containsEntry("task_success", 1.0);
    }

    @Test
    void scoresParsimonyFromRealizedDiffSizeAgainstBounds() {
        var tight = scorer(new RealizationSummary(1, 8), 80);
        var sprawling = scorer(new RealizationSummary(1, 72), 80);

        var evidence = new EvaluationEvidence(
                List.of(passed("build", "ok"), passed("publish-guard", "ok")),
                List.of(),
                Instant.parse("2026-07-28T00:00:00Z"));

        assertThat(tight.score(CANDIDATE, evidence).objectives()).containsEntry("parsimony", 0.9);
        // 1.0 - 72.0/80.0 is not exactly 0.1 in double arithmetic; 72.0/80.0 rounds to the nearest
        // double for 0.9, and 1.0 minus that is 0.09999999999999998, not 0.1.
        assertThat(sprawling.score(CANDIDATE, evidence).objectives())
                .containsEntry("parsimony", 0.09999999999999998);
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

        assertThat(result.objectives()).containsEntry("cost_latency_budget", 0.5);
    }

    private static PhenotypeBridgeScorer scorer(RealizationSummary summary, int maxLinesChanged) {
        return new PhenotypeBridgeScorer(
                candidate -> summary,
                new ScoringConfig(Set.of("publish-guard"), maxLinesChanged, Map.of()));
    }
}
