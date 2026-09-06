package com.dreamthought.saaa.adapters.sqlite;

import static com.dreamthought.saaa.domain.FitnessDecision.DISCARD;
import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.domain.BenchmarkEvidence;
import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.CheckEvidence;
import com.dreamthought.saaa.domain.EvaluationEvidence;
import com.dreamthought.saaa.domain.FitnessResult;
import com.dreamthought.saaa.domain.FitnessScore;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SqliteExperimentMetadataStoreIntegrationTest {
    @TempDir
    private Path tempDir;

    @Test
    void recordsCandidateFitnessAndEvaluationEvidence() throws SQLException {
        Path database = tempDir.resolve("experiments.sqlite");
        var store = new SqliteExperimentMetadataStore(database);
        var candidate = new Candidate(
                "candidate-mut-001",
                "mut-001",
                "candidate/baseline-mut-001",
                Path.of(".worktrees/candidate-baseline-mut-001"),
                "0123456789abcdef0123456789abcdef01234567"
        );
        var evidence = new EvaluationEvidence(
                List.of(
                        CheckEvidence.passed("unit-tests", "all tests passed"),
                        CheckEvidence.failed("architecture-boundary", "provider leak detected")
                ),
                List.of(BenchmarkEvidence.measurement("sample-throughput", 42.0, "ops/s")),
                Instant.parse("2026-07-27T00:00:00Z")
        );
        var result = new FitnessResult(
                candidate,
                evidence,
                Map.of("correctness", 0.0, "throughput", 0.4),
                FitnessScore.of(0.2, DISCARD),
                new com.dreamthought.saaa.domain.ScoringContext(
                        List.of(new com.dreamthought.saaa.domain.FitnessObjective("o", 1.0)),
                        java.util.Set.of(), java.util.Set.of(), 0.80,
                        java.util.Set.of("case"), 80, java.util.Map.of())
        );

        store.recordCandidate(candidate);
        store.recordFitness(result);
        store.recordCandidate(candidate);

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath())) {
            assertThat(count(connection, "schema_migrations")).isEqualTo(3);
            assertThat(singleText(connection, "select branch_name from candidates where id = ?", candidate.id()))
                    .isEqualTo(candidate.branchName());
            assertThat(singleText(connection, "select decision from fitness_results where candidate_id = ?", candidate.id()))
                    .isEqualTo("DISCARD");
            assertThat(singleDouble(connection, "select raw_magnitude from fitness_results where candidate_id = ?", candidate.id()))
                    .isEqualTo(0.2);
            assertThat(singleText(
                    connection,
                    "select value from fitness_objectives where candidate_id = ? and name = ?",
                    candidate.id(),
                    "throughput"
            )).isEqualTo("0.4");
            assertThat(singleText(
                    connection,
                    "select status from evaluation_checks where candidate_id = ? and position = ?",
                    candidate.id(),
                    "1"
            )).isEqualTo("FAILED");
            assertThat(singleText(
                    connection,
                    "select unit from evaluation_benchmarks where candidate_id = ? and position = ?",
                    candidate.id(),
                    "0"
            )).isEqualTo("ops/s");
        }
    }

    /**
     * fitness_results is the third durable surface: leaving the fingerprint out of it would
     * persist magnitudes with no record of what they were measured against. Independent review
     * caught this surface keeping the old four-column shape while the other two carried it.
     */
    @Test
    void theScoringFingerprintIsPersistedBesideTheRawMagnitude() throws SQLException {
        Path database = tempDir.resolve("experiments.sqlite");
        var store = new SqliteExperimentMetadataStore(database);
        var candidate = new Candidate(
                "candidate-1", "mut-1", "candidate/mut-1",
                Path.of(".worktrees/candidate-1"), "abc1234");
        var evidence = new EvaluationEvidence(List.of(), List.of(), Instant.parse("2026-08-24T00:00:00Z"));
        var result = new FitnessResult(candidate, evidence, Map.of(),
                FitnessScore.of(0.5, DISCARD),
                new com.dreamthought.saaa.domain.ScoringContext(
                        List.of(new com.dreamthought.saaa.domain.FitnessObjective("o", 1.0)),
                        java.util.Set.of("held_out_x"), java.util.Set.of(), 0.80,
                        java.util.Set.of("case"), 80, java.util.Map.of()));

        store.recordFitness(result);

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath())) {
            assertThat(singleText(connection,
                    "select scoring_fingerprint from fitness_results where candidate_id = ?",
                    candidate.id()))
                    .isEqualTo(result.scoringFingerprint());
        }
    }

    /**
     * S7. A generation has to leave a record, or the selection it made is unreproducible: the point
     * of a total order is that a later reader can check it rather than trust it.
     *
     * <p>Five things are asserted because each answers a question the row otherwise leaves open — the
     * order, the winner, the shared fingerprint, how many of N produced evidence, and the spread. The
     * spread is the one that looks like an extra and is not: ADR-0002 names "population ships but
     * ranking is not measurably useful" as a revisit trigger, and without the spread in the record
     * that question can only be answered by impression.
     */
    @Test
    void recordsAGenerationsRankingWinnerFingerprintEvidenceCountAndSpread() throws SQLException {
        Path database = tempDir.resolve("experiments.sqlite");
        var store = new SqliteExperimentMetadataStore(database);
        var promoted = scored("candidate-run-c1", 0.90, com.dreamthought.saaa.domain.FitnessDecision.PROMOTE);
        var discarded = scored("candidate-run-c2", 0.95, DISCARD);
        store.recordFitness(promoted);
        store.recordFitness(discarded);
        var generation = new com.dreamthought.saaa.domain.RankedGeneration(
                List.of(promoted, discarded),
                List.of(new com.dreamthought.saaa.domain.UnevaluatedCandidate(
                        "attempt-3-of-3", "IllegalStateException: worktree already exists")));

        store.recordGeneration(new com.dreamthought.saaa.domain.GenerationRecord(
                "run-1", generation, Instant.parse("2026-09-06T00:00:00Z")));

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath())) {
            assertThat(singleText(connection,
                    "select winner_candidate_id from generations where run_id = ?", "run-1"))
                    .as("the winner is the promoted candidate, not the higher magnitude")
                    .isEqualTo("candidate-run-c1");
            assertThat(singleText(connection,
                    "select evaluated_count || ' of ' || requested_count from generations where run_id = ?",
                    "run-1"))
                    .as("a generation that ranked two of three must say three")
                    .isEqualTo("2 of 3");
            assertThat(singleText(connection,
                    "select scoring_fingerprint from generations where run_id = ?", "run-1"))
                    .isEqualTo(promoted.scoringFingerprint());
            assertThat(singleDouble(connection,
                    "select spread from generations where run_id = ?", "run-1"))
                    .isEqualTo(0.05, org.assertj.core.data.Offset.offset(1e-9));
            assertThat(singleText(connection,
                    "select group_concat(candidate_id, ',') from ("
                            + "select candidate_id from generation_rankings where run_id = ? order by position)",
                    "run-1"))
                    .as("the recorded order is the ranking, best first")
                    .isEqualTo("candidate-run-c1,candidate-run-c2");
            assertThat(singleText(connection,
                    "select reason from generation_unevaluated where run_id = ? and position = 1", "run-1"))
                    .as("absent evidence is recorded, never a candidate quietly dropped")
                    .contains("worktree already exists");
        }
    }

    /**
     * A generation where nothing produced evidence has no fingerprint and no spread, and the record
     * says so rather than writing a zero. A zero spread means "every candidate scored the same",
     * which is a finding; an absent one means there was nothing to measure.
     */
    @Test
    void aGenerationThatProducedNoEvidenceRecordsNoFingerprintAndNoSpread() throws SQLException {
        Path database = tempDir.resolve("experiments.sqlite");
        var store = new SqliteExperimentMetadataStore(database);
        var generation = new com.dreamthought.saaa.domain.RankedGeneration(
                List.of(),
                List.of(new com.dreamthought.saaa.domain.UnevaluatedCandidate("attempt-1-of-1", "no worktree")));

        store.recordGeneration(new com.dreamthought.saaa.domain.GenerationRecord(
                "run-empty", generation, Instant.parse("2026-09-06T00:00:00Z")));

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath())) {
            assertThat(singleText(connection,
                    "select coalesce(scoring_fingerprint, 'null') from generations where run_id = ?",
                    "run-empty")).isEqualTo("null");
            assertThat(singleText(connection,
                    "select coalesce(cast(spread as text), 'null') from generations where run_id = ?",
                    "run-empty")).isEqualTo("null");
            assertThat(singleText(connection,
                    "select coalesce(winner_candidate_id, 'null') from generations where run_id = ?",
                    "run-empty")).isEqualTo("null");
        }
    }

    /**
     * S4 at the durable surface, and the case that separates "the winner" from "the top of the
     * ranking". They are the same candidate whenever anything promoted, because FitnessScore orders
     * decision-first, so a generation with a promotion cannot tell the two apart. Only a generation
     * where every candidate was discarded can, and without it the ledger could record its best
     * discard as a winner and every other test would still pass.
     *
     * <p>This was found by mutation, not by reading: recording {@code ranked().get(0)} instead of
     * {@code winner()} broke nothing until this test existed.
     */
    @Test
    void aGenerationWhereEveryCandidateWasDiscardedRecordsNoWinner() throws SQLException {
        Path database = tempDir.resolve("experiments.sqlite");
        var store = new SqliteExperimentMetadataStore(database);
        var better = scored("candidate-run-c1", 0.79, DISCARD);
        var worse = scored("candidate-run-c2", 0.40, DISCARD);
        store.recordFitness(better);
        store.recordFitness(worse);

        store.recordGeneration(new com.dreamthought.saaa.domain.GenerationRecord(
                "run-discarded",
                new com.dreamthought.saaa.domain.RankedGeneration(List.of(better, worse), List.of()),
                Instant.parse("2026-09-06T00:00:00Z")));

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath())) {
            assertThat(singleText(connection,
                    "select coalesce(winner_candidate_id, 'none') from generations where run_id = ?",
                    "run-discarded"))
                    .as("ranking selects among promotions; it is not a second opinion on the gates")
                    .isEqualTo("none");
            assertThat(singleText(connection,
                    "select candidate_id from generation_rankings where run_id = ? and position = 1",
                    "run-discarded"))
                    .as("the order is still recorded, so a near miss stays distinguishable from a total miss")
                    .isEqualTo("candidate-run-c1");
        }
    }

    private static FitnessResult scored(
            String candidateId, double magnitude, com.dreamthought.saaa.domain.FitnessDecision decision) {
        return new FitnessResult(
                new Candidate(candidateId, "mut-" + candidateId, "candidate/" + candidateId,
                        Path.of(".worktrees", candidateId), "abc1234"),
                new EvaluationEvidence(List.of(), List.of(), Instant.parse("2026-09-06T00:00:00Z")),
                Map.of(),
                FitnessScore.of(magnitude, decision),
                new com.dreamthought.saaa.domain.ScoringContext(
                        List.of(new com.dreamthought.saaa.domain.FitnessObjective("o", 1.0)),
                        java.util.Set.of(), java.util.Set.of(), 0.80,
                        java.util.Set.of("case"), 80, java.util.Map.of()));
    }

    private static int count(java.sql.Connection connection, String table) throws SQLException {
        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery("select count(*) from " + table)) {
            return resultSet.getInt(1);
        }
    }

    private static String singleText(java.sql.Connection connection, String sql, String... values) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setString(index + 1, values[index]);
            }
            try (var resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).as(sql).isTrue();
                return resultSet.getString(1);
            }
        }
    }

    private static double singleDouble(java.sql.Connection connection, String sql, String... values) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setString(index + 1, values[index]);
            }
            try (var resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).as(sql).isTrue();
                return resultSet.getDouble(1);
            }
        }
    }
}
