package com.dreamthought.saaa.deterministic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.CheckEvidence;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.FitnessResult;
import com.dreamthought.saaa.domain.RealizationSummary;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * CHG-024. The scorer stamps the scoring context, and the context is mandatory.
 *
 * <p>A field that exists but is never populated is the failure PAT-004 describes: an assertion that
 * the key is present passes whatever the outcome. These assert the fingerprint is real and that it
 * responds to a configuration change, which a constant could not do.
 *
 * <p>There is no legacy form. An earlier draft defaulted a missing context to a marker string, and
 * an independent review flagged the trap: any code path that forgot to stamp would silently produce
 * comparable-looking history instead of failing. The context is now a required constructor
 * argument, so the failure surface is a rejected construction rather than a poisoned ranking.
 */
final class ScorerStampsScoringContextTest {
    private static final PhenotypeFitnessScorer SCORER = new PhenotypeFitnessScorer();

    private static PhenotypeEvidence phenotype(Set<String> heldOut) {
        var checks = List.of(CheckEvidence.passed("gating", "held"));
        var objectives = Map.of(
                "subject.objective.task_success", 1.0,
                "subject.objective.reliability", 1.0,
                "subject.objective.cost_latency_budget", 1.0,
                "subject.objective.behavioral_safety", 1.0,
                "subject.objective.parsimony", 1.0);
        return new PhenotypeEvidence(
                new EvaluationEvidence(checks, List.of(), Instant.EPOCH),
                List.of(BehaviorCaseEvidence.passed("gating", "held")),
                objectives,
                new RealizationSummary(1, 1),
                heldOut,
                heldOut);
    }

    private static Candidate candidate() {
        return new Candidate("cand", "mut", "candidate/mut", Path.of(".worktrees/cand"), "abc1234");
    }

    @Test
    void aScoredResultCarriesTheStampedScoringContext() {
        var result = SCORER.score(candidate(), phenotype(Set.of("held_out")));

        assertThat(result.scoringContext().withheldCheckNames()).contains("held_out");
        assertThat(result.scoringFingerprint())
                .as("the fingerprint is derived from the stamped context, not a constant")
                .isEqualTo(result.scoringContext().fingerprint());
    }

    /**
     * The stamped context must reflect the configuration actually used. A constant fingerprint would
     * satisfy the presence assertion above while still letting two incomparable runs compare equal,
     * so the fingerprint has to move when the held-out set does.
     */
    @Test
    void changingTheHeldOutSetChangesTheStampedFingerprint() {
        String one = SCORER.score(candidate(), phenotype(Set.of("held_out"))).scoringFingerprint();
        String other = SCORER.score(candidate(), phenotype(Set.of("held_out", "second")))
                .scoringFingerprint();

        assertThat(other).isNotEqualTo(one);
    }

    /**
     * The context is a required constructor argument, so the only way to omit it is to pass null,
     * and that is rejected rather than defaulted. A default is exactly what an independent review
     * flagged: a code path that forgets to stamp would silently produce uncomparable history.
     */
    @Test
    void aResultCannotBeConstructedWithoutAScoringContext() {
        var scored = SCORER.score(candidate(), phenotype(Set.of()));

        assertThatNullPointerException()
                .as("omitting the scoring context must fail loudly, not default to invented provenance")
                .isThrownBy(() -> new FitnessResult(
                        scored.candidate(), scored.evidence(), scored.objectives(),
                        scored.fitnessScore(), null))
                .withMessage("scoringContext");
    }
}
