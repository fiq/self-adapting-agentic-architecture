package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.RetrievalProvenance;

/** Append-only audit projection for reconstructing the retrieval treatment applied to a proposal. */
public interface RetrievalProvenanceStore {
    void record(String queryFingerprint, RetrievalProvenance provenance);
}
