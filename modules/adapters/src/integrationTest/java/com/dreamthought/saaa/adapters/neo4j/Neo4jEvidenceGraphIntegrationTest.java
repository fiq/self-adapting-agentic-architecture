package com.dreamthought.saaa.adapters.neo4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dreamthought.saaa.domain.EvidenceAuthority;
import com.dreamthought.saaa.domain.EvidenceDocument;
import com.dreamthought.saaa.domain.EmbeddedRepositoryProjection;
import com.dreamthought.saaa.domain.GraphEdge;
import com.dreamthought.saaa.domain.RelationshipType;
import com.dreamthought.saaa.domain.RepositoryProjection;
import com.dreamthought.saaa.domain.SourceReference;
import java.util.List;
import java.util.Set;
import java.util.Map;
import com.dreamthought.saaa.domain.EvolutionaryMemoryRecord;
import com.dreamthought.saaa.domain.EvolutionContext;
import com.dreamthought.saaa.domain.MutationScope;
import com.dreamthought.saaa.domain.RepositoryRole;
import com.dreamthought.saaa.domain.RetrievalMode;
import com.dreamthought.saaa.domain.CheckEvidence;
import com.dreamthought.saaa.domain.CheckStatus;
import com.dreamthought.saaa.domain.FitnessDecision;
import com.dreamthought.saaa.domain.FitnessScore;
import java.time.Instant;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

final class Neo4jEvidenceGraphIntegrationTest {
    @Test
    void unavailableGraphFailsConfiguredRetrievalBeforeProposal() {
        Assumptions.assumeTrue("true".equals(System.getenv("SAAA_NEO4J_INTEGRATION")));
        assertThatThrownBy(() -> Neo4jEvidenceGraph.connect(new Neo4jConfig(
                "bolt://127.0.0.1:1", "neo4j", "unused", "neo4j", "integration-fixture",
                RepositoryRole.SUBJECT)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("retrieval treatment was not changed");
    }

    @Test
    void replacesRepositoryProjectionAndTraversesAnExplicitStructuralRelationship() {
        Assumptions.assumeTrue("true".equals(System.getenv("SAAA_NEO4J_INTEGRATION")));
        String password = System.getenv().getOrDefault("SAAA_NEO4J_PASSWORD", "saaa-local-only");
        var config = new Neo4jConfig(
                "bolt://127.0.0.1:7687", "neo4j", password, "neo4j", "integration-fixture",
                RepositoryRole.SUBJECT);

        try (var graph = Neo4jEvidenceGraph.connect(config)) {
            var production = document("type:example.Loop", "Type", "coordinates evaluation");
            var test = document("test:example.LoopTest", "Test", "tests evaluation");
            var projection = new RepositoryProjection(
                    "integration-fixture",
                    "rev-1",
                    "graph-schema-v1",
                    List.of(production, test),
                    List.of(new GraphEdge(test.stableId(), RelationshipType.TESTS, production.stableId(),
                            "fixture test relation")));

            var embedded = new EmbeddedRepositoryProjection(
                    projection, "fixture-embedding-v1", 2,
                    Map.of(production.stableId(), List.of(0.8f, 0.2f),
                            test.stableId(), List.of(0.0f, 1.0f)));
            graph.replaceEmbeddedRepositoryProjection(embedded);
            graph.replaceEmbeddedRepositoryProjection(embedded);

            var otherConfig = new Neo4jConfig(
                    "bolt://127.0.0.1:7687", "neo4j", password, "neo4j", "other-fixture",
                    RepositoryRole.PROCESS);
            try (var otherGraph = Neo4jEvidenceGraph.connect(otherConfig)) {
                var other = document("type:other.PerfectMatch", "Type", "unrelated exact vector match");
                var otherProjection = new RepositoryProjection(
                        "other-fixture", "other-rev-1", "graph-schema-v1", List.of(other), List.of());
                otherGraph.replaceEmbeddedRepositoryProjection(new EmbeddedRepositoryProjection(
                        otherProjection, "fixture-embedding-v1", 2,
                        Map.of(other.stableId(), List.of(1.0f, 0.0f))));
                assertThat(otherGraph.resolveExact(List.of(other.stableId()))).hasSize(1);
            }

            assertThat(graph.status().repositoryRevision()).contains("rev-1");
            assertThat(graph.status().nodeCount()).isEqualTo(2);
            assertThat(graph.resolveExact(List.of(production.logicalId())))
                    .extracting(EvidenceDocument::stableId)
                    .containsExactly(production.stableId());
            var expanded = graph.expand(
                    List.of(production.stableId()), Set.of(RelationshipType.TESTS), 1, 2);
            assertThat(expanded)
                    .extracting(EvidenceDocument::stableId)
                    .containsExactly(test.stableId());
            assertThat(expanded.getFirst().links()).singleElement().satisfies(link -> {
                assertThat(link.relationship()).isEqualTo(RelationshipType.TESTS);
                assertThat(link.targetId()).isEqualTo(production.stableId());
            });
            assertThat(graph.searchVector("fixture-embedding-v1", 2, List.of(1.0f, 0.0f), 1))
                    .extracting(EvidenceDocument::stableId)
                    .containsExactly(production.stableId());

            graph.append(new EvolutionaryMemoryRecord(
                    new EvolutionContext("integration-fixture", "rev-1", "saaa", "process-rev-1"),
                    "lineage-novelty-v1", "mutation-1", "bounded fixture",
                    MutationScope.WORKFLOW_DEFINITION, "candidate-1", "commit-1",
                    RetrievalMode.HYBRID, "retrieval-config-v1",
                    List.of("fixture/type:example.Loop"), List.of(),
                    List.of(new CheckEvidence("tests", CheckStatus.FAILED, "fixture failure")),
                    List.of(), FitnessScore.of(0.2, FitnessDecision.DISCARD), "fixture-fingerprint",
                    Instant.parse("2026-08-02T00:00:00Z")));
            graph.replaceEvolutionaryMemory(List.of(new EvolutionaryMemoryRecord(
                    new EvolutionContext("integration-fixture", "rev-1", "saaa", "process-rev-1"),
                    "lineage-novelty-v1", "mutation-1", "bounded fixture",
                    MutationScope.WORKFLOW_DEFINITION, "candidate-1", "commit-1",
                    RetrievalMode.HYBRID, "retrieval-config-v1",
                    List.of("fixture/type:example.Loop"), List.of(),
                    List.of(new CheckEvidence("tests", CheckStatus.FAILED, "fixture failure")),
                    List.of(), FitnessScore.of(0.2, FitnessDecision.DISCARD), "fixture-fingerprint",
                    Instant.parse("2026-08-02T00:00:00Z"))), "lineage-novelty-v1");
            assertThat(graph.memoryStatus().policyId()).contains("lineage-novelty-v1");
            assertThat(graph.memoryStatus().activeEvaluations()).isEqualTo(1);
            assertThat(graph.hasEvolutionContext(
                    "candidate-1", "integration-fixture", "saaa", "retrieval-config-v1")).isTrue();
            assertThat(graph.resolveExact(List.of(production.stableId())).getFirst().historicalOutcomes())
                    .singleElement()
                    .satisfies(outcome -> {
                        assertThat(outcome.decision()).isEqualTo("DISCARD");
                        assertThat(outcome.summary()).contains("fixture failure");
                    });

            graph.replaceRepositoryProjection(new RepositoryProjection(
                    "integration-fixture", "rev-2", "graph-schema-v1", List.of(production), List.of()));
            assertThat(graph.resolveExact(List.of(test.stableId()))).isEmpty();
            try (var otherGraph = Neo4jEvidenceGraph.connect(new Neo4jConfig(
                    "bolt://127.0.0.1:7687", "neo4j", password, "neo4j", "other-fixture",
                    RepositoryRole.PROCESS))) {
                assertThat(otherGraph.resolveExact(List.of("type:other.PerfectMatch"))).hasSize(1);
            }
        }
    }

    private static EvidenceDocument document(String id, String kind, String semanticText) {
        return new EvidenceDocument(
                id,
                id,
                kind,
                "rev-1",
                "hash-" + id,
                semanticText,
                EvidenceAuthority.CANONICAL,
                "active",
                List.of(new SourceReference("fixture/" + id, id)),
                List.of());
    }
}
