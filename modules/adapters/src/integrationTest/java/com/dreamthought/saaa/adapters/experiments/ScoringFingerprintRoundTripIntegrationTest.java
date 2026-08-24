package com.dreamthought.saaa.adapters.experiments;

import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.adapters.sqlite.SqliteEvolutionaryMemoryStore;
import com.dreamthought.saaa.domain.BenchmarkEvidence;
import com.dreamthought.saaa.domain.CheckEvidence;
import com.dreamthought.saaa.domain.CheckStatus;
import com.dreamthought.saaa.domain.EvolutionContext;
import com.dreamthought.saaa.domain.EvolutionaryMemoryRecord;
import com.dreamthought.saaa.domain.FitnessDecision;
import com.dreamthought.saaa.domain.FitnessScore;
import com.dreamthought.saaa.domain.MutationScope;
import com.dreamthought.saaa.domain.RetrievalMode;
import com.dreamthought.saaa.domain.ScoringContext;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * CHG-024. A fingerprint that does not survive persistence is worse than none at all: every reloaded
 * record would read as legacy, the comparability filter would exclude all of them, and the working
 * set would be silently empty for every run after a restart.
 *
 * <p>These pin the round trip on both durable surfaces — the local ledger and the Git-visible
 * envelope that rebuilds it. A distinctive fingerprint is used rather than a plausible-looking one,
 * so a implementation that defaulted the column could not accidentally match.
 */
final class ScoringFingerprintRoundTripIntegrationTest {
    private static final String FINGERPRINT = "9f86d081884c7d65";

    @Test
    void theScoringFingerprintSurvivesTheSqliteLedger(@TempDir Path tempDir) {
        var store = new SqliteEvolutionaryMemoryStore(tempDir.resolve("experiments.sqlite"));
        store.append(record(FINGERPRINT));

        var reloaded = store.records();

        assertThat(reloaded).singleElement()
                .extracting(EvolutionaryMemoryRecord::scoringFingerprint)
                .isEqualTo(FINGERPRINT);
    }

    /**
     * A record written before the fingerprint existed keeps reading as legacy rather than acquiring
     * a fabricated one. Backfilling would invent provenance the run never had.
     */
    @Test
    void aRecordWithoutAContextStillReadsAsLegacyAfterAReload(@TempDir Path tempDir) {
        var store = new SqliteEvolutionaryMemoryStore(tempDir.resolve("experiments.sqlite"));
        store.append(record(ScoringContext.LEGACY_UNVERSIONED));

        assertThat(store.records()).singleElement()
                .extracting(EvolutionaryMemoryRecord::scoringFingerprint)
                .isEqualTo(ScoringContext.LEGACY_UNVERSIONED);
    }

    @Test
    void theScoringFingerprintSurvivesTheLedgerEnvelope() {
        var codec = new ExperimentEnvelopeCodec();

        var decoded = codec.decode(codec.encode(record(FINGERPRINT)));

        assertThat(decoded.scoringFingerprint()).isEqualTo(FINGERPRINT);
    }

    private static EvolutionaryMemoryRecord record(String fingerprint) {
        return new EvolutionaryMemoryRecord(
                new EvolutionContext("subject", "baseline-revision", "saaa", "process-1"),
                "lineage-novelty-v1", "mutation-a", "summary a",
                MutationScope.WORKFLOW_DEFINITION, "candidate-a", "commit-a", RetrievalMode.HYBRID,
                "retrieval-config-v1", List.of("workflow.txt"), List.of("evidence-1"),
                List.of(new CheckEvidence("case", CheckStatus.PASSED, "passed")),
                List.<BenchmarkEvidence>of(),
                FitnessScore.of(0.91, FitnessDecision.PROMOTE),
                fingerprint,
                Instant.parse("2026-08-24T00:00:00Z"));
    }
}
