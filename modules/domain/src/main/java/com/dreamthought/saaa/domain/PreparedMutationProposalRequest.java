package com.dreamthought.saaa.domain;

import java.util.Objects;

public record PreparedMutationProposalRequest(
        WorkflowGraph baseline,
        RetrievalQuery retrievalQuery,
        RetrievalBundle retrieval
) {
    public PreparedMutationProposalRequest {
        baseline = Objects.requireNonNull(baseline, "baseline");
        retrievalQuery = Objects.requireNonNull(retrievalQuery, "retrievalQuery");
        retrieval = Objects.requireNonNull(retrieval, "retrieval");
        if (!baseline.equals(retrievalQuery.baseline()) || retrievalQuery.mode() != retrieval.mode()) {
            throw new IllegalArgumentException("prepared proposal retrieval must match its query and baseline");
        }
    }
}
