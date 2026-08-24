package com.dreamthought.saaa.adapters.sqlite;

import com.dreamthought.saaa.deterministic.ExperimentMetadataStore;
import com.dreamthought.saaa.domain.BenchmarkEvidence;
import com.dreamthought.saaa.domain.Candidate;
import com.dreamthought.saaa.domain.CheckEvidence;
import com.dreamthought.saaa.domain.FitnessResult;
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
