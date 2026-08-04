package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.EmbeddedRepositoryProjection;

public interface EmbeddedEvidenceProjectionStore extends EvidenceProjectionStore {
    void replaceEmbeddedRepositoryProjection(EmbeddedRepositoryProjection projection);
}
