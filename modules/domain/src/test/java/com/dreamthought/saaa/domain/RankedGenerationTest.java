package com.dreamthought.saaa.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CHG-026. What a ranked generation is, independent of the policy that produced one.
 *
 * <p>The winner and the fingerprint are derived rather than stored, so a record cannot be built
 * whose winner disagrees with its own ordering.
 */
final class RankedGenerationTest {
    private static final List<FitnessObjective> OBJECTIVES =
            List.of(new FitnessObjective("subject.objective.task_success", 1.00));

    @Test
    @DisplayName("a list that is not in best-first order is refused")
    void anUnorderedRankingIsRejected() {
        var better = result("candidate-better", 0.95, FitnessDecision.PROMOTE);
        var worse = result("candidate-worse", 0.50, FitnessDecision.PROMOTE);

        assertThatThrownBy(() -> new RankedGeneration(List.of(worse, better), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not in best-first order");
    }

    @Test
    @DisplayName("results measured against different configurations cannot be one generation")
    void aGenerationCannotMixScoringFingerprints() {
        var one = result("candidate-a", 0.95, FitnessDecision.PROMOTE, context(0.80));
        var other = result("candidate-b", 0.50, FitnessDecision.PROMOTE, context(0.60));

        assertThatThrownBy(() -> new RankedGeneration(List.of(one, other), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scoring fingerprint");
    }

    @Test
    @DisplayName("two candidates sharing an id are refused, because the order could not be total")
    void twoCandidatesSharingAnIdAreRejected() {
        var one = result("candidate-a", 0.95, FitnessDecision.PROMOTE);
        var collidingId = result("candidate-a", 0.50, FitnessDecision.PROMOTE);

        assertThatThrownBy(() -> new RankedGeneration(List.of(one, collidingId), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("share an id");
    }

    @Test
    @DisplayName("the spread covers every candidate, including the ones that were discarded")
    void theSpreadIsMeasuredAcrossAllCandidates() {
        var promoted = result("candidate-promoted", 0.81, FitnessDecision.PROMOTE);
        var discarded = result("candidate-discarded", 0.95, FitnessDecision.DISCARD);
        var alsoDiscarded = result("candidate-hopeless", 0.05, FitnessDecision.DISCARD);

        var generation = new RankedGeneration(
                List.of(promoted, discarded, alsoDiscarded),
                List.of(new UnevaluatedCandidate("candidate-broken", "no worktree could be created")));

        assertThat(generation.spread())
                .as("the spread answers whether ranking discriminated at all, so it reads every score")
                .contains(new BigDecimal("0.90"));
        assertThat(generation.evaluatedCount()).isEqualTo(3);
        assertThat(generation.requestedCount())
                .as("a generation that ranked three of four must say so rather than report three")
                .isEqualTo(4);
    }

    @Test
    @DisplayName("a generation where nothing could be evaluated has no winner, no spread and no fingerprint")
    void anEmptyGenerationClaimsNothing() {
        var generation = new RankedGeneration(
                List.of(), List.of(new UnevaluatedCandidate("candidate-broken", "realisation failed")));

        assertThat(generation.winner()).isEmpty();
        assertThat(generation.spread()).isEmpty();
        assertThat(generation.scoringFingerprint()).isEmpty();
        assertThat(generation.requestedCount()).isEqualTo(1);
    }

    private static FitnessResult result(String candidateId, double magnitude, FitnessDecision decision) {
        return result(candidateId, magnitude, decision, context(0.80));
    }

    private static FitnessResult result(
            String candidateId, double magnitude, FitnessDecision decision, ScoringContext context) {
        return new FitnessResult(
                new Candidate(candidateId, "MUT-" + candidateId, "candidate/" + candidateId,
                        Path.of(".worktrees", candidateId), "0".repeat(40)),
                new EvaluationEvidence(List.<CheckEvidence>of(), List.of(), Instant.EPOCH),
                Map.of("subject.objective.task_success", magnitude),
                FitnessScore.of(magnitude, decision),
                context);
    }

    private static ScoringContext context(double promotionThreshold) {
        return new ScoringContext(
                OBJECTIVES, Set.of(), Set.of(), promotionThreshold, Set.of("case_a"), 80,
                Map.<String, Double>of());
    }
}
