package com.dreamthought.saaa.adapters.sqlite;

import com.dreamthought.saaa.deterministic.EvolutionaryMemoryArchive;
import com.dreamthought.saaa.domain.BenchmarkEvidence;
import com.dreamthought.saaa.domain.CheckEvidence;
import com.dreamthought.saaa.domain.CheckStatus;
import com.dreamthought.saaa.domain.EvolutionContext;
import com.dreamthought.saaa.domain.EvolutionaryMemoryRecord;
import com.dreamthought.saaa.domain.FitnessDecision;
import com.dreamthought.saaa.domain.FitnessScore;
import com.dreamthought.saaa.domain.MutationScope;
import com.dreamthought.saaa.domain.RetrievalMode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Durable local evaluation ledger sharing the experiment database, but not its metadata port. */
public final class SqliteEvolutionaryMemoryStore implements EvolutionaryMemoryArchive {
    private final String jdbcUrl;

    public SqliteEvolutionaryMemoryStore(Path databasePath) {
        Path absolute = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
        try {
            if (absolute.getParent() != null) Files.createDirectories(absolute.getParent());
        } catch (IOException exception) {
            throw new IllegalStateException("failed to create experiment ledger directory", exception);
        }
        jdbcUrl = "jdbc:sqlite:" + absolute;
        migrate();
    }

    @Override
    public void append(EvolutionaryMemoryRecord record) {
        Objects.requireNonNull(record, "record");
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try (PreparedStatement attempt = connection.prepareStatement("""
                    insert into evolutionary_memory(
                      candidate_id, subject_repository_id, baseline_repository_revision,
                      process_repository_id, process_repository_revision, memory_policy_id,
                      mutation_id, mutation_summary, mutation_scope, candidate_commit, retrieval_mode,
                      retrieval_configuration_id, raw_magnitude, decision, scoring_fingerprint,
                      evaluated_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    on conflict(candidate_id) do update set
                      subject_repository_id=excluded.subject_repository_id,
                      baseline_repository_revision=excluded.baseline_repository_revision,
                      process_repository_id=excluded.process_repository_id,
                      process_repository_revision=excluded.process_repository_revision,
                      memory_policy_id=excluded.memory_policy_id,
                      mutation_id=excluded.mutation_id, mutation_summary=excluded.mutation_summary,
                      mutation_scope=excluded.mutation_scope, candidate_commit=excluded.candidate_commit,
                      retrieval_mode=excluded.retrieval_mode,
                      retrieval_configuration_id=excluded.retrieval_configuration_id,
                      raw_magnitude=excluded.raw_magnitude, decision=excluded.decision,
                      scoring_fingerprint=excluded.scoring_fingerprint,
                      evaluated_at=excluded.evaluated_at
                    """)) {
                EvolutionContext context = record.evolutionContext();
                attempt.setString(1, record.candidateId());
                attempt.setString(2, context.subjectRepositoryId());
                attempt.setString(3, context.subjectRepositoryRevision());
                attempt.setString(4, context.processRepositoryId());
                attempt.setString(5, context.processRepositoryRevision());
                attempt.setString(6, record.memoryPolicyId());
                attempt.setString(7, record.mutationId());
                attempt.setString(8, record.mutationSummary());
                attempt.setString(9, record.mutationScope().name());
                attempt.setString(10, record.candidateCommit());
                attempt.setString(11, record.retrievalMode().name());
                attempt.setString(12, record.retrievalConfigurationId());
                attempt.setBigDecimal(13, record.fitnessScore().rawMagnitude());
                attempt.setString(14, record.fitnessScore().decision().name());
                attempt.setString(15, record.scoringFingerprint());
                attempt.setString(16, record.evaluatedAt().toString());
                attempt.executeUpdate();
                deleteChildren(connection, record.candidateId());
                writeChangedPaths(connection, record);
                writeEvidence(connection, record);
                writeChecks(connection, record);
                writeBenchmarks(connection, record);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to append evolutionary memory", exception);
        }
    }

    @Override
    public List<EvolutionaryMemoryRecord> records() {
        var records = new ArrayList<EvolutionaryMemoryRecord>();
        try (Connection connection = connect(); var statement = connection.prepareStatement("""
                select * from evolutionary_memory order by evaluated_at, candidate_id
                """); var rows = statement.executeQuery()) {
            while (rows.next()) {
                String candidateId = rows.getString("candidate_id");
                records.add(new EvolutionaryMemoryRecord(
                        new EvolutionContext(
                                rows.getString("subject_repository_id"),
                                rows.getString("baseline_repository_revision"),
                                rows.getString("process_repository_id"),
                                rows.getString("process_repository_revision")),
                        rows.getString("memory_policy_id"),
                        rows.getString("mutation_id"), rows.getString("mutation_summary"),
                        MutationScope.valueOf(rows.getString("mutation_scope")), candidateId,
                        rows.getString("candidate_commit"), RetrievalMode.valueOf(rows.getString("retrieval_mode")),
                        rows.getString("retrieval_configuration_id"), readChangedPaths(connection, candidateId),
                        readEvidence(connection, candidateId),
                        readChecks(connection, candidateId), readBenchmarks(connection, candidateId),
                        new FitnessScore(rows.getBigDecimal("raw_magnitude"),
                                FitnessDecision.valueOf(rows.getString("decision"))),
                        rows.getString("scoring_fingerprint"),
                        Instant.parse(rows.getString("evaluated_at"))));
            }
            return List.copyOf(records);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read evolutionary memory", exception);
        }
    }

    private void migrate() {
        try (Connection connection = connect(); var statement = connection.createStatement()) {
            // create table if not exists never revisits a schema decision: a database created while
            // the column defaulted keeps defaulting on every later open. The ledger is derived and
            // rebuildable, so a stale schema is dropped rather than migrated - an old row could
            // never supply the fingerprint it was written without.
            if (schemaDefaultsTheFingerprint(connection)) {
                for (String table : List.of("evolutionary_memory_changed_paths",
                        "evolutionary_memory_evidence", "evolutionary_memory_checks",
                        "evolutionary_memory_benchmarks", "evolutionary_memory")) {
                    statement.execute("drop table if exists " + table);
                }
            }
            // scoring_fingerprint: required, with no schema-supplied value. A row that cannot say
            // what its magnitude was measured against would be ranked under invented provenance.
            // NOT NULL alone still accepts the empty string, which is why the CHECK exists.
            // (This rationale stays out of the DDL text itself: sqlite_master stores it, and the
            // stale-schema detection above reads that text.)
            statement.execute("""
                    create table if not exists evolutionary_memory (
                      candidate_id text primary key not null,
                      subject_repository_id text not null, baseline_repository_revision text not null,
                      process_repository_id text not null, process_repository_revision text not null,
                      memory_policy_id text not null, mutation_id text not null, mutation_summary text not null,
                      mutation_scope text not null, candidate_commit text not null, retrieval_mode text not null,
                      retrieval_configuration_id text not null, raw_magnitude real not null,
                      decision text not null,
                      scoring_fingerprint text not null check (length(trim(scoring_fingerprint)) > 0),
                      evaluated_at text not null
                    )
                    """);
            statement.execute("""
                    create table if not exists evolutionary_memory_changed_paths (
                      candidate_id text not null references evolutionary_memory(candidate_id) on delete cascade,
                      position integer not null, path text not null, primary key(candidate_id, position)
                    )
                    """);
            statement.execute("""
                    create table if not exists evolutionary_memory_evidence (
                      candidate_id text not null references evolutionary_memory(candidate_id) on delete cascade,
                      position integer not null, evidence_id text not null, primary key(candidate_id, position)
                    )
                    """);
            statement.execute("""
                    create table if not exists evolutionary_memory_checks (
                      candidate_id text not null references evolutionary_memory(candidate_id) on delete cascade,
                      position integer not null, name text not null, status text not null, summary text not null,
                      primary key(candidate_id, position)
                    )
                    """);
            statement.execute("""
                    create table if not exists evolutionary_memory_benchmarks (
                      candidate_id text not null references evolutionary_memory(candidate_id) on delete cascade,
                      position integer not null, name text not null, value real not null, unit text not null,
                      primary key(candidate_id, position)
                    )
                    """);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to migrate evolutionary memory ledger", exception);
        }
    }

    private static boolean schemaDefaultsTheFingerprint(Connection connection) throws SQLException {
        // Matches an actual default clause on scoring_fingerprint, not prose: sqlite_master stores
        // the CREATE TABLE text verbatim, comments included, so a looser substring match would
        // rename this guard into a drop-everything-on-every-open bug.
        try (var rows = connection.createStatement().executeQuery(
                "select sql from sqlite_master where name = 'evolutionary_memory'")) {
            return rows.next() && rows.getString(1).toLowerCase()
                    .contains("scoring_fingerprint text not null default");
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

    private static void deleteChildren(Connection connection, String candidateId) throws SQLException {
        for (String table : List.of(
                "evolutionary_memory_changed_paths", "evolutionary_memory_evidence",
                "evolutionary_memory_checks", "evolutionary_memory_benchmarks")) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "delete from " + table + " where candidate_id=?")) {
                statement.setString(1, candidateId);
                statement.executeUpdate();
            }
        }
    }

    private static void writeChangedPaths(Connection connection, EvolutionaryMemoryRecord record)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into evolutionary_memory_changed_paths values (?, ?, ?)")) {
            for (int i = 0; i < record.changedPaths().size(); i++) {
                statement.setString(1, record.candidateId()); statement.setInt(2, i);
                statement.setString(3, record.changedPaths().get(i)); statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void writeEvidence(Connection connection, EvolutionaryMemoryRecord record) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into evolutionary_memory_evidence values (?, ?, ?)")) {
            for (int i = 0; i < record.retrievedEvidenceIds().size(); i++) {
                statement.setString(1, record.candidateId()); statement.setInt(2, i);
                statement.setString(3, record.retrievedEvidenceIds().get(i)); statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void writeChecks(Connection connection, EvolutionaryMemoryRecord record) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into evolutionary_memory_checks values (?, ?, ?, ?, ?)")) {
            for (int i = 0; i < record.checks().size(); i++) {
                CheckEvidence check = record.checks().get(i);
                statement.setString(1, record.candidateId()); statement.setInt(2, i);
                statement.setString(3, check.name()); statement.setString(4, check.status().name());
                statement.setString(5, check.summary()); statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void writeBenchmarks(Connection connection, EvolutionaryMemoryRecord record) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into evolutionary_memory_benchmarks values (?, ?, ?, ?, ?)")) {
            for (int i = 0; i < record.benchmarks().size(); i++) {
                BenchmarkEvidence benchmark = record.benchmarks().get(i);
                statement.setString(1, record.candidateId()); statement.setInt(2, i);
                statement.setString(3, benchmark.name()); statement.setDouble(4, benchmark.value());
                statement.setString(5, benchmark.unit()); statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static List<String> readEvidence(Connection connection, String candidateId) throws SQLException {
        var values = new ArrayList<String>();
        try (PreparedStatement statement = connection.prepareStatement("""
                select evidence_id from evolutionary_memory_evidence where candidate_id=? order by position
                """)) {
            statement.setString(1, candidateId);
            try (var rows = statement.executeQuery()) { while (rows.next()) values.add(rows.getString(1)); }
        }
        return values;
    }

    private static List<String> readChangedPaths(Connection connection, String candidateId) throws SQLException {
        var values = new ArrayList<String>();
        try (PreparedStatement statement = connection.prepareStatement("""
                select path from evolutionary_memory_changed_paths where candidate_id=? order by position
                """)) {
            statement.setString(1, candidateId);
            try (var rows = statement.executeQuery()) { while (rows.next()) values.add(rows.getString(1)); }
        }
        return values;
    }

    private static List<CheckEvidence> readChecks(Connection connection, String candidateId) throws SQLException {
        var values = new ArrayList<CheckEvidence>();
        try (PreparedStatement statement = connection.prepareStatement("""
                select name, status, summary from evolutionary_memory_checks where candidate_id=? order by position
                """)) {
            statement.setString(1, candidateId);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) values.add(new CheckEvidence(
                        rows.getString(1), CheckStatus.valueOf(rows.getString(2)), rows.getString(3)));
            }
        }
        return values;
    }

    private static List<BenchmarkEvidence> readBenchmarks(Connection connection, String candidateId)
            throws SQLException {
        var values = new ArrayList<BenchmarkEvidence>();
        try (PreparedStatement statement = connection.prepareStatement("""
                select name, value, unit from evolutionary_memory_benchmarks where candidate_id=? order by position
                """)) {
            statement.setString(1, candidateId);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) values.add(new BenchmarkEvidence(
                        rows.getString(1), rows.getDouble(2), rows.getString(3)));
            }
        }
        return values;
    }
}
