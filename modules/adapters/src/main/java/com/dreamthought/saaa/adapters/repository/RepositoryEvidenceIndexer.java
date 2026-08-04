package com.dreamthought.saaa.adapters.repository;

import com.dreamthought.saaa.deterministic.EvidenceProjectionStore;
import com.dreamthought.saaa.domain.RepositoryProjection;
import java.nio.file.Path;
import java.util.Objects;

public final class RepositoryEvidenceIndexer {
    private final RepositoryEvidenceExtractor extractor;
    private final EvidenceProjectionStore store;

    public RepositoryEvidenceIndexer(RepositoryEvidenceExtractor extractor, EvidenceProjectionStore store) {
        this.extractor = Objects.requireNonNull(extractor, "extractor");
        this.store = Objects.requireNonNull(store, "store");
    }

    public RepositoryProjection build(Path repositoryRoot, String repositoryRevision) {
        RepositoryProjection projection = extractor.extract(repositoryRoot, repositoryRevision);
        store.replaceRepositoryProjection(projection);
        return projection;
    }

    public RepositoryProjection build(
            Path repositoryRoot, String repositoryRevision, String repositoryId) {
        RepositoryProjection projection = extractor.extract(repositoryRoot, repositoryRevision, repositoryId);
        store.replaceRepositoryProjection(projection);
        return projection;
    }
}
