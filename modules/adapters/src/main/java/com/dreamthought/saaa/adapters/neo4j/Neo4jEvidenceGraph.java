package com.dreamthought.saaa.adapters.neo4j;

import static org.neo4j.driver.Values.parameters;

import com.dreamthought.saaa.deterministic.EvidenceProjectionStore;
import com.dreamthought.saaa.deterministic.EvidenceSearch;
import com.dreamthought.saaa.deterministic.EmbeddedEvidenceProjectionStore;
import com.dreamthought.saaa.deterministic.VectorEvidenceStore;
import com.dreamthought.saaa.deterministic.EvolutionaryMemoryStore;
import com.dreamthought.saaa.domain.EmbeddedRepositoryProjection;
import com.dreamthought.saaa.domain.EvolutionaryMemoryRecord;
import com.dreamthought.saaa.domain.EvolutionaryMemoryProjectionStatus;
import com.dreamthought.saaa.domain.EvidenceLink;
import com.dreamthought.saaa.domain.HistoricalOutcome;
import com.dreamthought.saaa.domain.CheckStatus;
import com.dreamthought.saaa.domain.EvidenceAuthority;
import com.dreamthought.saaa.domain.EvidenceDocument;
import com.dreamthought.saaa.domain.GraphEdge;
import com.dreamthought.saaa.domain.ProjectionStatus;
import com.dreamthought.saaa.domain.RelationshipType;
import com.dreamthought.saaa.domain.RepositoryProjection;
import com.dreamthought.saaa.domain.SourceReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Config;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import java.util.concurrent.TimeUnit;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Neo4j Community projection and bounded search adapter. */
public final class Neo4jEvidenceGraph
        implements EmbeddedEvidenceProjectionStore, EvidenceSearch, VectorEvidenceStore,
        EvolutionaryMemoryStore, AutoCloseable {
    private static final Set<String> LABELS = Set.of(
            "Repository", "Module", "SourceFile", "Type", "Test", "Capability", "ChangeSpec",
            "ArchitectureDecision", "ArchitectureKnowledge", "KnowledgeEntry", "Risk", "Question",
            "Mutation", "Candidate", "Evaluation", "Check", "Benchmark", "EvolutionContext",
            "RepositoryRef", "RetrievalConfiguration", "ChangedPath");

    private final Driver driver;
    private final Neo4jConfig config;

    public static Neo4jEvidenceGraph connect(Neo4jConfig config) {
        Driver driver = GraphDatabase.driver(
                config.uri(),
                AuthTokens.basic(config.user(), config.password()),
                Config.builder().withConnectionTimeout(3, TimeUnit.SECONDS).build());
        try {
            driver.verifyConnectivity();
            return new Neo4jEvidenceGraph(driver, config);
        } catch (RuntimeException exception) {
            driver.close();
            throw new IllegalStateException(
                    "configured Neo4j retrieval is unavailable at " + config.uri()
                            + "; retrieval treatment was not changed",
                    exception);
        }
    }

    Neo4jEvidenceGraph(Driver driver, Neo4jConfig config) {
        this.driver = Objects.requireNonNull(driver, "driver");
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public void replaceRepositoryProjection(RepositoryProjection projection) {
        replace(projection, Optional.empty());
    }

    @Override
    public void replaceEmbeddedRepositoryProjection(EmbeddedRepositoryProjection projection) {
        replace(projection.repositoryProjection(), Optional.of(projection));
    }

    private void replace(RepositoryProjection projection, Optional<EmbeddedRepositoryProjection> embedded) {
        Objects.requireNonNull(projection, "projection");
        if (!config.repositoryId().equals(projection.repositoryId())) {
            throw new IllegalArgumentException("projection repository id does not match configured repository");
        }
        try (Session session = session()) {
            Optional<String> previousVectorIndex = optionalString(session.run("""
                    OPTIONAL MATCH (p:Projection {repositoryId:$repositoryId, source:'repository'})
                    RETURN p.embeddingIndexName AS embeddingIndexName
                    """, parameters("repositoryId", config.repositoryId())).single(), "embeddingIndexName");
            session.run("""
                    CREATE CONSTRAINT evidence_stable_key IF NOT EXISTS
                    FOR (n:Evidence) REQUIRE n.stableKey IS UNIQUE
                    """).consume();
            Optional<String> nextVectorIndex = embedded.map(value ->
                    vectorIndexName(config.repositoryId(), value.embeddingModelId(), value.dimensions()));
            previousVectorIndex.filter(previous -> !nextVectorIndex.map(previous::equals).orElse(false))
                    .ifPresent(previous ->
                    session.run("DROP INDEX `%s` IF EXISTS".formatted(previous)).consume());
            embedded.ifPresent(value -> {
                String index = nextVectorIndex.orElseThrow();
                session.run("""
                        CREATE VECTOR INDEX `%s` IF NOT EXISTS
                        FOR (n:`%s`) ON (n.embedding)
                        OPTIONS {indexConfig: {`vector.dimensions`: %d, `vector.similarity_function`: 'cosine'}}
                        """.formatted(index, repositoryVectorLabel(), value.dimensions())).consume();
            });
            session.executeWrite(transaction -> {
                transaction.run(
                        "MATCH (n:Evidence {repositoryId: $repositoryId, projectionSource: 'repository'}) DETACH DELETE n",
                        Map.of("repositoryId", config.repositoryId())).consume();
                transaction.run(
                        "MATCH (p:Projection {repositoryId: $repositoryId, source: 'repository'}) DELETE p",
                        Map.of("repositoryId", config.repositoryId())).consume();

                for (EvidenceDocument node : projection.nodes()) {
                    String label = label(node.kind());
                    String sourcePath = node.sources().getFirst().path();
                    String sourceAnchor = node.sources().getFirst().anchor();
                    String embeddingProperties = embedded.isPresent()
                            ? ", embedding: $embedding, embeddingModelId: $embeddingModelId" : "";
                    Map<String, Object> values = new java.util.HashMap<>();
                    values.put("stableKey", stableKey(node.stableId()));
                    values.put("repositoryId", config.repositoryId());
                    values.put("repositoryRole", config.repositoryRole().name());
                    values.put("stableId", node.stableId());
                    values.put("logicalId", node.logicalId());
                    values.put("kind", node.kind());
                    values.put("revision", node.revision());
                    values.put("contentHash", node.contentHash());
                    values.put("semanticText", node.semanticText());
                    values.put("authority", node.authority().name());
                    values.put("status", node.status());
                    values.put("sourcePath", sourcePath);
                    values.put("sourceAnchor", sourceAnchor);
                    embedded.ifPresent(value -> {
                        values.put("embedding", value.embeddingsByStableId().get(node.stableId()));
                        values.put("embeddingModelId", value.embeddingModelId());
                    });
                    transaction.run("""
                            CREATE (n:Evidence:%s:`%s` {
                              stableKey: $stableKey,
                              repositoryId: $repositoryId,
                              repositoryRole: $repositoryRole,
                              projectionSource: 'repository',
                              stableId: $stableId,
                              logicalId: $logicalId,
                              kind: $kind,
                              revision: $revision,
                              contentHash: $contentHash,
                              semanticText: $semanticText,
                              authority: $authority,
                              status: $status,
                              sourcePath: $sourcePath,
                              sourceAnchor: $sourceAnchor%s
                            })
                            """.formatted(label, repositoryVectorLabel(), embeddingProperties), values).consume();
                }
                for (GraphEdge edge : projection.edges()) {
                    transaction.run("""
                            MATCH (source:Evidence {stableKey: $sourceKey})
                            MATCH (target:Evidence {stableKey: $targetKey})
                            CREATE (source)-[:%s {reason: $reason}]->(target)
                            """.formatted(edge.type().name()), parameters(
                            "sourceKey", stableKey(edge.sourceId()),
                            "targetKey", stableKey(edge.targetId()),
                            "reason", edge.reason())).consume();
                }
                Map<String, Object> projectionValues = new java.util.HashMap<>();
                projectionValues.put("repositoryId", config.repositoryId());
                projectionValues.put("repositoryRole", config.repositoryRole().name());
                projectionValues.put("revision", projection.repositoryRevision());
                projectionValues.put("schemaVersion", projection.schemaVersion());
                projectionValues.put("nodeCount", projection.nodes().size());
                projectionValues.put("relationshipCount", projection.edges().size());
                nextVectorIndex.ifPresent(index -> projectionValues.put("embeddingIndexName", index));
                String vectorProjectionProperty = nextVectorIndex.isPresent()
                        ? ", embeddingIndexName: $embeddingIndexName" : "";
                transaction.run("""
                        CREATE (:Projection {
                          repositoryId: $repositoryId,
                          source: 'repository',
                          repositoryRole: $repositoryRole,
                          revision: $revision,
                          schemaVersion: $schemaVersion,
                          nodeCount: $nodeCount,
                          relationshipCount: $relationshipCount%s
                        })
                        """.formatted(vectorProjectionProperty), projectionValues).consume();
                return null;
            });
        }
    }

    @Override
    public ProjectionStatus status() {
        try (Session session = session()) {
            var record = session.run("""
                    OPTIONAL MATCH (p:Projection {repositoryId: $repositoryId, source: 'repository'})
                    RETURN p.revision AS revision, p.schemaVersion AS schemaVersion,
                           coalesce(p.nodeCount, 0) AS nodeCount,
                           coalesce(p.relationshipCount, 0) AS relationshipCount
                    """, Map.of("repositoryId", config.repositoryId())).single();
            return new ProjectionStatus(
                    config.repositoryId(),
                    optionalString(record, "revision"),
                    optionalString(record, "schemaVersion"),
                    record.get("nodeCount").asInt(),
                    record.get("relationshipCount").asInt());
        }
    }

    public EvolutionaryMemoryProjectionStatus memoryStatus() {
        try (Session session = session()) {
            var record = session.run("""
                    OPTIONAL MATCH (p:MemoryProjection {repositoryId:$repositoryId})
                    RETURN p.policyId AS policyId, coalesce(p.activeEvaluationCount, 0) AS activeEvaluationCount
                    """, parameters("repositoryId", config.repositoryId())).single();
            return new EvolutionaryMemoryProjectionStatus(
                    optionalString(record, "policyId"), record.get("activeEvaluationCount").asInt());
        }
    }

    /** Inspectable proof that one evaluation relates its subject, process and retrieval strategy. */
    public boolean hasEvolutionContext(
            String candidateId,
            String subjectRepositoryId,
            String processRepositoryId,
            String retrievalConfigurationId) {
        try (Session session = session()) {
            return session.run("""
                    MATCH (candidate:Candidate {repositoryId:$repositoryId, candidateId:$candidateId})
                          -[:SCORED]->(evaluation:Evaluation {repositoryId:$repositoryId})
                    MATCH (context:EvolutionContext {repositoryId:$repositoryId})-[:OBSERVED]->(evaluation)
                    MATCH (context)-[:EVOLVES]->(:RepositoryRef {repositoryId:$subjectRepositoryId})
                    MATCH (context)-[:EXECUTES_WITH]->(:RepositoryRef {repositoryId:$processRepositoryId})
                    MATCH (context)-[:USES_CONFIG]->(:RetrievalConfiguration {
                          configurationId:$retrievalConfigurationId})
                    RETURN count(context) > 0 AS present
                    """, parameters(
                    "repositoryId", config.repositoryId(),
                    "candidateId", candidateId,
                    "subjectRepositoryId", subjectRepositoryId,
                    "processRepositoryId", processRepositoryId,
                    "retrievalConfigurationId", retrievalConfigurationId)).single().get("present").asBoolean();
        }
    }

    @Override
    public List<EvidenceDocument> resolveExact(List<String> identifiers) {
        if (identifiers.isEmpty()) {
            return List.of();
        }
        try (Session session = session()) {
            List<EvidenceDocument> documents = session.run("""
                    MATCH (n:Evidence {repositoryId: $repositoryId})
                    WHERE n.stableId IN $identifiers
                       OR n.logicalId IN $identifiers
                       OR n.sourcePath IN $identifiers
                    RETURN n
                    ORDER BY n.stableId
                    """, parameters("repositoryId", config.repositoryId(), "identifiers", identifiers))
                    .list(record -> document(record.get("n").asMap()));
            return withHistoricalOutcomes(documents);
        }
    }

    @Override
    public List<EvidenceDocument> vectorSearch(String semanticQuery, int limit) {
        throw new IllegalStateException(
                "VECTOR retrieval requires an embedded Neo4j projection; build the vector slice before using this mode");
    }

    @Override
    public List<EvidenceDocument> searchVector(
            String embeddingModelId, int dimensions, List<Float> query, int limit) {
        if (query.size() != dimensions || limit < 1) {
            throw new IllegalArgumentException("vector query dimensions or limit are invalid");
        }
        String index = vectorIndexName(config.repositoryId(), embeddingModelId, dimensions);
        try (Session session = session()) {
            List<EvidenceDocument> documents = session.run("""
                    CALL db.index.vector.queryNodes($indexName, $limit, $query)
                    YIELD node, score
                    WHERE node.repositoryId = $repositoryId AND node.embeddingModelId = $embeddingModelId
                    RETURN node, score
                    ORDER BY score DESC, node.stableId
                    """, parameters(
                    "indexName", index,
                    "limit", limit,
                    "query", query,
                    "repositoryId", config.repositoryId(),
                    "embeddingModelId", embeddingModelId)).list(record -> document(record.get("node").asMap()));
            return withHistoricalOutcomes(documents);
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "VECTOR retrieval requires an index built with embedding model " + embeddingModelId,
                    exception);
        }
    }

    @Override
    public List<EvidenceDocument> expand(
            List<String> seedIds,
            Set<RelationshipType> relationships,
            int depth,
            int maxFanOut
    ) {
        if (seedIds.isEmpty() || relationships.isEmpty()) {
            return List.of();
        }
        if (depth < 1 || depth > 2 || maxFanOut < 1) {
            throw new IllegalArgumentException("graph expansion bounds are invalid");
        }
        String relationshipPattern = relationships.stream()
                .sorted()
                .map(Enum::name)
                .collect(Collectors.joining("|"));
        String cypher = """
                MATCH (seed:Evidence {repositoryId: $repositoryId, stableId: $seedId})
                MATCH path=(seed)-[:%s*1..%d]-(n:Evidence {repositoryId: $repositoryId})
                WHERE n <> seed
                WITH seed, n, path
                ORDER BY length(path), [node IN nodes(path) | node.stableId]
                WITH seed, n, collect(path)[0] AS selectedPath
                RETURN n, seed.stableId AS seedId, length(selectedPath) AS distance,
                       [relationship IN relationships(selectedPath) | type(relationship)] AS relationshipTypes
                ORDER BY distance, n.stableId
                LIMIT $limit
                """.formatted(relationshipPattern, depth);
        try (Session session = session()) {
            var documents = new java.util.LinkedHashMap<String, EvidenceDocument>();
            for (String seedId : seedIds.stream().distinct().sorted().toList()) {
                session.run(cypher, parameters(
                        "repositoryId", config.repositoryId(), "seedId", seedId,
                        "limit", maxFanOut)).list(record -> document(
                                record.get("n").asMap(),
                                record.get("relationshipTypes").asList(value -> new EvidenceLink(
                                        RelationshipType.valueOf(value.asString()),
                                        record.get("seedId").asString(),
                                        "selected by bounded path to exact/vector seed"))))
                        .forEach(document -> documents.putIfAbsent(document.stableId(), document));
            }
            return withHistoricalOutcomes(List.copyOf(documents.values()));
        }
    }

    @Override
    public void append(EvolutionaryMemoryRecord record) {
        Objects.requireNonNull(record, "record");
        String evaluationId = "evaluation:" + record.candidateId();
        String summary = "decision=" + record.decision() + "; failed checks="
                + record.checks().stream().filter(check -> check.status() != CheckStatus.PASSED)
                        .map(check -> check.name() + ": " + check.summary()).toList();
        try (Session session = session()) {
            session.executeWrite(transaction -> {
                transaction.run("""
                        MERGE (m:Mutation {repositoryId:$repositoryId, mutationId:$mutationId})
                        SET m.summary=$mutationSummary
                        MERGE (c:Candidate {repositoryId:$repositoryId, candidateId:$candidateId})
                        SET c.commitSha=$candidateCommit
                        MERGE (c)-[:REALIZES]->(m)
                        MERGE (e:Evaluation {repositoryId:$repositoryId, evaluationId:$evaluationId})
                        SET e.fitness=$fitness, e.decision=$decision, e.summary=$summary,
                            e.evaluatedAt=$evaluatedAt, e.retrievalMode=$retrievalMode,
                            e.retrievalConfigurationId=$retrievalConfigurationId,
                            e.memoryPolicyId=$memoryPolicyId,
                            e.baselineRepositoryRevision=$baselineRepositoryRevision,
                            e.processRepositoryId=$processRepositoryId,
                            e.processRepositoryRevision=$processRepositoryRevision
                        MERGE (c)-[:SCORED]->(e)
                        MERGE (d:Decision {name:$decision})
                        MERGE (e)-[:DECISION]->(d)
                        MERGE (subject:RepositoryRef {repositoryId:$subjectRepositoryId})
                        SET subject.subject=true
                        MERGE (process:RepositoryRef {repositoryId:$processRepositoryId})
                        SET process.process=true
                        MERGE (configuration:RetrievalConfiguration {configurationId:$retrievalConfigurationId})
                        MERGE (context:EvolutionContext {repositoryId:$repositoryId, evaluationId:$evaluationId})
                        SET context.subjectRevision=$baselineRepositoryRevision,
                            context.processRevision=$processRepositoryRevision,
                            context.memoryPolicyId=$memoryPolicyId
                        MERGE (context)-[:EVOLVES]->(subject)
                        MERGE (context)-[:EXECUTES_WITH]->(process)
                        MERGE (context)-[:USES_CONFIG]->(configuration)
                        MERGE (context)-[:OBSERVED]->(e)
                        """, parameters(
                        "repositoryId", config.repositoryId(), "mutationId", record.mutationId(),
                        "mutationSummary", record.mutationSummary(), "candidateId", record.candidateId(),
                        "candidateCommit", record.candidateCommit(), "evaluationId", evaluationId,
                        "fitness", record.aggregateFitness(), "decision", record.decision().name(),
                        "summary", summary, "evaluatedAt", record.evaluatedAt().toString(),
                        "retrievalMode", record.retrievalMode().name(),
                        "retrievalConfigurationId", record.retrievalConfigurationId(),
                        "memoryPolicyId", record.memoryPolicyId(),
                        "subjectRepositoryId", record.evolutionContext().subjectRepositoryId(),
                        "baselineRepositoryRevision", record.evolutionContext().subjectRepositoryRevision(),
                        "processRepositoryId", record.evolutionContext().processRepositoryId(),
                        "processRepositoryRevision", record.evolutionContext().processRepositoryRevision())).consume();
                for (String evidenceId : record.retrievedEvidenceIds()) {
                    transaction.run("""
                            MATCH (c:Candidate {repositoryId:$repositoryId, candidateId:$candidateId})
                            MATCH (n:Evidence {repositoryId:$repositoryId, stableId:$evidenceId})
                            MERGE (c)-[r:RETRIEVED]->(n)
                            SET r.configurationId=$configurationId, r.mode=$mode
                            """, parameters("repositoryId", config.repositoryId(),
                            "candidateId", record.candidateId(), "evidenceId", evidenceId,
                            "configurationId", record.retrievalConfigurationId(),
                            "mode", record.retrievalMode().name())).consume();
                }
                for (String path : record.changedPaths()) {
                    transaction.run("""
                            MATCH (c:Candidate {repositoryId:$repositoryId, candidateId:$candidateId})
                            MERGE (p:ChangedPath {repositoryId:$repositoryId, path:$path})
                            MERGE (c)-[:CHANGED]->(p)
                            """, parameters("repositoryId", config.repositoryId(),
                            "candidateId", record.candidateId(), "path", path)).consume();
                    transaction.run("""
                            MATCH (c:Candidate {repositoryId:$repositoryId, candidateId:$candidateId})
                            MATCH (n:Evidence {repositoryId:$repositoryId, sourcePath:$path})
                            MERGE (c)-[:CHANGED]->(n)
                            """, parameters("repositoryId", config.repositoryId(),
                            "candidateId", record.candidateId(), "path", path)).consume();
                }
                for (var check : record.checks()) {
                    String checkId = evaluationId + "|check|" + check.name();
                    transaction.run("""
                            MATCH (c:Candidate {repositoryId:$repositoryId, candidateId:$candidateId})
                            MATCH (e:Evaluation {repositoryId:$repositoryId, evaluationId:$evaluationId})
                            MERGE (k:Check {repositoryId:$repositoryId, checkId:$checkId})
                            SET k.name=$name, k.status=$status, k.summary=$summary
                            MERGE (e)-[:RAN]->(k)
                            """, parameters("repositoryId", config.repositoryId(),
                            "candidateId", record.candidateId(), "evaluationId", evaluationId,
                            "checkId", checkId, "name", check.name(), "status", check.status().name(),
                            "summary", check.summary())).consume();
                    if (check.status() != CheckStatus.PASSED) {
                        transaction.run("""
                                MATCH (c:Candidate {repositoryId:$repositoryId, candidateId:$candidateId})
                                MATCH (k:Check {repositoryId:$repositoryId, checkId:$checkId})
                                MERGE (c)-[:FAILED]->(k)
                                """, parameters("repositoryId", config.repositoryId(),
                                "candidateId", record.candidateId(), "checkId", checkId)).consume();
                    }
                }
                for (var benchmark : record.benchmarks()) {
                    String benchmarkId = evaluationId + "|benchmark|" + benchmark.name();
                    transaction.run("""
                            MATCH (e:Evaluation {repositoryId:$repositoryId, evaluationId:$evaluationId})
                            MERGE (b:Benchmark {repositoryId:$repositoryId, benchmarkId:$benchmarkId})
                            SET b.name=$name, b.value=$value, b.unit=$unit
                            MERGE (e)-[:RAN]->(b)
                            """, parameters("repositoryId", config.repositoryId(), "evaluationId", evaluationId,
                            "benchmarkId", benchmarkId, "name", benchmark.name(),
                            "value", benchmark.value(), "unit", benchmark.unit())).consume();
                }
                return null;
            });
        }
    }

    /** Replaces only the bounded outcome working set; the durable SQLite/Git ledger is untouched. */
    public void replaceEvolutionaryMemory(List<EvolutionaryMemoryRecord> records, String policyId) {
        Objects.requireNonNull(records, "records");
        Objects.requireNonNull(policyId, "policyId");
        try (Session session = session()) {
            session.executeWrite(transaction -> {
                transaction.run("""
                        MATCH (n {repositoryId:$repositoryId})
                        WHERE n:Mutation OR n:Candidate OR n:Evaluation OR n:Check OR n:Benchmark
                           OR n:EvolutionContext OR n:MemoryProjection OR n:ChangedPath
                        DETACH DELETE n
                        """, parameters("repositoryId", config.repositoryId())).consume();
                transaction.run("""
                        MATCH (n)
                        WHERE (n:RepositoryRef OR n:RetrievalConfiguration) AND NOT (n)--()
                        DELETE n
                        """).consume();
                transaction.run("""
                        CREATE (:MemoryProjection {repositoryId:$repositoryId, policyId:$policyId,
                          activeEvaluationCount:$activeEvaluationCount})
                        """, parameters("repositoryId", config.repositoryId(), "policyId", policyId,
                        "activeEvaluationCount", records.size())).consume();
                return null;
            });
        }
        records.forEach(this::append);
    }

    private List<EvidenceDocument> withHistoricalOutcomes(List<EvidenceDocument> documents) {
        if (documents.isEmpty()) return documents;
        List<String> ids = documents.stream().map(EvidenceDocument::stableId).toList();
        var outcomes = new java.util.HashMap<String, List<HistoricalOutcome>>();
        try (Session session = session()) {
            var rows = session.run("""
                    MATCH (c:Candidate {repositoryId:$repositoryId})-[:RETRIEVED|CHANGED]->(n:Evidence {repositoryId:$repositoryId})
                    MATCH (c)-[:SCORED]->(e:Evaluation {repositoryId:$repositoryId})
                    WHERE n.stableId IN $ids
                    RETURN DISTINCT n.stableId AS evidenceId, e.evaluationId AS evaluationId,
                           e.decision AS decision, e.fitness AS fitness, e.summary AS summary,
                           e.evaluatedAt AS evaluatedAt
                    ORDER BY evidenceId, evaluatedAt DESC, evaluationId
                    """, parameters("repositoryId", config.repositoryId(), "ids", ids)).list();
            for (Record row : rows) {
                List<HistoricalOutcome> existing = outcomes.computeIfAbsent(
                        row.get("evidenceId").asString(), ignored -> new ArrayList<>());
                if (existing.size() < 3) {
                    existing.add(new HistoricalOutcome(
                            row.get("evaluationId").asString(), row.get("decision").asString(),
                            Math.max(0.0, Math.min(1.0, row.get("fitness").asDouble())),
                            row.get("summary").asString()));
                }
            }
        }
        return documents.stream().map(document -> new EvidenceDocument(
                document.stableId(), document.logicalId(), document.kind(), document.revision(),
                document.contentHash(), document.semanticText(), document.authority(), document.status(),
                document.sources(), document.links(), outcomes.getOrDefault(document.stableId(), List.of()))).toList();
    }

    private Session session() {
        return driver.session(SessionConfig.builder().withDatabase(config.database()).build());
    }

    private String stableKey(String stableId) {
        return config.repositoryId() + "|" + stableId;
    }

    private static String label(String kind) {
        if (!LABELS.contains(kind)) {
            return "KnowledgeEntry";
        }
        return kind;
    }

    private static EvidenceDocument document(Map<String, Object> properties) {
        return document(properties, List.of());
    }

    private static EvidenceDocument document(Map<String, Object> properties, List<EvidenceLink> links) {
        String sourcePath = properties.get("sourcePath").toString();
        String sourceAnchor = properties.get("sourceAnchor").toString();
        return new EvidenceDocument(
                properties.get("stableId").toString(),
                properties.get("logicalId").toString(),
                properties.get("kind").toString(),
                properties.get("revision").toString(),
                properties.get("contentHash").toString(),
                properties.get("semanticText").toString(),
                EvidenceAuthority.valueOf(properties.get("authority").toString()),
                properties.get("status").toString(),
                List.of(new SourceReference(sourcePath, sourceAnchor)),
                links,
                List.of());
    }

    private static Optional<String> optionalString(Record record, String key) {
        return record.get(key).isNull() ? Optional.empty() : Optional.of(record.get(key).asString());
    }

    private String repositoryVectorLabel() {
        return "EvidenceRepository_" + digest(config.repositoryId()).substring(0, 16);
    }

    private static String vectorIndexName(String repositoryId, String modelId, int dimensions) {
        return "evidence_vector_" + digest(repositoryId + ":" + modelId + ":" + dimensions).substring(0, 16);
    }

    private static String digest(String value) {
        try {
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
            return digest;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    @Override
    public void close() {
        driver.close();
    }
}
