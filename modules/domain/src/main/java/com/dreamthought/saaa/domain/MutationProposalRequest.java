package com.dreamthought.saaa.domain;

import java.util.Objects;

public record MutationProposalRequest(WorkflowGraph baseline, RetrievalQuery retrievalQuery) {
    public MutationProposalRequest {
        baseline = Objects.requireNonNull(baseline, "baseline");
        retrievalQuery = Objects.requireNonNull(retrievalQuery, "retrievalQuery");
        if (!baseline.equals(retrievalQuery.baseline())) {
            throw new IllegalArgumentException("retrieval query baseline must match proposal baseline");
        }
    }
}
