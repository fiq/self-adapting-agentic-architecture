package com.dreamthought.saaa.deterministic;

import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.domain.EvidenceAuthority;
import com.dreamthought.saaa.domain.EvidenceDocument;
import com.dreamthought.saaa.domain.HistoricalOutcome;
import com.dreamthought.saaa.domain.RelationshipType;
import com.dreamthought.saaa.domain.RetrievalConfig;
import com.dreamthought.saaa.domain.RetrievalMode;
import com.dreamthought.saaa.domain.RetrievalQuery;
import com.dreamthought.saaa.domain.SourceReference;
import com.dreamthought.saaa.domain.WorkflowGraph;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class HybridEvidenceRetrieverTest {
    @Test
    void appliesTheConfiguredHistoricalCapOnceAfterRankFusion() {
        var unweightedFirst = document("first", List.of());
        var historicallyWeightedSecond = document("second", List.of(
                new HistoricalOutcome("evaluation-1", "DISCARD", 0.2, "failed check")));
        EvidenceSearch search = new EvidenceSearch() {
            private final List<EvidenceDocument> ranked = List.of(unweightedFirst, historicallyWeightedSecond);
            @Override public List<EvidenceDocument> resolveExact(List<String> identifiers) { return ranked; }
            @Override public List<EvidenceDocument> vectorSearch(String semanticQuery, int limit) { return ranked; }
            @Override public List<EvidenceDocument> expand(
                    List<String> seedIds, Set<RelationshipType> relationships, int depth, int maxFanOut) {
                return ranked;
            }
        };
        var config = new RetrievalConfig(
                "retrieval-config-v1", 1, 2, 1, 500, 60, 0.0005,
                Set.of(RelationshipType.RELATES_TO), "graph-schema-v1", "capsule-v1", "rrf-v1",
                "fake-embedding-v1", "lineage-novelty-v1");
        var retriever = new HybridEvidenceRetriever(search, config);
        var baseline = new WorkflowGraph("workflow", "revision-1", "definition");

        var result = retriever.retrieve(new RetrievalQuery(
                RetrievalMode.HYBRID, "bounded task", baseline, "revision-1",
                List.of("first", "second"), Optional.empty()));

        assertThat(result.capsules()).extracting(capsule -> capsule.subject().stableId())
                .containsExactly("first");
        assertThat(result.diagnostics().historicalWeightCap()).isEqualTo(0.0005);
    }

    private static EvidenceDocument document(String id, List<HistoricalOutcome> outcomes) {
        return new EvidenceDocument(
                id, id, "KnowledgeEntry", "revision-1", "hash-" + id, "summary " + id,
                EvidenceAuthority.CANONICAL, "active", List.of(new SourceReference("docs/" + id, id)), outcomes);
    }
}
