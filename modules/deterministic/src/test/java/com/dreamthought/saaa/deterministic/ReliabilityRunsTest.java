package com.dreamthought.saaa.deterministic;

import static com.dreamthought.saaa.domain.CheckEvidence.failed;
import static com.dreamthought.saaa.domain.CheckEvidence.passed;
import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.domain.BenchmarkEvidence;
import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.CheckEvidence;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.FitnessDecision;
import com.dreamthought.saaa.domain.FitnessResult;
import com.dreamthought.saaa.domain.RealizationSummary;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * CHG-022. The reliability objective asked only whether nothing timed out, which no candidate that
 * cleared the deterministic-checks gate could ever fail: a timed-out check is not a passed check, so
 * the gate had already discarded it. The objective restated its own gate and sat at 1.0 for every
 * candidate that promoted, pinning a fifth of the weight at a constant.
 *
 * <p>Repeated runs give it something to measure that the gate does not see. The canonical run decides
 * eligibility; the repeats say how reliably that result holds.
 */
final class ReliabilityRunsTest {
    @Test
    void aFlakyCandidateScoresLowerThanAConsistentOneAndBothStillPromote() {
        var flaky = score(4, passed("unit_tests_pass", "ok"),
                passed("unit_tests_pass.run2", "ok"),
                failed("unit_tests_pass.run3", "flaked"),
                passed("unit_tests_pass.run4", "ok"));
        var consistent = score(4, passed("unit_tests_pass", "ok"),
                passed("unit_tests_pass.run2", "ok"),
                passed("unit_tests_pass.run3", "ok"),
                passed("unit_tests_pass.run4", "ok"));

        assertThat(flaky.decision())
                .as("a repeat run grades, so flakiness must not discard a candidate that met the gate")
                .isEqualTo(FitnessDecision.PROMOTE);
        assertThat(consistent.decision()).isEqualTo(FitnessDecision.PROMOTE);
        assertThat(flaky.objectives())
                .containsEntry("subject.objective.reliability", 0.75);
        assertThat(consistent.objectives())
                .containsEntry("subject.objective.reliability", 1.0);
        assertThat(flaky.aggregateScore())
                .as("this is the point: two candidates that both promote no longer score the same")
                .isLessThan(consistent.aggregateScore());
    }

    /**
     * The repeats must not reach the deterministic-checks gate. If they did, one flaky run would
     * discard the candidate rather than lower its score, which is the trap that kept this objective
     * pinned in the first place.
     */
    @Test
    void aFailedRepeatRunDoesNotFailTheChecksGate() {
        var result = score(2, passed("unit_tests_pass", "ok"),
                failed("unit_tests_pass.run2", "flaked"));

        assertThat(result.objectives())
                .containsEntry(PhenotypeFitnessScorer.DETERMINISTIC_CHECKS_GATE.canonical(), 1.0);
        assertThat(result.decision()).isEqualTo(FitnessDecision.PROMOTE);
    }

    /** The failing run is still evidence, or a lowered score could not be explained afterwards. */
    @Test
    void theFailingRepeatRunStaysInTheRecordedEvidence() {
        var result = score(2, passed("unit_tests_pass", "ok"),
                failed("unit_tests_pass.run2", "flaked"));

        assertThat(result.evidence().checks())
                .extracting(CheckEvidence::name)
                .contains("unit_tests_pass.run2");
    }

    /** A caller who declares no repeats must score exactly as before, timeout rule included. */
    @Test
    void asingleRunKeepsTheObjectiveUnchanged() {
        var result = score(1, passed("unit_tests_pass", "ok"));

        assertThat(result.objectives()).containsEntry("subject.objective.reliability", 1.0);
    }

    private static FitnessResult score(int runs, CheckEvidence... checks) {
        var scorer = new PhenotypeBridgeScorer(
                candidate -> new RealizationSummary(1, 8),
                new ScoringConfig(Set.of("unit_tests_pass"), 80, Map.of(), Set.of(), runs));
        return scorer.score(
                new Candidate("c-1", "MUT-1", "candidate/MUT-1", Path.of(".worktrees/c"), "abc1234"),
                new EvaluationEvidence(List.of(checks), List.<BenchmarkEvidence>of(),
                        Instant.parse("2026-08-23T00:00:00Z")),
                Optional.empty());
    }
}
