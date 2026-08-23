package com.dreamthought.saaa.deterministic;

import static com.dreamthought.saaa.domain.CheckEvidence.failed;
import static com.dreamthought.saaa.domain.CheckEvidence.passed;
import static com.dreamthought.saaa.domain.FitnessDecision.DISCARD;
import static com.dreamthought.saaa.domain.FitnessDecision.PROMOTE;
import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.domain.BenchmarkEvidence;
import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.MutationOperatorType;
import com.dreamthought.saaa.domain.RealizationSummary;
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
                perfectObjectiveScores(),
                realized()
        ));

        assertThat(result.decision()).isEqualTo(DISCARD);
        // The magnitude now survives a gate failure by design (CHG-021, CON-002): the decision
        // stays binary and the score records how close the candidate got. Asserting the score is
        // still weighted, rather than asserting the decision twice, keeps this sensitive to the
        // zeroing coming back.
        assertThat(result.aggregateScore())
                .as("a failed gate discards without erasing how close the candidate got")
                .isGreaterThan(0.0);
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
                perfectObjectiveScores(),
                realized()
        ));

        assertThat(result.decision()).isEqualTo(DISCARD);
        assertThat(result.objectives()).containsEntry("subject.invariant.required_behavior_cases", 0.0);
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
                        "subject.objective.task_success", 0.90,
                        "subject.objective.reliability", 0.90,
                        "subject.objective.cost_latency_budget", 0.80,
                        "subject.objective.behavioral_safety", 1.00,
                        "subject.objective.parsimony", 0.70
                ),
                realized()
        ));

        assertThat(result.decision()).isEqualTo(PROMOTE);
        assertThat(result.aggregateScore()).isEqualTo(0.87);
    }

    @Test
    void treatsAbsentDeterministicCheckEvidenceAsAFailedGate() {
        var result = scorer.score(candidate(), new PhenotypeEvidence(
                new EvaluationEvidence(List.of(), List.of(), Instant.parse("2026-07-27T00:00:00Z")),
                List.of(BehaviorCaseEvidence.passed("renders-draft", "draft pages render")),
                perfectObjectiveScores(),
                realized()
        ));

        assertThat(result.decision()).isEqualTo(DISCARD);
        // The magnitude now survives a gate failure by design (CHG-021, CON-002): the decision
        // stays binary and the score records how close the candidate got. Asserting the score is
        // still weighted, rather than asserting the decision twice, keeps this sensitive to the
        // zeroing coming back.
        assertThat(result.aggregateScore())
                .as("a failed gate discards without erasing how close the candidate got")
                .isGreaterThan(0.0);
        assertThat(result.objectives())
                .containsEntry(PhenotypeFitnessScorer.DETERMINISTIC_CHECKS_GATE.canonical(), 0.0);
    }

    /**
     * A candidate that changed nothing measures zero lines, which parsimony rewards with 1.0 while
     * every other objective stays blind to it. The gate belongs here rather than in the caller for
     * the same reason as the behaviour-case gate: promotion integrity cannot depend on whoever
     * assembled the evidence having wired it correctly.
     */
    @Test
    void treatsAnEmptyRealizationAsAFailedGate() {
        var result = scorer.score(candidate(), new PhenotypeEvidence(
                evidence(),
                List.of(BehaviorCaseEvidence.passed("renders-draft", "draft pages render")),
                perfectObjectiveScores(),
                new RealizationSummary(0, 0)
        ));

        assertThat(result.decision()).isEqualTo(DISCARD);
        // The magnitude now survives a gate failure by design (CHG-021, CON-002): the decision
        // stays binary and the score records how close the candidate got. Asserting the score is
        // still weighted, rather than asserting the decision twice, keeps this sensitive to the
        // zeroing coming back.
        assertThat(result.aggregateScore())
                .as("a failed gate discards without erasing how close the candidate got")
                .isGreaterThan(0.0);
        assertThat(result.objectives())
                .containsEntry(PhenotypeFitnessScorer.NON_EMPTY_REALIZATION_GATE.canonical(), 0.0);
    }

    @Test
    void keepsRecordedGateOutcomesWhenEvidenceSuppliesGateKeys() {
        Map<String, Double> forged = new HashMap<>(perfectObjectiveScores());
        forged.put(PhenotypeFitnessScorer.DETERMINISTIC_CHECKS_GATE.canonical(), 1.0);

        var result = scorer.score(candidate(), new PhenotypeEvidence(
                new EvaluationEvidence(
                        List.of(failed("gradle-test", "deterministic checks failed")),
                        List.of(BenchmarkEvidence.measurement("publish-latency", 42.0, "ms")),
                        Instant.parse("2026-07-27T00:00:00Z")
                ),
                List.of(BehaviorCaseEvidence.passed("renders-draft", "draft pages render")),
                forged,
                realized()
        ));

        assertThat(result.decision()).isEqualTo(DISCARD);
        assertThat(result.objectives())
                .containsEntry(PhenotypeFitnessScorer.DETERMINISTIC_CHECKS_GATE.canonical(), 0.0);
    }

    @Test
    void comparesTheRawScoreAgainstTheThresholdRatherThanTheReportedRounding() {
        var result = scorer.score(candidate(), new PhenotypeEvidence(
                evidence(),
                List.of(BehaviorCaseEvidence.passed("renders-draft", "draft pages render")),
                Map.of(
                        "subject.objective.task_success", 1.00,
                        "subject.objective.reliability", 1.00,
                        "subject.objective.cost_latency_budget", 0.975,
                        "subject.objective.behavioral_safety", 0.00,
                        "subject.objective.parsimony", 0.00
                ),
                realized()
        ));

        assertThat(result.aggregateScore()).isEqualTo(0.80);
        assertThat(result.decision()).isEqualTo(DISCARD);
    }

    /**
     * The validator checks a contract's objectives against its operator's defaults, but the scorer
     * gates and weights against the shared constant because it never receives the contract. That is
     * only sound while the two cannot disagree. When this test fails, an operator has been given its
     * own objectives and the scorer must take the contract before that ships — see RISK-002 and T4b.
     */
    @Test
    void everyOperatorSharesTheObjectiveSetTheScorerAssumes() {
        assertThat(MutationOperatorType.values())
                .allSatisfy(operator -> assertThat(MutationOperatorPolicy.defaultsFor(operator).objectives())
                        .isEqualTo(MutationOperatorPolicy.DEFAULT_OBJECTIVES));
    }

    /**
     * S8 characterisation. Written before the contract-aware entry point exists and green on write,
     * so a regression to the two-argument path during CHG-014 fails here rather than going unnoticed.
     * This path is the wired one, so its behaviour must not drift while the new path is added.
     */
    @Test
    void contractlessScoringPreservesTheExistingGates() {
        var promoted = scorer.score(candidate(), new PhenotypeEvidence(
                evidence(),
                List.of(BehaviorCaseEvidence.passed("renders-draft", "draft pages render")),
                perfectObjectiveScores(),
                realized()));

        assertThat(promoted.decision())
                .as("a candidate clearing every structural gate still promotes without any contract")
                .isEqualTo(PROMOTE);
        assertThat(promoted.aggregateScore())
                .as("the contractless weighted sum is unchanged")
                .isEqualTo(1.00);
        assertThat(promoted.objectives())
                .as("the contractless audit map still carries exactly the five objectives and four gates")
                .hasSize(9)
                .containsKeys(
                        "subject.objective.task_success",
                        "subject.objective.reliability",
                        "subject.objective.cost_latency_budget",
                        "subject.objective.behavioral_safety",
                        "subject.objective.parsimony",
                        PhenotypeFitnessScorer.DETERMINISTIC_CHECKS_GATE.canonical(),
                        PhenotypeFitnessScorer.REQUIRED_BEHAVIOR_CASES_GATE.canonical(),
                        PhenotypeFitnessScorer.REQUIRED_OBJECTIVE_SCORES_GATE.canonical(),
                        PhenotypeFitnessScorer.NON_EMPTY_REALIZATION_GATE.canonical());
        assertThat(promoted.objectives())
                .as("every gate on a promoting candidate records a pass, not merely a key")
                .containsEntry(PhenotypeFitnessScorer.DETERMINISTIC_CHECKS_GATE.canonical(), 1.0)
                .containsEntry(PhenotypeFitnessScorer.REQUIRED_BEHAVIOR_CASES_GATE.canonical(), 1.0)
                .containsEntry(PhenotypeFitnessScorer.REQUIRED_OBJECTIVE_SCORES_GATE.canonical(), 1.0)
                .containsEntry(PhenotypeFitnessScorer.NON_EMPTY_REALIZATION_GATE.canonical(), 1.0);

        assertThat(scorer.score(candidate(), new PhenotypeEvidence(
                new EvaluationEvidence(
                        List.of(failed("gradle-test", "a deterministic check failed")),
                        List.of(),
                        Instant.parse("2026-07-27T00:00:00Z")),
                List.of(BehaviorCaseEvidence.passed("renders-draft", "draft pages render")),
                perfectObjectiveScores(),
                realized())).decision())
                .as("a failed deterministic check still discards")
                .isEqualTo(DISCARD);

        assertThat(scorer.score(candidate(), new PhenotypeEvidence(
                evidence(),
                List.of(BehaviorCaseEvidence.failed("publishes-review", "regressed")),
                perfectObjectiveScores(),
                realized())).decision())
                .as("a failed behaviour case still discards")
                .isEqualTo(DISCARD);

        assertThat(scorer.score(candidate(), new PhenotypeEvidence(
                evidence(),
                List.of(BehaviorCaseEvidence.passed("renders-draft", "draft pages render")),
                perfectObjectiveScores(),
                new RealizationSummary(0, 0))).decision())
                .as("an empty realization still discards")
                .isEqualTo(DISCARD);

        var missingObjective = new HashMap<>(perfectObjectiveScores());
        missingObjective.remove("subject.objective.parsimony");
        assertThat(scorer.score(candidate(), new PhenotypeEvidence(
                evidence(),
                List.of(BehaviorCaseEvidence.passed("renders-draft", "draft pages render")),
                missingObjective,
                realized())).decision())
                .as("a missing declared objective score still discards")
                .isEqualTo(DISCARD);
    }

    /** Any non-empty realization; these tests are about the other gates and the weighting. */
    private static RealizationSummary realized() {
        return new RealizationSummary(1, 8);
    }

    private static Map<String, Double> perfectObjectiveScores() {
        return Map.of(
                "subject.objective.task_success", 1.0,
                "subject.objective.reliability", 1.0,
                "subject.objective.cost_latency_budget", 1.0,
                "subject.objective.behavioral_safety", 1.0,
                "subject.objective.parsimony", 1.0
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
