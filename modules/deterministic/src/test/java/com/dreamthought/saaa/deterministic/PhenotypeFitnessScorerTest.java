package com.dreamthought.saaa.deterministic;

import static com.dreamthought.saaa.domain.CheckEvidence.failed;
import static com.dreamthought.saaa.domain.CheckEvidence.passed;
import static com.dreamthought.saaa.domain.FitnessDecision.DISCARD;
import static com.dreamthought.saaa.domain.FitnessDecision.PROMOTE;
import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.domain.BenchmarkEvidence;
import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class PhenotypeFitnessScorerTest {
    private final PhenotypeFitnessScorer scorer = new PhenotypeFitnessScorer();

    @Test
    void appliesHardGatesBeforeWeightedObjectives() {
        var result = scorer.score(candidate(), new PhenotypeEvidence(
                evidence(),
                List.of(
                        BehaviorCaseEvidence.passed("renders-draft", "draft pages render"),
                        BehaviorCaseEvidence.failed("publishes-review", "review transition regressed")
                ),
                perfectObjectiveScores()
        ));

        assertThat(result.decision()).isEqualTo(DISCARD);
        assertThat(result.aggregateScore()).isZero();
    }

    @Test
    void requiresAllRequiredBehaviorCasesBeforePromotion() {
        var result = scorer.score(candidate(), new PhenotypeEvidence(
                new EvaluationEvidence(
                        List.of(passed("gradle-test", "all deterministic checks passed")),
                        List.of(BenchmarkEvidence.measurement("publish-latency", 42.0, "ms")),
                        Instant.parse("2026-07-27T00:00:00Z")
                ),
                List.of(BehaviorCaseEvidence.failed("required-cms-flow", "required behavior failed")),
                perfectObjectiveScores()
        ));

        assertThat(result.decision()).isEqualTo(DISCARD);
        assertThat(result.objectives()).containsEntry("hard_gate_required_behavior_cases", 0.0);
    }

    @Test
    void promotesWhenHardGatesPassAndWeightedObjectivesMeetThreshold() {
        var result = scorer.score(candidate(), new PhenotypeEvidence(
                evidence(),
                List.of(
                        BehaviorCaseEvidence.passed("renders-draft", "draft pages render"),
                        BehaviorCaseEvidence.passed("publishes-review", "review transition works")
                ),
                Map.of(
                        "task_success", 0.90,
                        "reliability", 0.90,
                        "cost_latency_budget", 0.80,
                        "behavioral_safety", 1.00,
                        "parsimony", 0.70
                )
        ));

        assertThat(result.decision()).isEqualTo(PROMOTE);
        assertThat(result.aggregateScore()).isEqualTo(0.87);
    }

    @Test
    void treatsAbsentDeterministicCheckEvidenceAsAFailedGate() {
        var result = scorer.score(candidate(), new PhenotypeEvidence(
                new EvaluationEvidence(List.of(), List.of(), Instant.parse("2026-07-27T00:00:00Z")),
                List.of(BehaviorCaseEvidence.passed("renders-draft", "draft pages render")),
                perfectObjectiveScores()
        ));

        assertThat(result.decision()).isEqualTo(DISCARD);
        assertThat(result.aggregateScore()).isZero();
        assertThat(result.objectives())
                .containsEntry(PhenotypeFitnessScorer.DETERMINISTIC_CHECKS_GATE, 0.0);
    }

    @Test
    void keepsRecordedGateOutcomesWhenEvidenceSuppliesGateKeys() {
        Map<String, Double> forged = new HashMap<>(perfectObjectiveScores());
        forged.put(PhenotypeFitnessScorer.DETERMINISTIC_CHECKS_GATE, 1.0);

        var result = scorer.score(candidate(), new PhenotypeEvidence(
                new EvaluationEvidence(
                        List.of(failed("gradle-test", "deterministic checks failed")),
                        List.of(BenchmarkEvidence.measurement("publish-latency", 42.0, "ms")),
                        Instant.parse("2026-07-27T00:00:00Z")
                ),
                List.of(BehaviorCaseEvidence.passed("renders-draft", "draft pages render")),
                forged
        ));

        assertThat(result.decision()).isEqualTo(DISCARD);
        assertThat(result.objectives())
                .containsEntry(PhenotypeFitnessScorer.DETERMINISTIC_CHECKS_GATE, 0.0);
    }

    @Test
    void comparesTheRawScoreAgainstTheThresholdRatherThanTheReportedRounding() {
        var result = scorer.score(candidate(), new PhenotypeEvidence(
                evidence(),
                List.of(BehaviorCaseEvidence.passed("renders-draft", "draft pages render")),
                Map.of(
                        "task_success", 1.00,
                        "reliability", 1.00,
                        "cost_latency_budget", 0.975,
                        "behavioral_safety", 0.00,
                        "parsimony", 0.00
                )
        ));

        assertThat(result.aggregateScore()).isEqualTo(0.80);
        assertThat(result.decision()).isEqualTo(DISCARD);
    }

    private static Map<String, Double> perfectObjectiveScores() {
        return Map.of(
                "task_success", 1.0,
                "reliability", 1.0,
                "cost_latency_budget", 1.0,
                "behavioral_safety", 1.0,
                "parsimony", 1.0
        );
    }

    private static Candidate candidate() {
        return new Candidate(
                "cand-001",
                "MUT-001",
                "candidate/MUT-001",
                Path.of(".worktrees/candidate-MUT-001"),
                "abc1234"
        );
    }

    private static EvaluationEvidence evidence() {
        return new EvaluationEvidence(
                List.of(passed("gradle-test", "all deterministic checks passed")),
                List.of(BenchmarkEvidence.measurement("publish-latency", 42.0, "ms")),
                Instant.parse("2026-07-27T00:00:00Z")
        );
    }
}
