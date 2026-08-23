package com.dreamthought.saaa.deterministic;

import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.domain.CheckEvidence;
import com.dreamthought.saaa.domain.CheckStatus;
import com.dreamthought.saaa.domain.EvolutionContext;
import com.dreamthought.saaa.domain.EvolutionaryMemoryPolicyConfig;
import com.dreamthought.saaa.domain.EvolutionaryMemoryRecord;
import com.dreamthought.saaa.domain.FitnessDecision;
import com.dreamthought.saaa.domain.MutationScope;
import com.dreamthought.saaa.domain.RetrievalMode;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

final class LineageNoveltyMemoryPolicyTest {
    @Test
    void selectsByEvolutionaryValueAndKeepsKnownChampionLineageWithinABound() {
        var policy = new LineageNoveltyMemoryPolicy(
                new EvolutionaryMemoryPolicyConfig("fixture-policy-v1", 1, 1, 1, 1, 0, 4));
        var ancestor = record("ancestor", "base-0", "commit-ancestor", 0.7, "tests", CheckStatus.PASSED,
                Instant.parse("2020-01-01T00:00:00Z"), List.of("ARCH-001"));
        var champion = record("champion", "commit-ancestor", "commit-champion", 0.9, "tests", CheckStatus.PASSED,
                Instant.parse("2020-01-02T00:00:00Z"), List.of("ARCH-001"));
        var recentWeak = record("recent-weak", "base-x", "commit-weak", 0.1, "tests", CheckStatus.PASSED,
                Instant.parse("2026-01-01T00:00:00Z"), List.of("ARCH-001"));
        var distinctFailure = record("failure", "base-y", "commit-failure", 0.2, "hard-gate", CheckStatus.FAILED,
                Instant.parse("2021-01-01T00:00:00Z"), List.of("RISK-004"));

        var selected = policy.select(List.of(recentWeak, distinctFailure, ancestor, champion));

        assertThat(selected).hasSizeLessThanOrEqualTo(4);
        assertThat(selected).extracting(EvolutionaryMemoryRecord::candidateId)
                .contains("champion", "ancestor", "failure");
        assertThat(policy.id()).isEqualTo("fixture-policy-v1");
    }

    @Test
    void historicInflationExcludesOutcomesFromOtherBaselineRevisions() {
        var policy = new LineageNoveltyMemoryPolicy(
                new EvolutionaryMemoryPolicyConfig("fixture-policy-v1", 1, 0, 0, 0, 0, 1));

        assertThat(policy.selectForRevision(List.of(
                        record("matching", "historic", "candidate-a", 0.5, "tests", CheckStatus.PASSED,
                                Instant.EPOCH, List.of()),
                        record("other", "current", "candidate-b", 0.9, "tests", CheckStatus.PASSED,
                                Instant.EPOCH.plusSeconds(1), List.of())), "historic"))
                .extracting(EvolutionaryMemoryRecord::candidateId)
                .containsExactly("matching");
    }

    @Test
    void lineageCannotConsumeUnusedSlotsFromAnotherCategory() {
        var policy = new LineageNoveltyMemoryPolicy(
                new EvolutionaryMemoryPolicyConfig("fixture-policy-v1", 1, 1, 0, 0, 0, 2));
        var grandparent = record("grandparent", "base-0", "commit-grandparent", 0.5, "tests",
                CheckStatus.PASSED, Instant.parse("2020-01-01T00:00:00Z"), List.of());
        var parent = record("parent", "commit-grandparent", "commit-parent", 0.6, "tests",
                CheckStatus.PASSED, Instant.parse("2020-01-02T00:00:00Z"), List.of());
        var champion = record("champion", "commit-parent", "commit-champion", 0.9, "tests",
                CheckStatus.PASSED, Instant.parse("2020-01-03T00:00:00Z"), List.of());

        assertThat(policy.select(List.of(grandparent, parent, champion)))
                .extracting(EvolutionaryMemoryRecord::candidateId)
                .containsExactly("champion", "parent");
    }

    /**
     * CHG-021 let a discarded candidate keep its weighted magnitude, so score alone stopped meaning
     * "better". A champion slot must go to a candidate that actually passed its gates: a
     * high-scoring failure is a near miss worth remembering, never the best thing found so far.
     */
    @Test
    void aHigherScoringFailureDoesNotTakeAChampionSlotFromAPromotion() {
        var policy = new LineageNoveltyMemoryPolicy(
                new EvolutionaryMemoryPolicyConfig("fixture-policy-v1", 1, 0, 0, 0, 0, 1));
        var nearMiss = record("near-miss", "base-0", "commit-near-miss", 0.95, "hard-gate",
                CheckStatus.FAILED, Instant.parse("2026-01-02T00:00:00Z"), List.of());
        var promoted = record("promoted", "base-0", "commit-promoted", 0.85, "tests",
                CheckStatus.PASSED, Instant.parse("2026-01-01T00:00:00Z"), List.of());

        assertThat(policy.select(List.of(nearMiss, promoted)))
                .extracting(EvolutionaryMemoryRecord::candidateId)
                .as("decision outranks score, so the promoted candidate is the champion")
                .containsExactly("promoted");
    }

    /**
     * Decision partitions the ordering; magnitude still has to order records inside a partition, or
     * the near miss worth mutating from next is indistinguishable from the total miss. Asserting only
     * that promotions outrank failures would pass for a comparator that ignored score entirely.
     */
    @Test
    void magnitudeStillOrdersRecordsWithinOneDecision() {
        var policy = new LineageNoveltyMemoryPolicy(
                new EvolutionaryMemoryPolicyConfig("fixture-policy-v1", 1, 0, 0, 0, 0, 1));
        // Recency deliberately favours the weaker record, so a comparator that reached for
        // evaluatedAt before magnitude would return the other one.
        var nearMiss = record("near-miss", "base-0", "commit-near", 0.75, "hard-gate",
                CheckStatus.FAILED, Instant.parse("2026-01-01T00:00:00Z"), List.of());
        var totalMiss = record("total-miss", "base-0", "commit-total", 0.10, "hard-gate",
                CheckStatus.FAILED, Instant.parse("2026-06-01T00:00:00Z"), List.of());

        assertThat(policy.select(List.of(totalMiss, nearMiss)))
                .extracting(EvolutionaryMemoryRecord::candidateId)
                .as("among failures the near miss is the one worth keeping")
                .containsExactly("near-miss");
    }

    /**
     * A category slot exists to bring something new into the selection. If its representative was
     * already taken as a champion, spending the slot on that same record adds nothing and the
     * category goes unrepresented. Decision-first ordering turned this from an occasional collision
     * into a certainty, because a promoted record now always sorts ahead of every failure.
     */
    @Test
    void aNoveltySlotIsNotSpentOnARecordAlreadySelected() {
        var policy = new LineageNoveltyMemoryPolicy(
                new EvolutionaryMemoryPolicyConfig("fixture-policy-v1", 1, 0, 0, 1, 0, 2));
        var promoted = record("promoted", "base-0", "commit-promoted", 0.85, "tests",
                CheckStatus.PASSED, Instant.parse("2026-01-01T00:00:00Z"), List.of("ARCH-001"));
        var novelFailure = record("novel-failure", "base-0", "commit-novel", 0.40, "hard-gate",
                CheckStatus.FAILED, Instant.parse("2026-01-02T00:00:00Z"), List.of("RISK-004"));

        assertThat(policy.select(List.of(promoted, novelFailure)))
                .extracting(EvolutionaryMemoryRecord::candidateId)
                .as("the novelty slot must reach a record the champion pass did not already take")
                .containsExactlyInAnyOrder("promoted", "novel-failure");
    }

    private static EvolutionaryMemoryRecord record(
            String id, String baseline, String commit, double fitness, String checkName, CheckStatus checkStatus,
            Instant evaluatedAt, List<String> evidence) {
        return new EvolutionaryMemoryRecord(
                new EvolutionContext("subject", baseline, "saaa", "process-1"),
                "fixture-policy-v1", "mutation-" + id, "summary " + id,
                MutationScope.WORKFLOW_DEFINITION, id, commit, RetrievalMode.HYBRID,
                "retrieval-config-v1", evidence,
                List.of(new CheckEvidence(checkName, checkStatus, checkStatus.name().toLowerCase())),
                List.of(), fitness, checkStatus == CheckStatus.PASSED ? FitnessDecision.PROMOTE : FitnessDecision.DISCARD,
                evaluatedAt);
    }
}
