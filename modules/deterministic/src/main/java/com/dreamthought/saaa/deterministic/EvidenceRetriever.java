package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.RetrievalBundle;
import com.dreamthought.saaa.domain.RetrievalDiagnostics;
import com.dreamthought.saaa.domain.RetrievalMode;
import com.dreamthought.saaa.domain.RetrievalQuery;
import java.util.List;

@FunctionalInterface
public interface EvidenceRetriever {
    RetrievalBundle retrieve(RetrievalQuery query);

    static EvidenceRetriever none(String configurationId) {
        return none(configurationId, "lineage-novelty-v1");
    }

    static EvidenceRetriever none(String configurationId, String memoryPolicyId) {
        return query -> {
            if (query.mode() != RetrievalMode.NONE) {
                throw new IllegalStateException("retrieval mode " + query.mode() + " requires a configured retrieval adapter");
            }
            return new RetrievalBundle(
                    RetrievalMode.NONE,
                    configurationId,
                    query.repositoryRevision(),
                    "none",
                    "none",
                    "none",
                    "none",
                    memoryPolicyId,
                    List.of(),
                    RetrievalDiagnostics.empty(),
                    "");
        };
    }
}
