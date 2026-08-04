package com.dreamthought.saaa.adapters.sqlite;

import com.dreamthought.saaa.deterministic.EvidenceCapsuleCache;
import com.dreamthought.saaa.deterministic.EmbeddingCache;
import com.dreamthought.saaa.deterministic.RetrievalProvenanceStore;
import com.dreamthought.saaa.domain.EvidenceAuthority;
import com.dreamthought.saaa.domain.EvidenceCapsuleProjection;
import com.dreamthought.saaa.domain.EvidenceLink;
import com.dreamthought.saaa.domain.EvidenceSubject;
import com.dreamthought.saaa.domain.HistoricalOutcome;
import com.dreamthought.saaa.domain.RelationshipType;
import com.dreamthought.saaa.domain.RetrievalProvenance;
import com.dreamthought.saaa.domain.SourceReference;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Separate local projection store for context compilation and retrieval audit data. */
public final class SqliteRetrievalProjectionStore
        implements EvidenceCapsuleCache, EmbeddingCache, RetrievalProvenanceStore {
    private final String jdbcUrl;

    public SqliteRetrievalProjectionStore(Path databasePath) {
        Objects.requireNonNull(databasePath, "databasePath");
        Path absolute = databasePath.toAbsolutePath().normalize();
        try {
            if (absolute.getParent() != null) {
                Files.createDirectories(absolute.getParent());
            }
        } catch (IOException exception) {
            throw new IllegalStateException("failed to create retrieval projection directory", exception);
        }
        jdbcUrl = "jdbc:sqlite:" + absolute;
        migrate();
    }

    @Override
    public Optional<EvidenceCapsuleProjection> find(
            String logicalSubject, String subjectRevision, String projectionVersion) {
        String sql = """
                select stable_id, logical_id, kind, subject_revision, projection_version, summary,
                       authority, status, estimated_tokens
                  from evidence_capsules
                 where logical_id = ? and subject_revision = ? and projection_version = ?
                """;
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, logicalSubject);
            statement.setString(2, subjectRevision);
            statement.setString(3, projectionVersion);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                String stableId = rows.getString("stable_id");
                return Optional.of(new EvidenceCapsuleProjection(
                        new EvidenceSubject(stableId, rows.getString("logical_id"), rows.getString("kind")),
                        rows.getString("subject_revision"), rows.getString("projection_version"),
                        rows.getString("summary"), EvidenceAuthority.valueOf(rows.getString("authority")),
                        rows.getString("status"), readLinks(connection, stableId, subjectRevision, projectionVersion),
                        readOutcomes(connection, stableId, subjectRevision, projectionVersion),
                        readSources(connection, stableId, subjectRevision, projectionVersion),
                        rows.getInt("estimated_tokens")));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read evidence capsule projection", exception);
        }
    }

    @Override
    public void put(EvidenceCapsuleProjection projection) {
        Objects.requireNonNull(projection, "projection");
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                writeCapsule(connection, projection);
                deleteChildren(connection, projection);
                writeSources(connection, projection);
                writeLinks(connection, projection);
                writeOutcomes(connection, projection);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to write evidence capsule projection", exception);
        }
    }

    @Override
    public void record(String queryFingerprint, RetrievalProvenance provenance) {
        Objects.requireNonNull(queryFingerprint, "queryFingerprint");
        Objects.requireNonNull(provenance, "provenance");
        String attemptId = UUID.randomUUID().toString();
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try (PreparedStatement attempt = connection.prepareStatement("""
                    insert into retrieval_attempts(
                      id, query_fingerprint, repository_revision, retrieval_mode, configuration_id,
                      graph_schema_version, capsule_projection_version, ranking_version, embedding_model_id,
                      memory_policy_id, flattened_context, exact_candidates, vector_candidates, graph_nodes_considered,
                      deduplicated_candidates, cache_hits, cache_misses, historical_weight_cap)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """);
                PreparedStatement evidence = connection.prepareStatement("""
                    insert into retrieval_attempt_evidence(attempt_id, position, evidence_id) values (?, ?, ?)
                    """)) {
                attempt.setString(1, attemptId);
                attempt.setString(2, queryFingerprint);
                attempt.setString(3, provenance.repositoryRevision());
                attempt.setString(4, provenance.mode().name());
                attempt.setString(5, provenance.configurationId());
                attempt.setString(6, provenance.graphSchemaVersion());
                attempt.setString(7, provenance.capsuleProjectionVersion());
                attempt.setString(8, provenance.rankingVersion());
                attempt.setString(9, provenance.embeddingModelId());
                attempt.setString(10, provenance.memoryPolicyId());
                attempt.setString(11, provenance.flattenedContext());
                attempt.setInt(12, provenance.diagnostics().exactCandidates());
                attempt.setInt(13, provenance.diagnostics().vectorCandidates());
                attempt.setInt(14, provenance.diagnostics().graphNodesConsidered());
                attempt.setInt(15, provenance.diagnostics().deduplicatedCandidates());
                attempt.setInt(16, provenance.diagnostics().cacheHits());
                attempt.setInt(17, provenance.diagnostics().cacheMisses());
                attempt.setDouble(18, provenance.diagnostics().historicalWeightCap());
                attempt.executeUpdate();
                for (int position = 0; position < provenance.evidenceIds().size(); position++) {
                    evidence.setString(1, attemptId);
                    evidence.setInt(2, position);
                    evidence.setString(3, provenance.evidenceIds().get(position));
                    evidence.addBatch();
                }
                evidence.executeBatch();
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to record retrieval provenance", exception);
        }
    }

    @Override
    public Optional<List<Float>> find(String modelId, String contentHash, int dimensions) {
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement("""
                select dimensions, vector from embedding_cache where model_id=? and content_hash=?
                """)) {
            statement.setString(1, modelId);
            statement.setString(2, contentHash);
            try (var rows = statement.executeQuery()) {
                if (!rows.next() || rows.getInt("dimensions") != dimensions) {
                    return Optional.empty();
                }
                ByteBuffer bytes = ByteBuffer.wrap(rows.getBytes("vector")).order(ByteOrder.BIG_ENDIAN);
                var vector = new ArrayList<Float>(dimensions);
                while (bytes.remaining() >= Float.BYTES) {
                    vector.add(bytes.getFloat());
                }
                return vector.size() == dimensions ? Optional.of(List.copyOf(vector)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read embedding cache", exception);
        }
    }

    @Override
    public void put(String modelId, String contentHash, List<Float> embedding) {
        ByteBuffer bytes = ByteBuffer.allocate(embedding.size() * Float.BYTES).order(ByteOrder.BIG_ENDIAN);
        embedding.forEach(bytes::putFloat);
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement("""
                insert into embedding_cache(model_id, content_hash, dimensions, vector)
                values (?, ?, ?, ?)
                on conflict(model_id, content_hash) do update set
                  dimensions=excluded.dimensions, vector=excluded.vector
                """)) {
            statement.setString(1, modelId);
            statement.setString(2, contentHash);
            statement.setInt(3, embedding.size());
            statement.setBytes(4, bytes.array());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to write embedding cache", exception);
        }
    }

    public int retrievalAttemptCount() {
        try (Connection connection = connect(); var statement = connection.createStatement();
             var rows = statement.executeQuery("select count(*) from retrieval_attempts")) {
            return rows.next() ? rows.getInt(1) : 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to count retrieval attempts", exception);
        }
    }

    private void migrate() {
        try (Connection connection = connect(); var statement = connection.createStatement()) {
            statement.execute("""
                    create table if not exists evidence_capsules (
                      stable_id text not null, logical_id text not null, kind text not null,
                      subject_revision text not null, projection_version text not null,
                      summary text not null, authority text not null, status text not null,
                      estimated_tokens integer not null,
                      primary key (stable_id, subject_revision, projection_version),
                      unique (logical_id, subject_revision, projection_version)
                    )
                    """);
            statement.execute("""
                    create table if not exists evidence_capsule_sources (
                      stable_id text not null, subject_revision text not null, projection_version text not null,
                      position integer not null, path text not null, anchor text not null,
                      primary key (stable_id, subject_revision, projection_version, position)
                    )
                    """);
            statement.execute("""
                    create table if not exists evidence_capsule_links (
                      stable_id text not null, subject_revision text not null, projection_version text not null,
                      position integer not null, relationship text not null, target_id text not null, description text not null,
                      primary key (stable_id, subject_revision, projection_version, position)
                    )
                    """);
            statement.execute("""
                    create table if not exists evidence_capsule_outcomes (
                      stable_id text not null, subject_revision text not null, projection_version text not null,
                      position integer not null, evaluation_id text not null, decision text not null,
                      fitness real not null, summary text not null,
                      primary key (stable_id, subject_revision, projection_version, position)
                    )
                    """);
            statement.execute("""
                    create table if not exists retrieval_attempts (
                      id text primary key not null, query_fingerprint text not null, repository_revision text not null,
                      retrieval_mode text not null, configuration_id text not null, graph_schema_version text not null,
                      capsule_projection_version text not null, ranking_version text not null,
                      embedding_model_id text not null, memory_policy_id text not null,
                      flattened_context text not null,
                      exact_candidates integer not null, vector_candidates integer not null,
                      graph_nodes_considered integer not null, deduplicated_candidates integer not null,
                      cache_hits integer not null, cache_misses integer not null, historical_weight_cap real not null
                    )
                    """);
            ensureColumn(connection, "retrieval_attempts", "memory_policy_id",
                    "alter table retrieval_attempts add column memory_policy_id text not null default 'lineage-novelty-v1'");
            statement.execute("""
                    create table if not exists embedding_cache (
                      model_id text not null, content_hash text not null, dimensions integer not null,
                      vector blob not null, primary key (model_id, content_hash)
                    )
                    """);
            statement.execute("""
                    create table if not exists retrieval_attempt_evidence (
                      attempt_id text not null references retrieval_attempts(id) on delete cascade,
                      position integer not null, evidence_id text not null,
                      primary key (attempt_id, position)
                    )
                    """);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to migrate retrieval projection schema", exception);
        }
    }

    private Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (var statement = connection.createStatement()) {
            statement.execute("pragma foreign_keys = on");
        }
        return connection;
    }

    private static void ensureColumn(Connection connection, String table, String column, String alterSql)
            throws SQLException {
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("pragma table_info(" + table + ")")) {
            while (rows.next()) {
                if (column.equals(rows.getString("name"))) return;
            }
        }
        try (var statement = connection.createStatement()) {
            statement.execute(alterSql);
        }
    }

    private static void writeCapsule(Connection connection, EvidenceCapsuleProjection value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into evidence_capsules(stable_id, logical_id, kind, subject_revision, projection_version,
                  summary, authority, status, estimated_tokens) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict(stable_id, subject_revision, projection_version) do update set
                  logical_id=excluded.logical_id, kind=excluded.kind, summary=excluded.summary,
                  authority=excluded.authority, status=excluded.status, estimated_tokens=excluded.estimated_tokens
                """)) {
            statement.setString(1, value.subject().stableId());
            statement.setString(2, value.subject().logicalId());
            statement.setString(3, value.subject().kind());
            statement.setString(4, value.revision());
            statement.setString(5, value.projectionVersion());
            statement.setString(6, value.summary());
            statement.setString(7, value.authority().name());
            statement.setString(8, value.status());
            statement.setInt(9, value.estimatedTokens());
            statement.executeUpdate();
        }
    }

    private static void deleteChildren(Connection connection, EvidenceCapsuleProjection value) throws SQLException {
        for (String table : List.of("evidence_capsule_sources", "evidence_capsule_links", "evidence_capsule_outcomes")) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "delete from " + table + " where stable_id=? and subject_revision=? and projection_version=?")) {
                setKey(statement, value.subject().stableId(), value.revision(), value.projectionVersion());
                statement.executeUpdate();
            }
        }
    }

    private static void writeSources(Connection connection, EvidenceCapsuleProjection value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into evidence_capsule_sources values (?, ?, ?, ?, ?, ?)
                """)) {
            for (int i = 0; i < value.sources().size(); i++) {
                setKey(statement, value.subject().stableId(), value.revision(), value.projectionVersion());
                statement.setInt(4, i);
                statement.setString(5, value.sources().get(i).path());
                statement.setString(6, value.sources().get(i).anchor());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void writeLinks(Connection connection, EvidenceCapsuleProjection value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into evidence_capsule_links values (?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (int i = 0; i < value.links().size(); i++) {
                EvidenceLink link = value.links().get(i);
                setKey(statement, value.subject().stableId(), value.revision(), value.projectionVersion());
                statement.setInt(4, i);
                statement.setString(5, link.relationship().name());
                statement.setString(6, link.targetId());
                statement.setString(7, link.description());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void writeOutcomes(Connection connection, EvidenceCapsuleProjection value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into evidence_capsule_outcomes values (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (int i = 0; i < value.historicalOutcomes().size(); i++) {
                HistoricalOutcome outcome = value.historicalOutcomes().get(i);
                setKey(statement, value.subject().stableId(), value.revision(), value.projectionVersion());
                statement.setInt(4, i);
                statement.setString(5, outcome.evaluationId());
                statement.setString(6, outcome.decision());
                statement.setDouble(7, outcome.fitness());
                statement.setString(8, outcome.summary());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static List<SourceReference> readSources(Connection connection, String id, String rev, String version)
            throws SQLException {
        var values = new ArrayList<SourceReference>();
        try (PreparedStatement statement = connection.prepareStatement("""
                select path, anchor from evidence_capsule_sources
                 where stable_id=? and subject_revision=? and projection_version=? order by position
                """)) {
            setKey(statement, id, rev, version);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) values.add(new SourceReference(rows.getString(1), rows.getString(2)));
            }
        }
        return values;
    }

    private static List<EvidenceLink> readLinks(Connection connection, String id, String rev, String version)
            throws SQLException {
        var values = new ArrayList<EvidenceLink>();
        try (PreparedStatement statement = connection.prepareStatement("""
                select relationship, target_id, description from evidence_capsule_links
                 where stable_id=? and subject_revision=? and projection_version=? order by position
                """)) {
            setKey(statement, id, rev, version);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) values.add(new EvidenceLink(
                        RelationshipType.valueOf(rows.getString(1)), rows.getString(2), rows.getString(3)));
            }
        }
        return values;
    }

    private static List<HistoricalOutcome> readOutcomes(
            Connection connection, String id, String rev, String version) throws SQLException {
        var values = new ArrayList<HistoricalOutcome>();
        try (PreparedStatement statement = connection.prepareStatement("""
                select evaluation_id, decision, fitness, summary from evidence_capsule_outcomes
                 where stable_id=? and subject_revision=? and projection_version=? order by position
                """)) {
            setKey(statement, id, rev, version);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) values.add(new HistoricalOutcome(
                        rows.getString(1), rows.getString(2), rows.getDouble(3), rows.getString(4)));
            }
        }
        return values;
    }

    private static void setKey(PreparedStatement statement, String id, String revision, String version)
            throws SQLException {
        statement.setString(1, id);
        statement.setString(2, revision);
        statement.setString(3, version);
    }

}
