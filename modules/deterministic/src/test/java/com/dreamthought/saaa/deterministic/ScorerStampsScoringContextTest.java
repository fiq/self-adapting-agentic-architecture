package com.dreamthought.saaa.deterministic;

import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.CheckEvidence;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.RealizationSummary;
import com.dreamthought.saaa.domain.ScoringContext;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * CHG-024. The scorer stamps the scoring context, so only a genuinely older record is legacy.
 *
 * <p>A field that exists but is never populated is the failure PAT-004 describes: an assertion that
 * the key is present passes whatever the outcome. These assert the fingerprint is real and that it
 * responds to a configuration change, which a constant could not do.
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
    void aScoredResultCarriesARealScoringContextRatherThanLegacy() {
        var result = SCORER.score(candidate(), phenotype(Set.of("held_out")));

        assertThat(result.scoringContext()).isPresent();
        assertThat(result.scoringFingerprint())
                .as("a freshly scored result must never read as legacy")
                .isNotEqualTo(ScoringContext.LEGACY_UNVERSIONED);
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

    /** A result built without a context reports legacy rather than pretending to be comparable. */
    @Test
    void aResultBuiltWithoutAContextReportsLegacy() {
        var scored = SCORER.score(candidate(), phenotype(Set.of()));
        var legacy = new com.dreamthought.saaa.domain.FitnessResult(
                scored.candidate(), scored.evidence(), scored.objectives(), scored.fitnessScore());

        assertThat(legacy.scoringFingerprint()).isEqualTo(ScoringContext.LEGACY_UNVERSIONED);
    }
}
