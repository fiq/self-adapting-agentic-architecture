package com.dreamthought.saaa.deterministic;

import static com.dreamthought.saaa.domain.CheckEvidence.passed;
import static com.dreamthought.saaa.domain.FitnessDecision.DISCARD;
import static com.dreamthought.saaa.domain.FitnessDecision.PROMOTE;
import static com.dreamthought.saaa.domain.MutationOperatorType.TARGETED_BEHAVIOR_CHANGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dreamthought.saaa.domain.BenchmarkEvidence;
import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.FitnessSignalId;
import com.dreamthought.saaa.domain.MutationBounds;
import com.dreamthought.saaa.domain.MutationContract;
import com.dreamthought.saaa.domain.MutationTarget;
import com.dreamthought.saaa.domain.RealizationSummary;
import com.dreamthought.saaa.domain.RequiredEvidenceResult;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * CHG-014 S1 to S7. The contract-aware entry point decides against what the contract declared, in
 * addition to the structural gates it already applies. The contractless entry point is unchanged and
 * is characterised separately by {@code PhenotypeFitnessScorerTest}.
 */
final class ContractAwareFitnessTest {
    private final PhenotypeFitnessScorer scorer = new PhenotypeFitnessScorer();

    @Test
    void discardsWhenADeclaredEvidenceIdHasNoObservedResult() {
        var result = scorer.score(candidate(), cleanPhenotype(),
                contractDeclaring("failing_case_reproduced", "regression_case_added"),
                List.of(RequiredEvidenceResult.passed("failing_case_reproduced", "reproduced once")));

        assertThat(result.decision()).isEqualTo(DISCARD);
        assertThat(result.objectives())
                .as("the discard names the declared id that produced nothing")
                .containsKey(FitnessSignalId.invariant("regression_case_added").canonical());
    }

    @Test
    void aPassingResultForAnotherIdDoesNotSatisfyAMissingDeclaredId() {
        var result = scorer.score(candidate(), cleanPhenotype(),
                contractDeclaring("failing_case_reproduced", "regression_case_added"),
                List.of(
                        RequiredEvidenceResult.passed("failing_case_reproduced", "reproduced once"),
                        RequiredEvidenceResult.passed("unit_tests_pass", "green")));

        assertThat(result.decision())
                .as("an undeclared pass cannot stand in for the missing declared id")
                .isEqualTo(DISCARD);
    }

    @Test
    void discardsWhenADeclaredEvidenceIdReportsFailure() {
        var result = scorer.score(candidate(), cleanPhenotype(),
                contractDeclaring("regression_case_added"),
                List.of(RequiredEvidenceResult.failed("regression_case_added", "no new case found")));

        assertThat(result.decision()).isEqualTo(DISCARD);
        assertThat(result.objectives())
                .containsKey(FitnessSignalId.invariant("regression_case_added").canonical());
    }

    @Test
    void aPassingResultCannotMaskAFailingResultForTheSameId() {
        var result = scorer.score(candidate(), cleanPhenotype(),
                contractDeclaring("regression_case_added"),
                List.of(
                        RequiredEvidenceResult.passed("regression_case_added", "one runner said yes"),
                        RequiredEvidenceResult.failed("regression_case_added", "another said no")));

        assertThat(result.decision())
                .as("fail wins when one declared id has two conflicting results")
                .isEqualTo(DISCARD);
    }

    @Test
    void proceedsToObjectivesWhenEveryDeclaredEvidenceIdPasses() {
        var result = scorer.score(candidate(), cleanPhenotype(),
                contractDeclaring("failing_case_reproduced", "regression_case_added"),
                List.of(
                        RequiredEvidenceResult.passed("failing_case_reproduced", "reproduced once"),
                        RequiredEvidenceResult.passed("regression_case_added", "one case added")));

        assertThat(result.decision()).isEqualTo(PROMOTE);
        assertThat(result.fitnessScore().rawMagnitude()).isGreaterThan(java.math.BigDecimal.ZERO);
    }

    @Test
    void undeclaredEvidenceCannotSatisfyADeclaredGate() {
        var result = scorer.score(candidate(), cleanPhenotype(),
                contractDeclaring("regression_case_added"),
                List.of(
                        RequiredEvidenceResult.passed("regression_case_added", "one case added"),
                        RequiredEvidenceResult.failed("some_undeclared_probe", "failed, but not declared")));

        assertThat(result.decision())
                .as("an undeclared failure cannot weaken a declared gate that passed")
                .isEqualTo(PROMOTE);
        assertThat(result.objectives())
                .as("it is still recorded with its observed value, so the audit shows what happened")
                .containsEntry(FitnessSignalId.invariant("some_undeclared_probe").canonical(), 0.0);
    }

    @Test
    void structuralGatesStillApplyWhenDeclaredEvidencePasses() {
        var result = scorer.score(candidate(),
                new PhenotypeEvidence(evidence(), behaviourCases(), perfectObjectiveScores(),
                        new RealizationSummary(0, 0)),
                contractDeclaring("regression_case_added"),
                List.of(RequiredEvidenceResult.passed("regression_case_added", "one case added")));

        assertThat(result.decision())
                .as("declared evidence is additional to the structural gates, never a replacement")
                .isEqualTo(DISCARD);
    }

    @Test
    void declaredEvidenceGatesAreEmittedUnderTheCanonicalScheme() {
        var result = scorer.score(candidate(), cleanPhenotype(),
                contractDeclaring("regression_case_added"),
                List.of(RequiredEvidenceResult.failed("regression_case_added", "no new case found")));

        assertThat(result.objectives().keySet())
                .as("declared-evidence gates join the existing naming scheme rather than starting a second one")
                .anyMatch(key -> key.equals("subject.invariant.regression_case_added"));
        assertThat(result.decision())
                .as("integrity is expressed as voiding, because no severity field exists")
                .isEqualTo(DISCARD);
    }

    @Test
    void evidenceCannotOverwriteAStructuralGateOutcome() {
        // FitnessSignalId.invariant("deterministic_checks") is exactly DETERMINISTIC_CHECKS_GATE, so
        // an evidence id of that name would land on the same audit key and could report a passing
        // structural gate for a candidate whose checks failed.
        var failedChecks = new EvaluationEvidence(
                List.of(com.dreamthought.saaa.domain.CheckEvidence.failed("gradle-test", "a check failed")),
                List.of(),
                Instant.parse("2026-07-27T00:00:00Z"));

        assertThatThrownBy(() -> scorer.score(candidate(),
                new PhenotypeEvidence(failedChecks, behaviourCases(), perfectObjectiveScores(),
                        new RealizationSummary(1, 8)),
                contractDeclaring("regression_case_added"),
                List.of(RequiredEvidenceResult.passed("deterministic_checks", "not really"))))
                .as("an evidence id that collides with a structural gate is rejected, not merged")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deterministic_checks");
    }

    @Test
    void aDeclaredIdCollidingWithAStructuralGateIsRejected() {
        assertThatThrownBy(() -> scorer.score(candidate(), cleanPhenotype(),
                contractDeclaring("non_empty_realization"),
                List.of(RequiredEvidenceResult.passed("non_empty_realization", "declared, not observed"))))
                .as("a contract cannot declare an id that owns a structural gate's audit key")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non_empty_realization");
    }

    @Test
    void aFailingResultIsNotMaskedByALaterPassingResult() {
        var result = scorer.score(candidate(), cleanPhenotype(),
                contractDeclaring("regression_case_added"),
                List.of(
                        RequiredEvidenceResult.failed("regression_case_added", "another said no"),
                        RequiredEvidenceResult.passed("regression_case_added", "one runner said yes")));

        assertThat(result.decision())
                .as("fail wins regardless of the order the results arrive in")
                .isEqualTo(DISCARD);
    }

    private static MutationContract contractDeclaring(String... requiredEvidence) {
        return new MutationContract(
                "MUT-001",
                TARGETED_BEHAVIOR_CHANGE,
                "change interest rounding only at money boundaries",
                new MutationTarget("method", "src/main/java/example/Billing.java", "calculateInterest"),
                List.of("method_body"),
                new MutationBounds(2, 80, false, false, false),
                List.of(requiredEvidence),
                List.of("subject.invariant.deterministic_checks_pass",
                        "subject.invariant.required_evidence_present"),
                MutationOperatorPolicy.DEFAULT_OBJECTIVES,
                Optional.empty(),
                List.of());
    }

    private static PhenotypeEvidence cleanPhenotype() {
        return new PhenotypeEvidence(evidence(), behaviourCases(), perfectObjectiveScores(),
                new RealizationSummary(1, 8));
    }

    private static List<BehaviorCaseEvidence> behaviourCases() {
        return List.of(BehaviorCaseEvidence.passed("renders-draft", "draft pages render"));
    }

    private static Map<String, Double> perfectObjectiveScores() {
        return Map.of(
                "subject.objective.task_success", 1.0,
                "subject.objective.reliability", 1.0,
                "subject.objective.cost_latency_budget", 1.0,
                "subject.objective.behavioral_safety", 1.0,
                "subject.objective.parsimony", 1.0);
    }

    private static Candidate candidate() {
        return new Candidate("cand-001", "MUT-001", "candidate/MUT-001",
                Path.of(".worktrees/candidate-MUT-001"), "abc1234");
    }

    private static EvaluationEvidence evidence() {
        return new EvaluationEvidence(
                List.of(passed("gradle-test", "all deterministic checks passed")),
                List.of(BenchmarkEvidence.measurement("publish-latency", 42.0, "ms")),
                Instant.parse("2026-07-27T00:00:00Z"));
    }
}
