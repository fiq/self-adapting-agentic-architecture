package com.dreamthought.saaa.adapters.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dreamthought.saaa.deterministic.CachedSemanticEmbeddingModel;
import com.dreamthought.saaa.deterministic.EmbeddedEvidenceProjectionStore;
import com.dreamthought.saaa.deterministic.EmbeddingCache;
import com.dreamthought.saaa.deterministic.SemanticEmbeddingModel;
import com.dreamthought.saaa.domain.EmbeddedRepositoryProjection;
import com.dreamthought.saaa.domain.ProjectionStatus;
import com.dreamthought.saaa.domain.RepositoryProjection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RepositoryEmbeddingIndexerTest {
    /**
     * Arranged rather than resolved from Git: this test covers atomic publication, so repository
     * identity is an input. Resolving it would make the test depend on whichever repository
     * happens to enclose the temp directory.
     */
    private static final String REPOSITORY_ID = "fixture";

    @TempDir Path temporaryDirectory;

    @Test
    void embeddingFailureDoesNotPublishAPartialGraphRevision() throws Exception {
        Path source = temporaryDirectory.resolve("modules/domain/src/main/java/example/Subject.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "package example;\npublic final class Subject {}\n");
        var calls = new AtomicInteger();
        var model = new CachedSemanticEmbeddingModel(new SemanticEmbeddingModel() {
            public String modelId() { return "failing-fixture"; }
            public int dimensions() { return 2; }
            public List<Float> embed(String text) {
                if (calls.incrementAndGet() == 1) throw new IllegalStateException("fixture embedding failed");
                return List.of(1.0f, 0.0f);
            }
        }, new InMemoryEmbeddingCache());
        var store = new RecordingStore();

        assertThatThrownBy(() -> new RepositoryEmbeddingIndexer(
                new RepositoryEvidenceExtractor(), model, store)
                .build(temporaryDirectory, "rev-1", REPOSITORY_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fixture embedding failed");
        assertThat(store.published).isFalse();
    }

    private static final class RecordingStore implements EmbeddedEvidenceProjectionStore {
        boolean published;
        public void replaceRepositoryProjection(RepositoryProjection projection) { published = true; }
        public void replaceEmbeddedRepositoryProjection(EmbeddedRepositoryProjection projection) { published = true; }
        public ProjectionStatus status() {
            return new ProjectionStatus(REPOSITORY_ID, Optional.empty(), Optional.empty(), 0, 0);
        }
    }

    private static final class InMemoryEmbeddingCache implements EmbeddingCache {
        public Optional<List<Float>> find(String modelId, String contentHash, int dimensions) {
            return Optional.empty();
        }
        public void put(String modelId, String contentHash, List<Float> embedding) { }
    }
}
