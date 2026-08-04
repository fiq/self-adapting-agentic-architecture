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
