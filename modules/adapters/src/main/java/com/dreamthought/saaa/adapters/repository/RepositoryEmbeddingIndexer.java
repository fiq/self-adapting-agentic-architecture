package com.dreamthought.saaa.adapters.repository;

import com.dreamthought.saaa.deterministic.CachedSemanticEmbeddingModel;
import com.dreamthought.saaa.deterministic.EmbeddedEvidenceProjectionStore;
import com.dreamthought.saaa.domain.EmbeddedRepositoryProjection;
import com.dreamthought.saaa.domain.RepositoryProjection;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Objects;

/** Computes the complete embedding set before atomically replacing the graph projection. */
public final class RepositoryEmbeddingIndexer {
    private final RepositoryEvidenceExtractor extractor;
    private final CachedSemanticEmbeddingModel embeddings;
    private final EmbeddedEvidenceProjectionStore store;

    public RepositoryEmbeddingIndexer(
            RepositoryEvidenceExtractor extractor,
            CachedSemanticEmbeddingModel embeddings,
            EmbeddedEvidenceProjectionStore store) {
        this.extractor = Objects.requireNonNull(extractor, "extractor");
        this.embeddings = Objects.requireNonNull(embeddings, "embeddings");
        this.store = Objects.requireNonNull(store, "store");
    }

    public EmbeddedRepositoryProjection build(Path repositoryRoot, String repositoryRevision) {
        return publish(extractor.extract(repositoryRoot, repositoryRevision));
    }

    public EmbeddedRepositoryProjection build(
            Path repositoryRoot, String repositoryRevision, String repositoryId) {
        return publish(extractor.extract(repositoryRoot, repositoryRevision, repositoryId));
    }

    private EmbeddedRepositoryProjection publish(RepositoryProjection repository) {
        var vectors = new LinkedHashMap<String, java.util.List<Float>>();
        for (var document : repository.nodes()) {
            vectors.put(document.stableId(), embeddings.embed(document.contentHash(), document.semanticText()).vector());
        }
        var projection = new EmbeddedRepositoryProjection(
                repository, embeddings.modelId(), embeddings.dimensions(), vectors);
        store.replaceEmbeddedRepositoryProjection(projection);
        return projection;
    }
}
