package com.dreamthought.saaa.deterministic;

import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.domain.BenchmarkEvidence;
import com.dreamthought.saaa.domain.CheckEvidence;
import com.dreamthought.saaa.domain.CheckStatus;
import com.dreamthought.saaa.domain.EvolutionContext;
import com.dreamthought.saaa.domain.EvolutionaryMemoryPolicyConfig;
import com.dreamthought.saaa.domain.EvolutionaryMemoryRecord;
import com.dreamthought.saaa.domain.FitnessDecision;
import com.dreamthought.saaa.domain.FitnessScore;
import com.dreamthought.saaa.domain.MutationScope;
import com.dreamthought.saaa.domain.RetrievalMode;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * CHG-024. A magnitude only means something beside the configuration that produced it, so the
 * working set must never rank records scored under different configurations against each other.
 *
 * <p>Stamping the fingerprint was not enough on its own. An independent review found it declared and
 * never read: nothing refused to compare across it, which is the same failure shape as RISK-002 — a
 * guard that is written down and that no code consumes. These tests are what make the guard real,
 * and they were written before the filter existed.
 *
 * <p>The highest-scoring record in each fixture below is deliberately the one that must be excluded.
 * A filter that silently did nothing would let it win a champion slot, so these assertions cannot
 * pass against an unenforced fingerprint.
 */
final class ScoringContextEnforcementTest {
    private static final String CURRENT = "aaaa111122223333";
    private static final String OTHER = "bbbb444455556666";

    private static final EvolutionaryMemoryPolicyConfig CONFIG =
            new EvolutionaryMemoryPolicyConfig("fixture-policy-v1", 1, 1, 1, 1, 0, 4);

    @Test
    void recordsScoredUnderADifferentConfigurationAreNotRanked() {
        var policy = new LineageNoveltyMemoryPolicy(CONFIG);
        var archive = List.of(
                record("under-other", OTHER, 0.99, CheckStatus.PASSED),
                record("under-current", CURRENT, 0.81, CheckStatus.PASSED));

        var selected = policy.select(archive, CURRENT);

        assertThat(selected)
                .as("a record scored under another configuration must not be ranked, however high")
                .extracting(EvolutionaryMemoryRecord::candidateId)
                .containsExactly("under-current");
    }

    /**
     * The filter must narrow the archive and nothing else. Without this, a filter that returned an
     * empty list would satisfy both assertions above, and the slot logic the policy exists for would
     * be silently dead.
     */
    @Test
    void everyRecordSharingTheCurrentFingerprintStillReachesTheSlotLogic() {
        var policy = new LineageNoveltyMemoryPolicy(CONFIG);
        var archive = List.of(
                record("promoted", CURRENT, 0.91, CheckStatus.PASSED),
                record("failed", CURRENT, 0.42, CheckStatus.FAILED));

        var selected = policy.select(archive, CURRENT);

        assertThat(selected)
                .as("filtering by fingerprint must not swallow the failure-fingerprint slot")
                .extracting(EvolutionaryMemoryRecord::candidateId)
                .containsExactlyInAnyOrder("promoted", "failed");
    }

    /** Revision selection carries the same guard; a revision match is not a comparability match. */
    @Test
    void selectingForARevisionAlsoRefusesAnotherConfiguration() {
        var policy = new LineageNoveltyMemoryPolicy(CONFIG);
        var archive = List.of(
                record("under-other", OTHER, 0.99, CheckStatus.PASSED),
                record("under-current", CURRENT, 0.81, CheckStatus.PASSED));

        var selected = policy.selectForRevision(archive, "baseline-revision", CURRENT);

        assertThat(selected)
                .extracting(EvolutionaryMemoryRecord::candidateId)
                .containsExactly("under-current");
    }

    private static EvolutionaryMemoryRecord record(
            String id, String fingerprint, double magnitude, CheckStatus status) {
        return new EvolutionaryMemoryRecord(
                new EvolutionContext("subject", "baseline-revision", "saaa", "process-1"),
                "fixture-policy-v1", "mutation-" + id, "summary " + id,
                MutationScope.WORKFLOW_DEFINITION, id, "commit-" + id, RetrievalMode.HYBRID,
                "retrieval-config-v1", List.of(), List.of(),
                List.of(new CheckEvidence("case", status, status.name().toLowerCase())),
                List.<BenchmarkEvidence>of(),
                FitnessScore.of(magnitude,
                        status == CheckStatus.PASSED ? FitnessDecision.PROMOTE : FitnessDecision.DISCARD),
                fingerprint,
                Instant.parse("2026-08-23T00:00:00Z"));
    }
}
