package com.dreamthought.saaa.adapters.sqlite;

import com.dreamthought.saaa.deterministic.ExperimentMetadataStore;
import com.dreamthought.saaa.domain.BenchmarkEvidence;
import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.CheckEvidence;
import com.dreamthought.saaa.domain.FitnessResult;
import com.dreamthought.saaa.domain.GenerationRecord;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

public final class SqliteExperimentMetadataStore implements ExperimentMetadataStore {
    private final String jdbcUrl;

    public SqliteExperimentMetadataStore() {
        this(Path.of("experiments.sqlite"));
    }

    public SqliteExperimentMetadataStore(Path databasePath) {
        Objects.requireNonNull(databasePath, "databasePath");
        Path absolutePath = databasePath.toAbsolutePath().normalize();
        Path parent = absolutePath.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (java.io.IOException exception) {
                throw new IllegalStateException("failed to create SQLite database directory: " + parent, exception);
            }
        }
        this.jdbcUrl = "jdbc:sqlite:" + absolutePath;
        migrate();
    }

    @Override
    public void recordCandidate(Candidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        try (Connection connection = connect()) {
            writeCandidate(connection, candidate);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to record candidate metadata", exception);
        }
    }

    @Override
    public void recordFitness(FitnessResult result) {
        Objects.requireNonNull(result, "result");
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                writeCandidate(connection, result.candidate());
                deleteFitnessChildren(connection, result.candidate().id());
                writeFitnessResult(connection, result);
                writeObjectives(connection, result);
                writeChecks(connection, result);
                writeBenchmarks(connection, result);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to record fitness metadata", exception);
        }
    }

    @Override
    public void recordGeneration(GenerationRecord record) {
        Objects.requireNonNull(record, "record");
        var generation = record.generation();
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                // Written in one transaction with the ranking rows. A generation header without its
                // order would claim a winner nothing supports, which is worse than no record.
                deleteGenerationChildren(connection, record.runId());
                writeGeneration(connection, record);
                int position = 1;
                for (var ranked : generation.ranked()) {
                    writeGenerationRanking(connection, record.runId(), position++, ranked.candidate().id());
                }
                position = 1;
                for (var lost : generation.unevaluated()) {
                    writeGenerationUnevaluated(connection, record.runId(), position++, lost);
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to record generation metadata", exception);
        }
    }

    private static void deleteGenerationChildren(Connection connection, String runId) throws SQLException {
        for (String table : java.util.List.of("generation_rankings", "generation_unevaluated")) {
            try (PreparedStatement statement =
                         connection.prepareStatement("delete from " + table + " where run_id = ?")) {
                statement.setString(1, runId);
                statement.executeUpdate();
            }
        }
    }

    private static void writeGeneration(Connection connection, GenerationRecord record) throws SQLException {
        var generation = record.generation();
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into generations(
                  run_id, requested_count, evaluated_count, scoring_fingerprint,
                  winner_candidate_id, spread, recorded_at)
                values (?, ?, ?, ?, ?, ?, ?)
                on conflict(run_id) do update set
                  requested_count = excluded.requested_count,
                  evaluated_count = excluded.evaluated_count,
                  scoring_fingerprint = excluded.scoring_fingerprint,
                  winner_candidate_id = excluded.winner_candidate_id,
                  spread = excluded.spread,
                  recorded_at = excluded.recorded_at
                """)) {
            statement.setString(1, record.runId());
            statement.setInt(2, generation.requestedCount());
            statement.setInt(3, generation.evaluatedCount());
            setNullableString(statement, 4, generation.scoringFingerprint().orElse(null));
            setNullableString(statement, 5,
                    generation.winner().map(result -> result.candidate().id()).orElse(null));
            var spread = generation.spread().orElse(null);
            if (spread == null) {
                statement.setNull(6, java.sql.Types.REAL);
            } else {
                statement.setDouble(6, spread.doubleValue());
            }
            statement.setString(7, record.recordedAt().toString());
            statement.executeUpdate();
        }
    }

    private static void writeGenerationRanking(
            Connection connection, String runId, int position, String candidateId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into generation_rankings(run_id, position, candidate_id) values (?, ?, ?)")) {
            statement.setString(1, runId);
            statement.setInt(2, position);
            statement.setString(3, candidateId);
            statement.executeUpdate();
        }
    }

    private static void writeGenerationUnevaluated(
            Connection connection, String runId, int position,
            com.dreamthought.saaa.domain.UnevaluatedCandidate candidate) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into generation_unevaluated(run_id, position, candidate_reference, reason)
                values (?, ?, ?, ?)
                """)) {
            statement.setString(1, runId);
            statement.setInt(2, position);
            statement.setString(3, candidate.reference());
            statement.setString(4, candidate.reason());
            statement.executeUpdate();
        }
    }

    private static void setNullableString(PreparedStatement statement, int index, String value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    private void migrate() {
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                execute(connection, """
                        create table if not exists schema_migrations (
                          version integer primary key not null
                        )
                        """);
                if (!migrationApplied(connection, 1)) {
                    execute(connection, """
                            create table if not exists candidates (
                              id text primary key not null,
                              mutation_id text not null,
                              branch_name text not null,
                              worktree_path text not null,
                              commit_sha text not null
                            )
                            """);
                    execute(connection, """
                            create table if not exists fitness_results (
                              candidate_id text primary key not null references candidates(id) on delete cascade,
                              raw_magnitude real not null,
                              decision text not null,
                              evaluated_at text not null
                            )
                            """);
                    execute(connection, """
                            create table if not exists fitness_objectives (
                              candidate_id text not null references candidates(id) on delete cascade,
                              name text not null,
                              value real not null,
                              primary key (candidate_id, name)
                            )
                            """);
                    execute(connection, """
                            create table if not exists evaluation_checks (
                              candidate_id text not null references candidates(id) on delete cascade,
                              position integer not null,
                              name text not null,
                              status text not null,
                              summary text not null,
                              primary key (candidate_id, position)
                            )
                            """);
                    execute(connection, """
                            create table if not exists evaluation_benchmarks (
                              candidate_id text not null references candidates(id) on delete cascade,
                              position integer not null,
                              name text not null,
                              value real not null,
                              unit text not null,
                              primary key (candidate_id, position)
                            )
                            """);
                    try (PreparedStatement statement = connection.prepareStatement(
                            "insert into schema_migrations(version) values (?)"
                    )) {
                        statement.setInt(1, 1);
                        statement.executeUpdate();
                    }
                }
                if (!migrationApplied(connection, 2)) {
                    // CHG-024: the fingerprint must sit beside the magnitude it describes. SQLite
                    // cannot ADD COLUMN ... NOT NULL without a default, and a default would invent
                    // provenance, so the table is rebuilt. The store is derived audit data in a new
                    // project: rows written without a fingerprint are dropped, not migrated.
                    execute(connection, "drop table if exists fitness_results");
                    execute(connection, """
                            create table fitness_results (
                              candidate_id text primary key not null references candidates(id) on delete cascade,
                              raw_magnitude real not null,
                              decision text not null,
                              scoring_fingerprint text not null check (length(trim(scoring_fingerprint)) > 0),
                              evaluated_at text not null
                            )
                            """);
                    try (PreparedStatement statement = connection.prepareStatement(
                            "insert into schema_migrations(version) values (?)"
                    )) {
                        statement.setInt(1, 2);
                        statement.executeUpdate();
                    }
                }
                if (!migrationApplied(connection, 3)) {
                    // CHG-026: a generation is a comparison, so it is recorded as one thing rather
                    // than inferred by grouping candidate rows after the fact. The fingerprint and
                    // the spread are nullable because a generation where nothing produced evidence
                    // has neither, and inventing a zero there would read as "every candidate scored
                    // the same" — which is a finding, not an absence.
                    execute(connection, """
                            create table if not exists generations (
                              run_id text primary key not null check (length(trim(run_id)) > 0),
                              requested_count integer not null,
                              evaluated_count integer not null,
                              scoring_fingerprint text
                                check (scoring_fingerprint is null or length(trim(scoring_fingerprint)) > 0),
                              winner_candidate_id text,
                              spread real,
                              recorded_at text not null
                            )
                            """);
                    execute(connection, """
                            create table if not exists generation_rankings (
                              run_id text not null references generations(run_id) on delete cascade,
                              position integer not null,
                              candidate_id text not null references candidates(id) on delete cascade,
                              primary key (run_id, position)
                            )
                            """);
                    execute(connection, """
                            create table if not exists generation_unevaluated (
                              run_id text not null references generations(run_id) on delete cascade,
                              position integer not null,
                              candidate_reference text not null,
                              reason text not null,
                              primary key (run_id, position)
                            )
                            """);
                    try (PreparedStatement statement = connection.prepareStatement(
                            "insert into schema_migrations(version) values (?)"
                    )) {
                        statement.setInt(1, 3);
                        statement.executeUpdate();
                    }
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to migrate SQLite experiment metadata schema", exception);
        }
    }

    private Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (var statement = connection.createStatement()) {
            statement.execute("pragma foreign_keys = on");
        } catch (SQLException exception) {
            connection.close();
            throw exception;
        }
        return connection;
    }

    private static boolean migrationApplied(Connection connection, int version) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select 1 from schema_migrations where version = ?"
        )) {
            statement.setInt(1, version);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static void writeCandidate(Connection connection, Candidate candidate) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into candidates(id, mutation_id, branch_name, worktree_path, commit_sha)
                values (?, ?, ?, ?, ?)
                on conflict(id) do update set
                  mutation_id = excluded.mutation_id,
                  branch_name = excluded.branch_name,
                  worktree_path = excluded.worktree_path,
                  commit_sha = excluded.commit_sha
                """)) {
            statement.setString(1, candidate.id());
            statement.setString(2, candidate.mutationId());
            statement.setString(3, candidate.branchName());
            statement.setString(4, candidate.worktreePath().toString());
            statement.setString(5, candidate.commitSha());
            statement.executeUpdate();
        }
    }

    private static void deleteFitnessChildren(Connection connection, String candidateId) throws SQLException {
        deleteByCandidateId(connection, "fitness_objectives", candidateId);
        deleteByCandidateId(connection, "evaluation_checks", candidateId);
        deleteByCandidateId(connection, "evaluation_benchmarks", candidateId);
    }

    private static void deleteByCandidateId(Connection connection, String table, String candidateId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "delete from " + table + " where candidate_id = ?"
        )) {
            statement.setString(1, candidateId);
            statement.executeUpdate();
        }
    }

    private static void writeFitnessResult(Connection connection, FitnessResult result) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert or replace into fitness_results(
                  candidate_id, raw_magnitude, decision, scoring_fingerprint, evaluated_at)
                values (?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, result.candidate().id());
            statement.setBigDecimal(2, result.fitnessScore().rawMagnitude());
            statement.setString(3, result.decision().name());
            statement.setString(4, result.scoringFingerprint());
            statement.setString(5, result.evidence().evaluatedAt().toString());
            statement.executeUpdate();
        }
    }

    private static void writeObjectives(Connection connection, FitnessResult result) throws SQLException {
        var objectives = new ArrayList<>(result.objectives().entrySet());
        objectives.sort(Map.Entry.comparingByKey());
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into fitness_objectives(candidate_id, name, value)
                values (?, ?, ?)
                """)) {
            for (Map.Entry<String, Double> objective : objectives) {
                statement.setString(1, result.candidate().id());
                statement.setString(2, objective.getKey());
                statement.setDouble(3, objective.getValue());
                statement.executeUpdate();
            }
        }
    }

    private static void writeChecks(Connection connection, FitnessResult result) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into evaluation_checks(candidate_id, position, name, status, summary)
                values (?, ?, ?, ?, ?)
                """)) {
            int position = 0;
            for (CheckEvidence check : result.evidence().checks()) {
                statement.setString(1, result.candidate().id());
                statement.setInt(2, position);
                statement.setString(3, check.name());
                statement.setString(4, check.status().name());
                statement.setString(5, check.summary());
                statement.executeUpdate();
                position++;
            }
        }
    }

    private static void writeBenchmarks(Connection connection, FitnessResult result) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into evaluation_benchmarks(candidate_id, position, name, value, unit)
                values (?, ?, ?, ?, ?)
                """)) {
            int position = 0;
            for (BenchmarkEvidence benchmark : result.evidence().benchmarks()) {
                statement.setString(1, result.candidate().id());
                statement.setInt(2, position);
                statement.setString(3, benchmark.name());
                statement.setDouble(4, benchmark.value());
                statement.setString(5, benchmark.unit());
                statement.executeUpdate();
                position++;
            }
        }
    }
}
