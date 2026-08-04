package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.ProjectionStatus;
import com.dreamthought.saaa.domain.RepositoryProjection;

/** Rebuildable graph projection boundary; repository artifacts remain canonical. */
public interface EvidenceProjectionStore {
    void replaceRepositoryProjection(RepositoryProjection projection);

    ProjectionStatus status();
}
