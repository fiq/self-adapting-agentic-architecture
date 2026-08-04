package com.dreamthought.saaa.adapters.sqlite;

import static org.assertj.core.api.Assertions.assertThat;

import com.dreamthought.saaa.domain.EvidenceAuthority;
import com.dreamthought.saaa.domain.EvidenceCapsuleProjection;
import com.dreamthought.saaa.domain.EvidenceLink;
import com.dreamthought.saaa.domain.EvidenceSubject;
import com.dreamthought.saaa.domain.HistoricalOutcome;
import com.dreamthought.saaa.domain.RelationshipType;
import com.dreamthought.saaa.domain.RetrievalDiagnostics;
import com.dreamthought.saaa.domain.RetrievalMode;
import com.dreamthought.saaa.domain.RetrievalProvenance;
import com.dreamthought.saaa.domain.SourceReference;
import com.dreamthought.saaa.deterministic.CachedSemanticEmbeddingModel;
import com.dreamthought.saaa.deterministic.SemanticEmbeddingModel;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.util.concurrent.atomic.AtomicInteger;

final class SqliteRetrievalProjectionStoreIntegrationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void reusesCapsulesOnlyForTheSameLogicalSubjectRevisionAndProjectionVersion() {
        var store = new SqliteRetrievalProjectionStore(temporaryDirectory.resolve("retrieval.sqlite"));
        var projection = new EvidenceCapsuleProjection(
                new EvidenceSubject("type:example.Loop@hash", "example.Loop", "Type"),
                "commit-a:hash", "capsule-v1", "Coordinates deterministic evaluation.",
                EvidenceAuthority.CANONICAL, "active",
                List.of(new EvidenceLink(RelationshipType.TESTS, "test:example.LoopTest", "covered by test")),
                List.of(new HistoricalOutcome("evaluation-1", "DISCARD", 0.25, "failed hard gate")),
                List.of(new SourceReference("modules/deterministic/Loop.java", "example.Loop")), 12);

        store.put(projection);

        assertThat(store.find("example.Loop", "commit-a:hash", "capsule-v1")).contains(projection);
        assertThat(store.find("example.Loop", "commit-b:hash", "capsule-v1")).isEmpty();
        assertThat(store.find("example.Loop", "commit-a:hash", "capsule-v2")).isEmpty();
    }

    @Test
    void recordsRetrievalProvenanceApartFromExperimentMetadata() {
        var store = new SqliteRetrievalProjectionStore(temporaryDirectory.resolve("retrieval.sqlite"));
        store.record("sha256:query", new RetrievalProvenance(
                RetrievalMode.GRAPH, "retrieval-config-v1", "commit-a", "graph-schema-v1",
                "capsule-v1", "rrf-v1", "unconfigured", "lineage-novelty-v1", List.of("ARCH-001"),
                "[ARCH-001] deterministic authority", new RetrievalDiagnostics(
                        1, 0, 2, 2, 1, 0, 0.1, List.of("ARCH-001", "type:example.Loop"))));

        assertThat(store.retrievalAttemptCount()).isEqualTo(1);
    }

    @Test
    void reusesAndInvalidatesEmbeddingsByModelAndContentHash() {
        var store = new SqliteRetrievalProjectionStore(temporaryDirectory.resolve("retrieval.sqlite"));
        var calls = new AtomicInteger();
        SemanticEmbeddingModel model = new SemanticEmbeddingModel() {
            public String modelId() { return "fixture-v1"; }
            public int dimensions() { return 2; }
            public List<Float> embed(String text) {
                calls.incrementAndGet();
                return List.of((float) text.length(), 1.0f);
            }
        };
        var cached = new CachedSemanticEmbeddingModel(model, store);

        assertThat(cached.embed("hash-a", "first").cacheHit()).isFalse();
        assertThat(cached.embed("hash-a", "first").cacheHit()).isTrue();
        assertThat(cached.embed("hash-b", "changed").cacheHit()).isFalse();
        assertThat(calls).hasValue(2);

        var otherModel = new CachedSemanticEmbeddingModel(new SemanticEmbeddingModel() {
            public String modelId() { return "fixture-v2"; }
            public int dimensions() { return 2; }
            public List<Float> embed(String text) { calls.incrementAndGet(); return List.of(0.0f, 1.0f); }
        }, store);
        assertThat(otherModel.embed("hash-a", "first").cacheHit()).isFalse();
        assertThat(calls).hasValue(3);
    }

}
