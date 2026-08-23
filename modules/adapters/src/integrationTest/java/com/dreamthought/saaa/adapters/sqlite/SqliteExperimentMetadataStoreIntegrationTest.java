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
                FitnessScore.of(0.2, DISCARD)
        );

        store.recordCandidate(candidate);
        store.recordFitness(result);
        store.recordCandidate(candidate);

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath())) {
            assertThat(count(connection, "schema_migrations")).isEqualTo(1);
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
