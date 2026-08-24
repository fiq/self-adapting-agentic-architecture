package com.dreamthought.saaa.adapters.experiments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.sql.DriverManager;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * CHG-024. A fingerprint that does not survive persistence is worse than none at all: if reload
 * lost it, every record would claim comparability it never had, and nothing downstream could tell.
 *
 * <p>These pin the round trip on both durable surfaces — the local ledger and the Git-visible
 * envelope that rebuilds it. A distinctive fingerprint is used rather than a plausible-looking one,
 * so an implementation that defaulted the column could not accidentally match.
 *
 * <p>Neither surface defaults a missing fingerprint. An earlier draft carrying a default marker was
 * flagged by independent review: any writer that forgot to stamp would silently fabricate
 * provenance. The envelope codec rejects the document and the ledger schema rejects the row.
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
     * The schema carries no default: a raw row written without the column is rejected, so no
     * future writer path can bypass the record's own validation and invent provenance.
     */
    @Test
    void aRowWithoutAFingerprintIsRejectedByTheLedgerSchema(@TempDir Path tempDir) throws Exception {
        var database = tempDir.resolve("experiments.sqlite");
        new SqliteEvolutionaryMemoryStore(database);

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
                var statement = connection.prepareStatement("""
                        insert into evolutionary_memory(
                          candidate_id, subject_repository_id, baseline_repository_revision,
                          process_repository_id, process_repository_revision, memory_policy_id,
                          mutation_id, mutation_summary, mutation_scope, candidate_commit, retrieval_mode,
                          retrieval_configuration_id, raw_magnitude, decision, evaluated_at)
                        values ('c', 's', 'r', 'p', 'pr', 'm', 'mu', 'ms', 'WORKFLOW_DEFINITION',
                                'cc', 'HYBRID', 'rc', 0.5, 'DISCARD', '2026-08-24T00:00:00Z')
                        """)) {
            assertThatThrownBy(statement::executeUpdate)
                    .hasMessageContaining("scoring_fingerprint");
        }
    }

    @Test
    void theScoringFingerprintSurvivesTheLedgerEnvelope() {
        var codec = new ExperimentEnvelopeCodec();

        var decoded = codec.decode(codec.encode(record(FINGERPRINT)));

        assertThat(decoded.scoringFingerprint()).isEqualTo(FINGERPRINT);
    }

    /**
     * An envelope without a fingerprint cannot say what its magnitude was measured against, so it
     * is rejected rather than admitted with a default that would fabricate provenance.
     */
    @Test
    void anEnvelopeWithoutAFingerprintIsRejected() {
        var codec = new ExperimentEnvelopeCodec();
        var stripped = codec.encode(record(FINGERPRINT)).lines()
                .filter(line -> !line.trim().startsWith("scoring_fingerprint:"))
                .reduce("", (left, right) -> left + right + "\n");

        assertThatThrownBy(() -> codec.decode(stripped))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scoring_fingerprint");
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
